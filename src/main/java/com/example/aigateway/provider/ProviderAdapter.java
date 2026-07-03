package com.example.aigateway.provider;

import com.example.aigateway.dto.request.ChatRequest;
import com.example.aigateway.dto.response.ChatResponse;
import java.util.List;
import reactor.core.publisher.Flux;

public interface ProviderAdapter {
    List<String> providerCodes();

    ChatResponse chat(ChatRequest request, ProviderCredential credential);

    Flux<String> stream(ChatRequest request, ProviderCredential credential);
}
