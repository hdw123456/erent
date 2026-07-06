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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import com.example.aigateway.entity.UsageRecord;
import com.example.aigateway.mapper.IdempotencyRecordMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.erdtman.jcs.JsonCanonicalizer;
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
    private final ProviderKeySelectorService providerKeySelectorService;
    private final ProviderKeyAvailabilityService providerKeyAvailabilityService;
    private final ModelService modelService;
    private final RequestLogMapper requestLogMapper;
    private final UpstreamErrorService upstreamErrorService;
    private final BillingService billingService;
    private final IdempotencyRecordMapper idempotencyRecordMapper;
    private final ObjectMapper objectMapper;

    public ModelCallService(
            ProviderAdapterFactory providerAdapterFactory,
            ProviderKeySelectorService providerKeySelectorService,
            ProviderKeyAvailabilityService providerKeyAvailabilityService,
            ModelService modelService,
            RequestLogMapper requestLogMapper,
            UpstreamErrorService upstreamErrorService,
            BillingService billingService,
            IdempotencyRecordMapper idempotencyRecordMapper,
            ObjectMapper objectMapper) {
        this.providerAdapterFactory = providerAdapterFactory;
        this.providerKeySelectorService = providerKeySelectorService;
        this.providerKeyAvailabilityService = providerKeyAvailabilityService;
        this.modelService = modelService;
        this.requestLogMapper = requestLogMapper;
        this.upstreamErrorService = upstreamErrorService;
        this.billingService = billingService;
        this.idempotencyRecordMapper = idempotencyRecordMapper;
        this.objectMapper = objectMapper;
    }

    public ChatResponse chat(ChatRequest request, ApiKeyPrincipal principal, String idempotencyKey) {
        return chat(request, principal, idempotencyKey, "model_call", writeJson(request));
    }

    public ChatResponse chat(
            ChatRequest request,
            ApiKeyPrincipal principal,
            String idempotencyKey,
            String idempotencyPayload) {
        return chat(request, principal, idempotencyKey, "model_call", idempotencyPayload);
    }

    public ChatResponse chat(
            ChatRequest request,
            ApiKeyPrincipal principal,
            String idempotencyKey,
            String idempotencyRoute,
            String idempotencyPayload) {
        requirePrincipal(principal);
        request.setStream(false);

        String requestId = newRequestId();
        long startedAt = System.nanoTime();
        ProviderModelPricing model = null;
        IdempotencyRecord idempotencyRecord = null;
        String scope = idempotencyScope(principal);
        String payload = canonicalizePayload(hasText(idempotencyPayload) ? idempotencyPayload : writeJson(request));
        String requestFingerprint = buildRequestFingerprint("POST", idempotencyRoute, scope, payload);
        if (hasText(idempotencyKey)) {
            String idempotencyKeyHash = sha256Hex(idempotencyKey.trim());
            idempotencyRecord = toIdempotencyRecord(
                    requestId, principal, scope, idempotencyKeyHash, requestFingerprint);
            IdempotencyClaim idempotencyClaim = claimIdempotencyRequest(idempotencyRecord);
            if (!idempotencyClaim.created()) {
                return replayIdempotentResponse(idempotencyClaim.record(), requestFingerprint);
            }
            idempotencyRecord = idempotencyClaim.record();
        }

        ProviderCredential usedCredential = null;
        try {
            model = modelService.getAvailableModelByCode(request.getProviderCode(), request.getModel());
            billingService.ensureWalletCanStartCall(principal.getUserId());
            List<ProviderCredential> credentials = providerKeySelectorService.selectCredentials(model, principal.getUserId());
            ProviderAdapter adapter = providerAdapterFactory.getAdapter(model.getProviderCode());

            ChatResponse response = null;
            BusinessException lastProviderException = null;
            for (int index = 0; index < credentials.size(); index++) {
                ProviderCredential credential = credentials.get(index);
                usedCredential = credential;
                try {
                    response = adapter.chat(request, credential);
                    providerKeyAvailabilityService.markSuccess(credential.getProviderKeyId());
                    break;
                } catch (Throwable providerThrowable) {
                    BusinessException providerException = upstreamErrorService.toBusinessException(providerThrowable);
                    lastProviderException = providerException;
                    providerKeyAvailabilityService.markFailure(
                            credential.getProviderKeyId(), providerException, providerThrowable);
                    if (index == credentials.size() - 1 || !shouldTryNextCredential(providerException)) {
                        throw providerException;
                    }
                }
            }
            if (response == null) {
                throw lastProviderException == null
                        ? new BusinessException("PROVIDER_EMPTY_RESPONSE", "Provider returned empty response",
                                HttpStatus.BAD_GATEWAY)
                        : lastProviderException;
            }
            response.setRequestId(requestId);

            RequestLog succeslog = toRequestLog(
                    requestId, principal, model, providerKeyId(usedCredential), HttpStatus.OK.value(), startedAt, null);
            writeUsageRecord(succeslog, response.getUsage(), model, idempotencyId(idempotencyRecord), requestFingerprint,
                    writeJson(response));
            return response;
        } catch (Throwable throwable) {
            BusinessException exception = upstreamErrorService.toBusinessException(throwable);
            RequestLog failedLog = toRequestLog(
                    requestId, principal, model, providerKeyId(usedCredential), exception.getStatus().value(),
                    startedAt, exception.getCode());
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
        AtomicReference<ProviderCredential> credentialRef = new AtomicReference<>();
        ProviderModelPricing model = null;

        try {
            model = modelService.getAvailableModelByCode(request.getProviderCode(), request.getModel());
            modelRef.set(model);
            billingService.ensureWalletCanStartCall(principal.getUserId());
            ProviderCredential credential = providerKeySelectorService.selectCredentials(model, principal.getUserId()).get(0);
            credentialRef.set(credential);
            ProviderAdapter adapter = providerAdapterFactory.getAdapter(model.getProviderCode());
            ProviderModelPricing resolvedModel = model;

            Disposable subscription = adapter.stream(request, credential).subscribe(
                    data -> sendData(emitter, subscriptionRef, data),
                    throwable -> {
                        BusinessException exception = upstreamErrorService.toBusinessException(throwable);
                        providerKeyAvailabilityService.markFailure(
                                providerKeyId(credential), exception, throwable);
                        if (logged.compareAndSet(false, true)) {
                            writeRequestLog(requestId, principal, resolvedModel, providerKeyId(credential),
                                    exception.getStatus().value(),
                                    startedAt, exception.getCode());
                        }
                        sendError(emitter, exception, requestId);
                    },
                    () -> {
                        providerKeyAvailabilityService.markSuccess(providerKeyId(credential));
                        if (logged.compareAndSet(false, true)) {
                            writeRequestLog(requestId, principal, resolvedModel, providerKeyId(credential),
                                    HttpStatus.OK.value(), startedAt,
                                    null);
                        }
                        emitter.complete();
                    });
            subscriptionRef.set(subscription);
        } catch (Throwable throwable) {
            BusinessException exception = upstreamErrorService.toBusinessException(throwable);
            ProviderCredential credential = credentialRef.get();
            providerKeyAvailabilityService.markFailure(providerKeyId(credential), exception, throwable);
            if (logged.compareAndSet(false, true)) {
                writeRequestLog(requestId, principal, model, providerKeyId(credential), exception.getStatus().value(), startedAt,
                        exception.getCode());
            }
            throw exception;
        }

        emitter.onCompletion(() -> dispose(subscriptionRef));
        emitter.onTimeout(() -> {
            ProviderCredential credential = credentialRef.get();
            providerKeyAvailabilityService.markFailure(
                    providerKeyId(credential),
                    new BusinessException("PROVIDER_TIMEOUT", "Provider request timed out",
                            HttpStatus.GATEWAY_TIMEOUT),
                    new TimeoutException());
            if (logged.compareAndSet(false, true)) {
                writeRequestLog(requestId, principal, modelRef.get(), providerKeyId(credential),
                        HttpStatus.GATEWAY_TIMEOUT.value(), startedAt,
                        "PROVIDER_TIMEOUT");
            }
            dispose(subscriptionRef);
            emitter.complete();
        });
        emitter.onError(throwable -> {
            if (logged.compareAndSet(false, true)) {
                writeRequestLog(requestId, principal, modelRef.get(), providerKeyId(credentialRef.get()), 499,
                        startedAt, "CLIENT_STREAM_CLOSED");
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

    private boolean shouldTryNextCredential(BusinessException exception) {
        if (exception == null || exception.getCode() == null) {
            return false;
        }
        return switch (exception.getCode()) {
            case "PROVIDER_RATE_LIMITED",
                    "PROVIDER_AUTH_FAILED",
                    "PROVIDER_TIMEOUT",
                    "PROVIDER_UNAVAILABLE",
                    "PROVIDER_UPSTREAM_ERROR",
                    "PROVIDER_EMPTY_RESPONSE",
                    "PROVIDER_MODEL_NOT_FOUND" -> true;
            default -> false;
        };
    }

    private Long providerKeyId(ProviderCredential credential) {
        return credential == null ? null : credential.getProviderKeyId();
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
            Long providerKeyId,
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
        requestLog.setProviderKeyId(providerKeyId);
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
            Long providerKeyId,
            int statusCode,
            long startedAt,
            String errorCode) {
        RequestLog requestLog = toRequestLog(requestId, principal, model, providerKeyId, statusCode, startedAt,
                errorCode);
        requestLogMapper.insertRequestLog(requestLog);
        return requestLog;
    }

    public void writeUsageRecord(
            RequestLog requestLog,
            Usage usage,
            ProviderModelPricing model,
            Long idempotencyRecordId,
            String requestFingerprint,
            String responseJson) {
        int promptTokens = token(usage == null ? null : usage.getPromptTokens());
        int completionTokens = token(usage == null ? null : usage.getCompletionTokens());
        int totalTokens = token(usage == null ? null : usage.getTotalTokens());
        UsageRecord usageRecord = new UsageRecord(requestLog.getRequestId(), requestLog.getUserId(),
                requestLog.getModelId(), promptTokens, completionTokens, totalTokens,
                calculateCost(model, promptTokens, completionTokens));
        usageRecord.setProviderKeyId(requestLog.getProviderKeyId());
        billingService.recordSuccessfulUsage(
                requestLog,
                usageRecord,
                idempotencyRecordId,
                requestFingerprint,
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

    private IdempotencyClaim claimIdempotencyRequest(IdempotencyRecord record) {
        int inserted = idempotencyRecordMapper.insertIdempotencyRecordIgnore(record);

        if (inserted == 1) {
            return new IdempotencyClaim(true, record);
        }
        IdempotencyRecord existingRecord = idempotencyRecordMapper.getByScopeAndIdempotencyKeyHash(
                record.getScope(), record.getIdempotencyKeyHash());
        if (existingRecord == null) {
            throw new BusinessException(
                    "IDEMPOTENCY_LOOKUP_FAILED",
                    "Failed to load existing idempotency record",
                    HttpStatus.CONFLICT);
        }
        return new IdempotencyClaim(false, existingRecord);
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

    private String canonicalizePayload(String payload) {
        try {
            JsonCanonicalizer jc = new JsonCanonicalizer(payload);
            return jc.getEncodedString();
        } catch (IOException exception) {
            return payload;
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
        String safeMethod = hasText(method) ? method.toUpperCase() : "POST";
        String safeRoute = hasText(route) ? route : "model_call";
        String safeScope = hasText(scope) ? scope : "anonymous";
        String safePayload = payload == null ? "" : payload;
        return sha256Hex(safeMethod + "\n" + safeRoute + "\n" + safeScope + "\n" + safePayload);
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

    private record IdempotencyClaim(boolean created, IdempotencyRecord record) {
    }
}
