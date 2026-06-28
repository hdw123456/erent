package com.example.aigateway.security;

import org.jasypt.encryption.StringEncryptor;
import org.springframework.stereotype.Component;

@Component
public class ProviderKeyCrypto {
    private final StringEncryptor stringEncryptor;

    public ProviderKeyCrypto(StringEncryptor stringEncryptor) {
        this.stringEncryptor = stringEncryptor;
    }

    public String encrypt(String rawProviderKey) {
        return stringEncryptor.encrypt(rawProviderKey);
    }

    public String decrypt(String encryptedProviderKey) {
        return stringEncryptor.decrypt(encryptedProviderKey);
    }
}
