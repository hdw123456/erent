package com.example.aigateway.service;

import com.example.aigateway.common.ErrorResponse;
import com.example.aigateway.dto.ProviderModelPricing;
import com.example.aigateway.dto.request.ChatRequest;
import com.example.aigateway.dto.response.ChatResponse;
import com.example.aigateway.dto.response.ChatResponse.Usage;
import com.example.aigateway.entity.IdempotencyRecord;
import com.example.aigateway.entity.RequestLog;
import com.example.aigateway.exception.BusinessException;
import com.example.aigateway.mapper.RequestLogMapper;
import com.example.aigateway.provider.ProviderAdapter;
import com.example.aigateway.provider.ProviderAdapterFactory;
import com.example.aigateway.provider.ProviderCredential;
import com.example.aigateway.security.ApiKeyPrincipal;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import com.example.aigateway.entity.UsageRecord;
import com.example.aigateway.mapper.IdempotencyRecordMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@Service
public class ModelCallService {
    private static final long STREAM_TIMEOUT_MS = 0L;
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";

    private final ProviderAdapterFactory providerAdapterFactory;
    private final ProviderCredentialService providerCredentialService;
    private final ModelService modelService;
    private final RequestLogMapper requestLogMapper;
    private final UpstreamErrorService upstreamErrorService;
    private final BillingService billingService;
    private final IdempotencyRecordMapper idempotencyRecordMapper;
    private final ObjectMapper objectMapper;

    public ModelCallService(
            ProviderAdapterFactory providerAdapterFactory,
            ProviderCredentialService providerCredentialService,
            ModelService modelService,
            RequestLogMapper requestLogMapper,
            UpstreamErrorService upstreamErrorService,
            BillingService billingService,
            IdempotencyRecordMapper idempotencyRecordMapper,
            ObjectMapper objectMapper) {
        this.providerAdapterFactory = providerAdapterFactory;
        this.providerCredentialService = providerCredentialService;
        this.modelService = modelService;
        this.requestLogMapper = requestLogMapper;
        this.upstreamErrorService = upstreamErrorService;
        this.billingService = billingService;
        this.idempotencyRecordMapper = idempotencyRecordMapper;
        this.objectMapper = objectMapper;
    }

    public ChatResponse chat(ChatRequest request, ApiKeyPrincipal principal, String idempotencyKey) {
        return chat(request, principal, idempotencyKey, writeJson(request));
    }

    public ChatResponse chat(
            ChatRequest request,
            ApiKeyPrincipal principal,
            String idempotencyKey,
            String idempotencyPayload
    ) {
        requirePrincipal(principal);
        request.setStream(false);

        String requestId = newRequestId();
        long startedAt = System.nanoTime();
        ProviderModelPricing model = null;
        IdempotencyRecord idempotencyRecord = null;
        if (hasText(idempotencyKey)) {
            String scope = idempotencyScope(principal);
            String payload = hasText(idempotencyPayload) ? idempotencyPayload : writeJson(request);
            String idempotencyKeyHash = sha256Hex(idempotencyKey.trim());
            String requestFingerprint = buildRequestFingerprint("POST", "model_call", scope, payload);
            idempotencyRecord = toIdempotencyRecord(
                    requestId, principal, scope, idempotencyKeyHash, requestFingerprint);
            IdempotencyRecord existingRecord = checkRequest(idempotencyRecord);
            if (!requestId.equals(existingRecord.getRequestId())) {
                return replayIdempotentResponse(existingRecord, requestFingerprint);
            }
        }

        try {
            model = modelService.getAvailableModelByCode(request.getProviderCode(), request.getModel());
            billingService.ensureWalletCanStartCall(principal.getUserId());
            ProviderCredential credential = providerCredentialService.resolveForCall(model, principal.getUserId());
            ProviderAdapter adapter = providerAdapterFactory.getAdapter(model.getProviderCode());

            ChatResponse response = adapter.chat(request, credential);
            response.setRequestId(requestId);

            RequestLog succeslog = toRequestLog(requestId, principal, model, HttpStatus.OK.value(), startedAt, null);
            writeUsageRecord(succeslog, response.getUsage(), model, idempotencyId(idempotencyRecord), writeJson(response));
            return response;
        } catch (Throwable throwable) {
            BusinessException exception = upstreamErrorService.toBusinessException(throwable);
            RequestLog failedLog = toRequestLog(
                    requestId, principal, model, exception.getStatus().value(), startedAt, exception.getCode());
            billingService.recordFailedRequestWithoutCharge(
                    failedLog, idempotencyId(idempotencyRecord), exception.getCode());
            throw exception;
        }
    }

    public SseEmitter stream(ChatRequest request, ApiKeyPrincipal principal, String idempotencyKey) {
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
            billingService.ensureWalletCanStartCall(principal.getUserId());
            ProviderCredential credential = providerCredentialService.resolveForCall(model, principal.getUserId());
            ProviderAdapter adapter = providerAdapterFactory.getAdapter(model.getProviderCode());
            ProviderModelPricing resolvedModel = model;

            Disposable subscription = adapter.stream(request, credential).subscribe(
                    data -> sendData(emitter, subscriptionRef, data),
                    throwable -> {
                        BusinessException exception = upstreamErrorService.toBusinessException(throwable);
                        if (logged.compareAndSet(false, true)) {
                            writeRequestLog(requestId, principal, resolvedModel, exception.getStatus().value(),
                                    startedAt, exception.getCode());
                        }
                        sendError(emitter, exception, requestId);
                    },
                    () -> {
                        if (logged.compareAndSet(false, true)) {
                            writeRequestLog(requestId, principal, resolvedModel, HttpStatus.OK.value(), startedAt,
                                    null);
                        }
                        emitter.complete();
                    });
            subscriptionRef.set(subscription);
        } catch (Throwable throwable) {
            BusinessException exception = upstreamErrorService.toBusinessException(throwable);
            if (logged.compareAndSet(false, true)) {
                writeRequestLog(requestId, principal, model, exception.getStatus().value(), startedAt,
                        exception.getCode());
            }
            throw exception;
        }

        emitter.onCompletion(() -> dispose(subscriptionRef));
        emitter.onTimeout(() -> {
            if (logged.compareAndSet(false, true)) {
                writeRequestLog(requestId, principal, modelRef.get(), HttpStatus.GATEWAY_TIMEOUT.value(), startedAt,
                        "PROVIDER_TIMEOUT");
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
                            Map.of("requestId", requestId))));
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

    private RequestLog toRequestLog(
            String requestId,
            ApiKeyPrincipal principal,
            ProviderModelPricing model,
            int statusCode,
            long startedAt,
            String errorCode) {
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
        return requestLog;
    }

    private RequestLog writeRequestLog(
            String requestId,
            ApiKeyPrincipal principal,
            ProviderModelPricing model,
            int statusCode,
            long startedAt,
            String errorCode) {
        RequestLog requestLog = toRequestLog(requestId, principal, model, statusCode, startedAt, errorCode);
        requestLogMapper.insertRequestLog(requestLog);
        return requestLog;
    }

    public void writeUsageRecord(
            RequestLog requestLog,
            Usage usage,
            ProviderModelPricing model,
            Long idempotencyRecordId,
            String responseJson
    ) {
        int promptTokens = token(usage == null ? null : usage.getPromptTokens());
        int completionTokens = token(usage == null ? null : usage.getCompletionTokens());
        int totalTokens = token(usage == null ? null : usage.getTotalTokens());
        billingService.recordSuccessfulUsage(
                requestLog,
                new UsageRecord(requestLog.getRequestId(), requestLog.getUserId(), requestLog.getModelId(),
                        promptTokens, completionTokens, totalTokens,
                        calculateCost(model, promptTokens, completionTokens)),
                idempotencyRecordId,
                responseJson);
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

    private IdempotencyRecord toIdempotencyRecord(
            String requestId,
            ApiKeyPrincipal principal,
            String scope,
            String idempotencyKeyHash,
            String requestFingerprint) {
        Date expiresAt = new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000L);
        return new IdempotencyRecord(
                scope,
                principal.getApiKeyId(),
                idempotencyKeyHash,
                requestFingerprint,
                requestId,
                STATUS_PENDING,
                expiresAt);
    }

    private IdempotencyRecord checkRequest(IdempotencyRecord record) {
        int inserted = idempotencyRecordMapper.insertIdempotencyRecordIgnore(record);

        if (inserted == 1) {
            return record;
        }
        IdempotencyRecord existingRecord = idempotencyRecordMapper.getByScopeAndIdempotencyKeyHash(
                record.getScope(), record.getIdempotencyKeyHash());
        if (existingRecord == null) {
            throw new BusinessException(
                    "IDEMPOTENCY_LOOKUP_FAILED",
                    "Failed to load existing idempotency record",
                    HttpStatus.CONFLICT);
        }
        return existingRecord;
    }

    private ChatResponse replayIdempotentResponse(IdempotencyRecord existingRecord, String requestFingerprint) {
        if (!existingRecord.getRequestFingerprint().equals(requestFingerprint)) {
            throw new BusinessException(
                    "IDEMPOTENCY_KEY_REUSED",
                    "Idempotency-Key was already used with a different request payload",
                    HttpStatus.CONFLICT);
        }
        if (STATUS_COMPLETED.equals(existingRecord.getStatus())) {
            ChatResponse cachedResponse = readJson(existingRecord.getResponseJson());
            cachedResponse.setRequestId(existingRecord.getRequestId());
            return cachedResponse;
        }
        if (STATUS_FAILED.equals(existingRecord.getStatus())) {
            throw new BusinessException(
                    existingRecord.getErrorCode(), "Request previously failed", HttpStatus.CONFLICT);
        }
        throw new BusinessException(
                "IDEMPOTENCY_REQUEST_IN_PROGRESS",
                "A request with this Idempotency-Key is still being processed",
                HttpStatus.CONFLICT);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to serialize idempotency payload", exception);
        }
    }

    private ChatResponse readJson(String json) {
        try {
            return objectMapper.readValue(json, ChatResponse.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to deserialize cached idempotency response", exception);
        }
    }

    private String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    private String buildRequestFingerprint(String method, String route, String scope, String payload) {
        return sha256Hex(method.toUpperCase() + "\n" + route + "\n" + scope + "\n" + payload);
    }

    private Long idempotencyId(IdempotencyRecord record) {
        return record == null ? null : record.getId();
    }

    private String idempotencyScope(ApiKeyPrincipal principal) {
        return "api_key:" + principal.getApiKeyId();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private int token(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal calculateCost(ProviderModelPricing model, int promptTokens, int completionTokens) {
        BigDecimal inputTokenPrice = model == null || model.getInputTokenPrice() == null
                ? BigDecimal.ZERO
                : model.getInputTokenPrice();
        BigDecimal outputTokenPrice = model == null || model.getOutputTokenPrice() == null
                ? BigDecimal.ZERO
                : model.getOutputTokenPrice();
        return inputTokenPrice.multiply(BigDecimal.valueOf(promptTokens))
                .add(outputTokenPrice.multiply(BigDecimal.valueOf(completionTokens)));
    }
}
