package com.example.aigateway.service;

import com.example.aigateway.dto.ProviderModelPricing;
import com.example.aigateway.dto.request.ChatRequest;
import com.example.aigateway.dto.response.ChatResponse;
import com.example.aigateway.dto.response.ChatResponse.Usage;
import com.example.aigateway.entity.IdempotencyRecord;
import com.example.aigateway.entity.RequestLog;
import com.example.aigateway.entity.UsageRecord;
import com.example.aigateway.exception.BusinessException;
import com.example.aigateway.gateway.stream.GatewayStreamFrame;
import com.example.aigateway.gateway.stream.GatewayStreamProtocol;
import com.example.aigateway.gateway.stream.GatewayStreamResponseAdapter;
import com.example.aigateway.gateway.stream.GatewayStreamSink;
import com.example.aigateway.gateway.stream.SseGatewayStreamSink;
import com.example.aigateway.gateway.stream.StreamReplayResponse;
import com.example.aigateway.mapper.IdempotencyRecordMapper;
import com.example.aigateway.provider.ProviderAdapter;
import com.example.aigateway.provider.ProviderAdapterFactory;
import com.example.aigateway.provider.ProviderCredential;
import com.example.aigateway.provider.ProviderStreamEvent;
import com.example.aigateway.security.ApiKeyPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.erdtman.jcs.JsonCanonicalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/** Orchestrates model calls, failover, streaming, idempotency, and billing. */
@Service
public class ModelCallService {
    private static final Logger logger = LoggerFactory.getLogger(ModelCallService.class);
    private static final long STREAM_TIMEOUT_MS = 0L;
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";

    private final ProviderAdapterFactory providerAdapterFactory;
    private final ProviderKeySelectorService providerKeySelectorService;
    private final ProviderKeyAvailabilityService providerKeyAvailabilityService;
    private final ModelService modelService;
    private final GatewayStreamResponseAdapter gatewayStreamResponseAdapter;
    private final UpstreamErrorService upstreamErrorService;
    private final BillingService billingService;
    private final IdempotencyRecordMapper idempotencyRecordMapper;
    private final ObjectMapper objectMapper;

    public ModelCallService(
            ProviderAdapterFactory providerAdapterFactory,
            ProviderKeySelectorService providerKeySelectorService,
            ProviderKeyAvailabilityService providerKeyAvailabilityService,
            ModelService modelService,
            GatewayStreamResponseAdapter gatewayStreamResponseAdapter,
            UpstreamErrorService upstreamErrorService,
            BillingService billingService,
            IdempotencyRecordMapper idempotencyRecordMapper,
            ObjectMapper objectMapper) {
        this.providerAdapterFactory = providerAdapterFactory;
        this.providerKeySelectorService = providerKeySelectorService;
        this.providerKeyAvailabilityService = providerKeyAvailabilityService;
        this.modelService = modelService;
        this.gatewayStreamResponseAdapter = gatewayStreamResponseAdapter;
        this.upstreamErrorService = upstreamErrorService;
        this.billingService = billingService;
        this.idempotencyRecordMapper = idempotencyRecordMapper;
        this.objectMapper = objectMapper;
    }

    public ChatResponse chat(ChatRequest request, ApiKeyPrincipal principal, String idempotencyKey) {
        return chat(request, principal, idempotencyKey, "model_call", writeJson(request));
    }

    /** Executes a non-streaming call with replay, key failover, and atomic billing. */
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
        request.setStream(true);
        return stream(
                request,
                principal,
                idempotencyKey,
                "model_call_stream",
                writeJson(request),
                GatewayStreamProtocol.OPENAI_CHAT_COMPLETIONS);
    }

    public SseEmitter stream(
            ChatRequest request,
            ApiKeyPrincipal principal,
            String idempotencyKey,
            String idempotencyRoute,
            String idempotencyPayload,
            GatewayStreamProtocol protocol) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        StreamCallState state = startStream(
                request,
                principal,
                idempotencyKey,
                idempotencyRoute,
                idempotencyPayload,
                protocol,
                new SseGatewayStreamSink(emitter));
        if (state != null) {
            emitter.onCompletion(() -> dispose(state.subscriptionRef));
            emitter.onTimeout(() -> handleStreamTimeout(state));
            emitter.onError(throwable -> handleClientDisconnect(state, throwable));
        }
        return emitter;
    }

    /** Starts a stream on a caller-owned transport and returns a cancellation hook. */
    public Runnable streamToSink(
            ChatRequest request,
            ApiKeyPrincipal principal,
            String idempotencyKey,
            String idempotencyRoute,
            String idempotencyPayload,
            GatewayStreamProtocol protocol,
            GatewayStreamSink sink) {
        StreamCallState state = startStream(
                request,
                principal,
                idempotencyKey,
                idempotencyRoute,
                idempotencyPayload,
                protocol,
                sink);
        if (state == null) {
            return () -> {
            };
        }
        return () -> handleClientDisconnect(state, new IOException("Stream cancelled"));
    }

    /** Claims stream identity, validates prerequisites, and subscribes the provider pipeline. */
    private StreamCallState startStream(
            ChatRequest request,
            ApiKeyPrincipal principal,
            String idempotencyKey,
            String idempotencyRoute,
            String idempotencyPayload,
            GatewayStreamProtocol protocol,
            GatewayStreamSink sink) {
        requirePrincipal(principal);
        request.setStream(true);

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
            IdempotencyClaim claim = claimIdempotencyRequest(idempotencyRecord);
            if (!claim.created()) {
                replayIdempotentStream(claim.record(), requestFingerprint, protocol, sink);
                return null;
            }
            idempotencyRecord = claim.record();
        }

        try {
            model = modelService.getAvailableModelByCode(request.getProviderCode(), request.getModel());
            billingService.ensureWalletCanStartCall(principal.getUserId());
            List<ProviderCredential> credentials = providerKeySelectorService.selectCredentials(
                    model, principal.getUserId());
            ProviderAdapter adapter = providerAdapterFactory.getAdapter(model.getProviderCode());
            StreamCallState state = new StreamCallState(
                    requestId,
                    startedAt,
                    sink,
                    principal,
                    model,
                    idempotencyRecord,
                    requestFingerprint,
                    protocol,
                    gatewayStreamResponseAdapter.open(protocol, requestId, model.getModelCode()),
                    new StreamResponseAccumulator(request, requestId, model.getModelCode()));

            Disposable subscription = providerStreamWithFailover(
                    adapter, request, credentials, 0, state.credentialRef)
                    .subscribe(
                            event -> handleStreamEvent(state, event),
                            throwable -> handleStreamFailure(
                                    state,
                                    upstreamErrorService.toBusinessException(throwable),
                                    true),
                            () -> completeStream(state));
            state.subscriptionRef.set(subscription);
            return state;
        } catch (Throwable throwable) {
            BusinessException exception = upstreamErrorService.toBusinessException(throwable);
            RequestLog failedLog = toRequestLog(
                    requestId, principal, model, null, exception.getStatus().value(), startedAt, exception.getCode());
            billingService.recordFailedRequestWithoutCharge(
                    failedLog, idempotencyId(idempotencyRecord), exception.getCode());
            throw exception;
        }
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

    /** Retries another key only while no downstream-visible provider event has arrived. */
    private Flux<ProviderStreamEvent> providerStreamWithFailover(
            ProviderAdapter adapter,
            ChatRequest request,
            List<ProviderCredential> credentials,
            int index,
            AtomicReference<ProviderCredential> credentialRef) {
        if (credentials == null || index >= credentials.size()) {
            return Flux.error(new BusinessException(
                    "PROVIDER_KEY_NOT_CONFIGURED",
                    "No provider key is available",
                    HttpStatus.SERVICE_UNAVAILABLE));
        }

        return Flux.defer(() -> {
            ProviderCredential credential = credentials.get(index);
            credentialRef.set(credential);
            AtomicBoolean emitted = new AtomicBoolean(false);
            return Flux.defer(() -> adapter.stream(request, credential))
                    .publishOn(Schedulers.boundedElastic())
                    .doOnNext(event -> emitted.set(true))
                    .switchIfEmpty(Flux.error(new BusinessException(
                            "PROVIDER_EMPTY_RESPONSE",
                            "Provider returned an empty stream",
                            HttpStatus.BAD_GATEWAY)))
                    .onErrorResume(throwable -> {
                        BusinessException exception = upstreamErrorService.toBusinessException(throwable);
                        providerKeyAvailabilityService.markFailure(
                                providerKeyId(credential), exception, throwable);
                        if (!emitted.get()
                                && shouldTryNextCredential(exception)
                                && index + 1 < credentials.size()) {
                            return providerStreamWithFailover(
                                    adapter, request, credentials, index + 1, credentialRef);
                        }
                        return Flux.error(exception);
                    });
        });
    }

    private void handleStreamEvent(StreamCallState state, ProviderStreamEvent event) {
        state.accumulator.accept(event);
        try {
            emitFrames(state, state.responseSession.accept(event), true);
        } catch (IOException exception) {
            handleClientDisconnect(state, exception);
        }
    }

    /** Persists terminal usage before publishing the downstream completion frames. */
    private void completeStream(StreamCallState state) {
        if (!state.finalized.compareAndSet(false, true)) {
            return;
        }

        providerKeyAvailabilityService.markSuccess(providerKeyId(state.credentialRef.get()));
        StreamResponseAccumulator.Result result = state.accumulator.result();
        ChatResponse response = result.response();
        RequestLog successLog = toRequestLog(
                state.requestId,
                state.principal,
                state.model,
                providerKeyId(state.credentialRef.get()),
                HttpStatus.OK.value(),
                state.startedAt,
                null);
        List<GatewayStreamFrame> finalFrames = state.responseSession.finish(response);
        List<GatewayStreamFrame> replayFrames = new ArrayList<>(state.sentFrames);
        replayFrames.addAll(finalFrames);
        String replayJson = writeJson(new StreamReplayResponse(response, replayFrames));

        try {
            UsageRecord usageRecord = toUsageRecord(
                    successLog, response.getUsage(), state.model, result.usageSource());
            billingService.recordSuccessfulUsage(
                    successLog,
                    usageRecord,
                    idempotencyId(state.idempotencyRecord),
                    state.requestFingerprint,
                    replayJson);
        } catch (Throwable throwable) {
            BusinessException exception = upstreamErrorService.toBusinessException(throwable);
            recordTerminalFailureWithoutCharge(state, exception, exception.getStatus().value());
            emitTerminalError(state, exception);
            return;
        }

        try {
            emitFrames(state, finalFrames, true);
            state.sink.complete();
        } catch (IOException exception) {
            state.sink.completeWithError(exception);
        } finally {
            dispose(state.subscriptionRef);
        }
    }

    /** Charges already-emitted partial work, or records an uncharged pre-output failure. */
    private void handleStreamFailure(
            StreamCallState state,
            BusinessException exception,
            boolean notifyClient) {
        handleStreamFailure(state, exception, exception.getStatus().value(), notifyClient);
    }

    private void handleStreamFailure(
            StreamCallState state,
            BusinessException exception,
            int statusCode,
            boolean notifyClient) {
        if (!state.finalized.compareAndSet(false, true)) {
            return;
        }
        dispose(state.subscriptionRef);

        RequestLog failedLog = toRequestLog(
                state.requestId,
                state.principal,
                state.model,
                providerKeyId(state.credentialRef.get()),
                statusCode,
                state.startedAt,
                exception.getCode());
        try {
            if (state.accumulator.hasProviderEvent()) {
                StreamResponseAccumulator.Result result = state.accumulator.result();
                UsageRecord usageRecord = toUsageRecord(
                        failedLog, result.response().getUsage(), state.model, result.usageSource());
                billingService.recordPartialUsage(
                        failedLog,
                        usageRecord,
                        idempotencyId(state.idempotencyRecord),
                        state.requestFingerprint,
                        exception.getCode());
            } else {
                billingService.recordFailedRequestWithoutCharge(
                        failedLog, idempotencyId(state.idempotencyRecord), exception.getCode());
            }
        } catch (Throwable billingThrowable) {
            logger.warn("Failed to persist terminal stream failure, requestId={}", state.requestId, billingThrowable);
            recordTerminalFailureWithoutCharge(state, exception, statusCode);
        }

        if (notifyClient) {
            emitTerminalError(state, exception);
        } else {
            state.sink.completeWithError(new IOException(exception.getMessage()));
        }
    }

    private void handleStreamTimeout(StreamCallState state) {
        BusinessException exception = new BusinessException(
                "PROVIDER_TIMEOUT",
                "Provider request timed out",
                HttpStatus.GATEWAY_TIMEOUT);
        providerKeyAvailabilityService.markFailure(
                providerKeyId(state.credentialRef.get()), exception, new TimeoutException());
        handleStreamFailure(state, exception, true);
    }

    private void handleClientDisconnect(StreamCallState state, Throwable throwable) {
        BusinessException exception = new BusinessException(
                "CLIENT_STREAM_CLOSED",
                "Client closed the stream",
                HttpStatus.BAD_REQUEST);
        handleStreamFailure(state, exception, 499, false);
    }

    private void recordTerminalFailureWithoutCharge(
            StreamCallState state,
            BusinessException exception,
            int statusCode) {
        RequestLog failedLog = toRequestLog(
                state.requestId,
                state.principal,
                state.model,
                providerKeyId(state.credentialRef.get()),
                statusCode,
                state.startedAt,
                exception.getCode());
        try {
            billingService.recordFailedRequestWithoutCharge(
                    failedLog, idempotencyId(state.idempotencyRecord), exception.getCode());
        } catch (Throwable persistenceException) {
            logger.error("Failed to persist stream failure, requestId={}", state.requestId, persistenceException);
        }
    }

    private void emitTerminalError(StreamCallState state, BusinessException exception) {
        try {
            emitFrames(
                    state,
                    gatewayStreamResponseAdapter.error(state.protocol, exception, state.requestId),
                    false);
            state.sink.complete();
        } catch (IOException ioException) {
            state.sink.completeWithError(ioException);
        } finally {
            dispose(state.subscriptionRef);
        }
    }

    private void emitFrames(
            StreamCallState state,
            List<GatewayStreamFrame> frames,
            boolean remember) throws IOException {
        for (GatewayStreamFrame frame : frames) {
            state.sink.send(frame);
            if (remember) {
                state.sentFrames.add(frame);
            }
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

    public void writeUsageRecord(
            RequestLog requestLog,
            Usage usage,
            ProviderModelPricing model,
            Long idempotencyRecordId,
            String requestFingerprint,
            String responseJson) {
        UsageRecord usageRecord = toUsageRecord(
                requestLog,
                usage,
                model,
                usage == null ? "MISSING" : StreamResponseAccumulator.USAGE_SOURCE_PROVIDER);
        billingService.recordSuccessfulUsage(
                requestLog,
                usageRecord,
                idempotencyRecordId,
                requestFingerprint,
                responseJson);
    }

    private UsageRecord toUsageRecord(
            RequestLog requestLog,
            Usage usage,
            ProviderModelPricing model,
            String usageSource) {
        int promptTokens = token(usage == null ? null : usage.getPromptTokens());
        int completionTokens = token(usage == null ? null : usage.getCompletionTokens());
        int totalTokens = token(usage == null ? null : usage.getTotalTokens());
        UsageRecord usageRecord = new UsageRecord(requestLog.getRequestId(), requestLog.getUserId(),
                requestLog.getModelId(), promptTokens, completionTokens, totalTokens,
                calculateCost(model, promptTokens, completionTokens));
        usageRecord.setProviderKeyId(requestLog.getProviderKeyId());
        usageRecord.setUsageSource(usageSource);
        return usageRecord;
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

    /** Uses insert-ignore as the concurrency-safe ownership claim for an idempotency key. */
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

    private void replayIdempotentStream(
            IdempotencyRecord existingRecord,
            String requestFingerprint,
            GatewayStreamProtocol protocol,
            GatewayStreamSink sink) {
        if (!existingRecord.getRequestFingerprint().equals(requestFingerprint)) {
            throw new BusinessException(
                    "IDEMPOTENCY_KEY_REUSED",
                    "Idempotency-Key was already used with a different request payload",
                    HttpStatus.CONFLICT);
        }
        if (STATUS_FAILED.equals(existingRecord.getStatus())) {
            throw new BusinessException(
                    existingRecord.getErrorCode(), "Request previously failed", HttpStatus.CONFLICT);
        }
        if (!STATUS_COMPLETED.equals(existingRecord.getStatus())) {
            throw new BusinessException(
                    "IDEMPOTENCY_REQUEST_IN_PROGRESS",
                    "A request with this Idempotency-Key is still being processed",
                    HttpStatus.CONFLICT);
        }

        StreamReplayResponse replay = readStreamReplay(existingRecord.getResponseJson());
        ChatResponse response = replay.getResponse();
        if (response == null) {
            response = readJson(existingRecord.getResponseJson());
        }
        response.setRequestId(existingRecord.getRequestId());
        List<GatewayStreamFrame> frames = replay.getFrames().isEmpty()
                ? gatewayStreamResponseAdapter.replay(protocol, response)
                : replay.getFrames();

        try {
            for (GatewayStreamFrame frame : frames) {
                sink.send(frame);
            }
            sink.complete();
        } catch (IOException exception) {
            sink.completeWithError(exception);
        }
    }

    private StreamReplayResponse readStreamReplay(String json) {
        if (!hasText(json)) {
            throw new IllegalStateException("Cached stream response is empty");
        }
        try {
            StreamReplayResponse replay = objectMapper.readValue(json, StreamReplayResponse.class);
            return replay == null ? new StreamReplayResponse() : replay;
        } catch (IOException exception) {
            return new StreamReplayResponse();
        }
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

    private static final class StreamCallState {
        private final String requestId;
        private final long startedAt;
        private final GatewayStreamSink sink;
        private final ApiKeyPrincipal principal;
        private final ProviderModelPricing model;
        private final IdempotencyRecord idempotencyRecord;
        private final String requestFingerprint;
        private final GatewayStreamProtocol protocol;
        private final GatewayStreamResponseAdapter.Session responseSession;
        private final StreamResponseAccumulator accumulator;
        private final AtomicReference<ProviderCredential> credentialRef = new AtomicReference<>();
        private final AtomicReference<Disposable> subscriptionRef = new AtomicReference<>();
        private final AtomicBoolean finalized = new AtomicBoolean(false);
        private final List<GatewayStreamFrame> sentFrames = new ArrayList<>();

        private StreamCallState(
                String requestId,
                long startedAt,
                GatewayStreamSink sink,
                ApiKeyPrincipal principal,
                ProviderModelPricing model,
                IdempotencyRecord idempotencyRecord,
                String requestFingerprint,
                GatewayStreamProtocol protocol,
                GatewayStreamResponseAdapter.Session responseSession,
                StreamResponseAccumulator accumulator) {
            this.requestId = requestId;
            this.startedAt = startedAt;
            this.sink = sink;
            this.principal = principal;
            this.model = model;
            this.idempotencyRecord = idempotencyRecord;
            this.requestFingerprint = requestFingerprint;
            this.protocol = protocol;
            this.responseSession = responseSession;
            this.accumulator = accumulator;
        }
    }

    private record IdempotencyClaim(boolean created, IdempotencyRecord record) {
    }
}
