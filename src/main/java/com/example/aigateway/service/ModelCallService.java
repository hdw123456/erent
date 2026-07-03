package com.example.aigateway.service;

import com.example.aigateway.common.ErrorResponse;
import com.example.aigateway.dto.ProviderModelPricing;
import com.example.aigateway.dto.request.ChatRequest;
import com.example.aigateway.dto.response.ChatResponse;
import com.example.aigateway.entity.RequestLog;
import com.example.aigateway.exception.BusinessException;
import com.example.aigateway.mapper.RequestLogMapper;
import com.example.aigateway.provider.ProviderAdapter;
import com.example.aigateway.provider.ProviderAdapterFactory;
import com.example.aigateway.provider.ProviderCredential;
import com.example.aigateway.security.ApiKeyPrincipal;
import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

@Service
public class ModelCallService {
    private static final long STREAM_TIMEOUT_MS = 0L;

    private final ProviderAdapterFactory providerAdapterFactory;
    private final ProviderCredentialService providerCredentialService;
    private final ModelService modelService;
    private final RequestLogMapper requestLogMapper;
    private final UpstreamErrorService upstreamErrorService;

    public ModelCallService(
            ProviderAdapterFactory providerAdapterFactory,
            ProviderCredentialService providerCredentialService,
            ModelService modelService,
            RequestLogMapper requestLogMapper,
            UpstreamErrorService upstreamErrorService
    ) {
        this.providerAdapterFactory = providerAdapterFactory;
        this.providerCredentialService = providerCredentialService;
        this.modelService = modelService;
        this.requestLogMapper = requestLogMapper;
        this.upstreamErrorService = upstreamErrorService;
    }

    public ChatResponse chat(ChatRequest request, ApiKeyPrincipal principal) {
        requirePrincipal(principal);
        request.setStream(false);

        String requestId = newRequestId();
        long startedAt = System.nanoTime();
        ProviderModelPricing model = null;
        try {
            model = modelService.getAvailableModelByCode(request.getProviderCode(), request.getModel());
            ProviderCredential credential = providerCredentialService.resolveForCall(model, principal.getUserId());
            ProviderAdapter adapter = providerAdapterFactory.getAdapter(model.getProviderCode());

            ChatResponse response = adapter.chat(request, credential);
            response.setRequestId(requestId);
            writeRequestLog(requestId, principal, model, HttpStatus.OK.value(), startedAt, null);
            return response;
        } catch (Throwable throwable) {
            BusinessException exception = upstreamErrorService.toBusinessException(throwable);
            writeRequestLog(requestId, principal, model, exception.getStatus().value(), startedAt, exception.getCode());
            throw exception;
        }
    }

    public SseEmitter stream(ChatRequest request, ApiKeyPrincipal principal) {
        requirePrincipal(principal);
        request.setStream(true);

        String requestId = newRequestId();
        long startedAt = System.nanoTime();
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        AtomicBoolean logged = new AtomicBoolean(false);
        AtomicReference<Disposable> subscriptionRef = new AtomicReference<>();
        AtomicReference<ProviderModelPricing> modelRef = new AtomicReference<>();
        ProviderModelPricing model = null;

        try {
            model = modelService.getAvailableModelByCode(request.getProviderCode(), request.getModel());
            modelRef.set(model);
            ProviderCredential credential = providerCredentialService.resolveForCall(model, principal.getUserId());
            ProviderAdapter adapter = providerAdapterFactory.getAdapter(model.getProviderCode());
            ProviderModelPricing resolvedModel = model;

            Disposable subscription = adapter.stream(request, credential).subscribe(
                    data -> sendData(emitter, subscriptionRef, data),
                    throwable -> {
                        BusinessException exception = upstreamErrorService.toBusinessException(throwable);
                        if (logged.compareAndSet(false, true)) {
                            writeRequestLog(requestId, principal, resolvedModel, exception.getStatus().value(), startedAt, exception.getCode());
                        }
                        sendError(emitter, exception, requestId);
                    },
                    () -> {
                        if (logged.compareAndSet(false, true)) {
                            writeRequestLog(requestId, principal, resolvedModel, HttpStatus.OK.value(), startedAt, null);
                        }
                        emitter.complete();
                    }
            );
            subscriptionRef.set(subscription);
        } catch (Throwable throwable) {
            BusinessException exception = upstreamErrorService.toBusinessException(throwable);
            if (logged.compareAndSet(false, true)) {
                writeRequestLog(requestId, principal, model, exception.getStatus().value(), startedAt, exception.getCode());
            }
            throw exception;
        }

        emitter.onCompletion(() -> dispose(subscriptionRef));
        emitter.onTimeout(() -> {
            if (logged.compareAndSet(false, true)) {
                writeRequestLog(requestId, principal, modelRef.get(), HttpStatus.GATEWAY_TIMEOUT.value(), startedAt, "PROVIDER_TIMEOUT");
            }
            dispose(subscriptionRef);
            emitter.complete();
        });
        emitter.onError(throwable -> {
            if (logged.compareAndSet(false, true)) {
                writeRequestLog(requestId, principal, modelRef.get(), 499, startedAt, "CLIENT_STREAM_CLOSED");
            }
            dispose(subscriptionRef);
        });
        return emitter;
    }

    private void requirePrincipal(ApiKeyPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new BusinessException("UNAUTHORIZED", "API key is required", HttpStatus.UNAUTHORIZED);
        }
    }

    private void sendData(SseEmitter emitter, AtomicReference<Disposable> subscriptionRef, String data) {
        try {
            emitter.send(SseEmitter.event().data(data));
        } catch (IOException exception) {
            dispose(subscriptionRef);
            emitter.completeWithError(exception);
        }
    }

    private void sendError(SseEmitter emitter, BusinessException exception, String requestId) {
        try {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(ErrorResponse.of(
                            exception.getCode(),
                            exception.getMessage(),
                            Map.of("requestId", requestId)
                    )));
            emitter.complete();
        } catch (IOException ioException) {
            emitter.completeWithError(ioException);
        }
    }

    private void dispose(AtomicReference<Disposable> subscriptionRef) {
        Disposable subscription = subscriptionRef.get();
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
    }

    private void writeRequestLog(
            String requestId,
            ApiKeyPrincipal principal,
            ProviderModelPricing model,
            int statusCode,
            long startedAt,
            String errorCode
    ) {
        RequestLog requestLog = new RequestLog();
        requestLog.setRequestId(requestId);
        requestLog.setUserId(principal.getUserId());
        requestLog.setApiKeyId(principal.getApiKeyId());
        if (model != null) {
            requestLog.setProviderId(model.getProviderId());
            requestLog.setModelId(model.getModelId());
        }
        requestLog.setStatusCode(statusCode);
        requestLog.setLatencyMs(latencyMs(startedAt));
        requestLog.setErrorCode(errorCode);
        requestLog.setCreatedAt(new Date());
        requestLogMapper.insertRequestLog(requestLog);
    }

    private int latencyMs(long startedAt) {
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
        if (elapsedMs > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) elapsedMs;
    }

    private String newRequestId() {
        return "req_" + UUID.randomUUID().toString().replace("-", "");
    }
}
