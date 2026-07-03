package com.example.aigateway.provider.gemini;

import com.example.aigateway.dto.request.ChatRequest;
import com.example.aigateway.dto.response.ChatResponse;
import com.example.aigateway.exception.BusinessException;
import com.example.aigateway.provider.ProviderAdapter;
import com.example.aigateway.provider.ProviderCredential;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class GeminiAdapter implements ProviderAdapter {
    @Override
    public List<String> providerCodes() {
        return List.of("GEMINI");
    }

    @Override
    public ChatResponse chat(ChatRequest request, ProviderCredential credential) {
        throw notImplemented();
    }

    @Override
    public Flux<String> stream(ChatRequest request, ProviderCredential credential) {
        return Flux.error(notImplemented());
    }

    private BusinessException notImplemented() {
        return new BusinessException(
                "PROVIDER_ADAPTER_NOT_IMPLEMENTED",
                "Gemini adapter is not implemented yet",
                HttpStatus.NOT_IMPLEMENTED
        );
    }
}
