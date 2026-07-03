package com.example.aigateway.provider.openai;

import com.example.aigateway.client.UpstreamHttpClient;
import com.example.aigateway.dto.request.ChatRequest;
import com.example.aigateway.dto.response.ChatResponse;
import com.example.aigateway.exception.BusinessException;
import com.example.aigateway.provider.ProviderAdapter;
import com.example.aigateway.provider.ProviderCredential;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

@Component
public class OpenAiAdapter implements ProviderAdapter {
    private final UpstreamHttpClient upstreamHttpClient;

    public OpenAiAdapter(UpstreamHttpClient upstreamHttpClient) {
        this.upstreamHttpClient = upstreamHttpClient;
    }

    @Override
    public List<String> providerCodes() {
        return List.of("OPENAI", "OPENROUTER");
    }

    @Override
    public ChatResponse chat(ChatRequest request, ProviderCredential credential) {
        OpenaiRequest upstreamRequest = OpenaiRequest.from(request);
        upstreamRequest.setStream(false);

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
    public Flux<String> stream(ChatRequest request, ProviderCredential credential) {
        OpenaiRequest upstreamRequest = OpenaiRequest.from(request);
        upstreamRequest.setStream(true);
        return upstreamHttpClient.postJsonStream(
                chatCompletionsUrl(credential),
                upstreamRequest,
                authorizationHeaders(credential)
        );
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
            case 429 -> new BusinessException(
                    "PROVIDER_RATE_LIMITED",
                    "Provider rate limit exceeded",
                    HttpStatus.TOO_MANY_REQUESTS
            );
            case 401, 403 -> new BusinessException(
                    "PROVIDER_AUTH_FAILED",
                    "Provider authentication failed",
                    HttpStatus.BAD_GATEWAY
            );
            case 404 -> new BusinessException(
                    "PROVIDER_MODEL_NOT_FOUND",
                    "Provider model not found",
                    HttpStatus.BAD_GATEWAY
            );
            default -> new BusinessException(
                    "PROVIDER_UPSTREAM_ERROR",
                    "Provider request failed",
                    HttpStatus.BAD_GATEWAY
            );
        };
    }
}
