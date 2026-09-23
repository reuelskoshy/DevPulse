package com.devpulse.common.security;

import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EncryptionKeyInitializer {

    public EncryptionKeyInitializer(@Value("${devpulse.security.encryption-key}") String base64Key) {
        EncryptedStringConverter.init(Base64.getDecoder().decode(base64Key));
    }
}
