package com.example.aigateway.provider.claude;

import com.example.aigateway.dto.request.ChatRequest;
import com.example.aigateway.dto.response.ChatResponse;
import com.example.aigateway.exception.BusinessException;
import com.example.aigateway.provider.ProviderAdapter;
import com.example.aigateway.provider.ProviderCredential;
import com.example.aigateway.provider.ProviderStreamEvent;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/** Placeholder adapter boundary for native Anthropic integration. */
@Component
public class ClaudeAdapter implements ProviderAdapter {
    @Override
    public List<String> providerCodes() {
        return List.of("CLAUDE");
    }

    @Override
    public ChatResponse chat(ChatRequest request, ProviderCredential credential) {
        throw notImplemented();
    }

    @Override
    public Flux<ProviderStreamEvent> stream(ChatRequest request, ProviderCredential credential) {
        return Flux.error(notImplemented());
    }

    private BusinessException notImplemented() {
        return new BusinessException(
                "PROVIDER_ADAPTER_NOT_IMPLEMENTED",
                "Claude adapter is not implemented yet",
                HttpStatus.NOT_IMPLEMENTED
        );
    }
}
