package com.example.aigateway.provider.openai;

import com.example.aigateway.client.UpstreamHttpClient;
import com.example.aigateway.dto.request.ChatRequest;
import com.example.aigateway.dto.response.ChatResponse;
import com.example.aigateway.exception.BusinessException;
import com.example.aigateway.exception.ProviderUpstreamException;
import com.example.aigateway.provider.ProviderAdapter;
import com.example.aigateway.provider.ProviderCredential;
import com.example.aigateway.provider.ProviderStreamEvent;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

/** Calls OpenAI-compatible chat-completions providers. */
@Component
public class OpenAiAdapter implements ProviderAdapter {
    private final UpstreamHttpClient upstreamHttpClient;
    private final OpenAiStreamEventParser streamEventParser;

    public OpenAiAdapter(
            UpstreamHttpClient upstreamHttpClient,
            OpenAiStreamEventParser streamEventParser) {
        this.upstreamHttpClient = upstreamHttpClient;
        this.streamEventParser = streamEventParser;
    }

    @Override
    public List<String> providerCodes() {
        return List.of("OPENAI", "OPENROUTER");
    }

    @Override
    public ChatResponse chat(ChatRequest request, ProviderCredential credential) {
        Object upstreamRequest = upstreamRequest(request, false);

        OpenaiResponse response;
        try {
            response = upstreamHttpClient.postJson(
                    chatCompletionsUrl(credential),
                    upstreamRequest,
                    authorizationHeaders(credential),
                    OpenaiResponse.class
            ).block();
        } catch (WebClientResponseException exception) {
            throw toProviderException(exception);
        } catch (WebClientRequestException exception) {
            throw new BusinessException(
                    "PROVIDER_UNAVAILABLE",
                    "Provider request failed",
                    HttpStatus.BAD_GATEWAY
            );
        }

        if (response == null) {
            throw new BusinessException("PROVIDER_EMPTY_RESPONSE", "Provider returned empty response", HttpStatus.BAD_GATEWAY);
        }
        return response.toChatResponse();
    }

    @Override
    public Flux<ProviderStreamEvent> stream(ChatRequest request, ProviderCredential credential) {
        Object upstreamRequest = upstreamRequest(request, true);
        return upstreamHttpClient.postJsonStream(
                chatCompletionsUrl(credential),
                upstreamRequest,
                authorizationHeaders(credential)
        ).map(streamEventParser::parse);
    }

    private Object upstreamRequest(ChatRequest request, boolean stream) {
        if (request.getOpenAiPayload() == null) {
            OpenaiRequest upstreamRequest = OpenaiRequest.from(request);
            upstreamRequest.setStream(stream);
            upstreamRequest.setStreamOptions(stream ? OpenaiRequest.StreamOptions.includeUsage() : null);
            return upstreamRequest;
        }

        ObjectNode payload = request.getOpenAiPayload().deepCopy();
        payload.remove(List.of("provider", "provider_code", "providerCode"));
        if (payload.has("maxTokens") && !payload.has("max_tokens")) {
            payload.set("max_tokens", payload.get("maxTokens"));
        }
        payload.remove("maxTokens");
        payload.put("stream", stream);
        if (stream) {
            ObjectNode streamOptions;
            if (payload.path("stream_options").isObject()) {
                streamOptions = (ObjectNode) payload.path("stream_options");
            } else {
                streamOptions = payload.putObject("stream_options");
            }
            streamOptions.put("include_usage", true);
        } else {
            payload.remove("stream_options");
        }
        return payload;
    }

    private String chatCompletionsUrl(ProviderCredential credential) {
        return credential.requireBaseUrl().replaceAll("/+$", "") + "/chat/completions";
    }

    private Map<String, String> authorizationHeaders(ProviderCredential credential) {
        Map<String, String> headers = new LinkedHashMap<>(credential.getHeaders());
        headers.put(HttpHeaders.AUTHORIZATION, "Bearer " + credential.requireApiKey());
        return headers;
    }

    private BusinessException toProviderException(WebClientResponseException exception) {
        int statusCode = exception.getStatusCode().value();
        return switch (statusCode) {
            case 429 -> new ProviderUpstreamException(
                    "PROVIDER_RATE_LIMITED",
                    "Provider rate limit exceeded",
                    HttpStatus.TOO_MANY_REQUESTS,
                    statusCode,
                    exception.getHeaders(),
                    exception.getResponseBodyAsString()
            );
            case 401, 403 -> new ProviderUpstreamException(
                    "PROVIDER_AUTH_FAILED",
                    "Provider authentication failed",
                    HttpStatus.BAD_GATEWAY,
                    statusCode,
                    exception.getHeaders(),
                    exception.getResponseBodyAsString()
            );
            case 404 -> new ProviderUpstreamException(
                    "PROVIDER_MODEL_NOT_FOUND",
                    "Provider model not found",
                    HttpStatus.BAD_GATEWAY,
                    statusCode,
                    exception.getHeaders(),
                    exception.getResponseBodyAsString()
            );
            default -> new ProviderUpstreamException(
                    "PROVIDER_UPSTREAM_ERROR",
                    "Provider request failed",
                    HttpStatus.BAD_GATEWAY,
                    statusCode,
                    exception.getHeaders(),
                    exception.getResponseBodyAsString()
            );
        };
    }
}
