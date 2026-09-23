package com.devpulse.common.security;

import java.security.SecureRandom;
import java.util.Base64;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EncryptedStringConverterTest {

    @BeforeAll
    static void initKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        EncryptedStringConverter.init(key);
    }

    @Test
    void roundTripsAValue() {
        EncryptedStringConverter converter = new EncryptedStringConverter();
        String plaintext = "gho_someRealisticLookingGitHubToken12345";

        String encrypted = converter.convertToDatabaseColumn(plaintext);

        assertThat(encrypted).isNotNull().isNotEqualTo(plaintext);
        assertThat(Base64.getDecoder().decode(encrypted)).isNotEmpty();
        assertThat(converter.convertToEntityAttribute(encrypted)).isEqualTo(plaintext);
    }

    @Test
    void producesDifferentCiphertextEachTimeDueToRandomIv() {
        EncryptedStringConverter converter = new EncryptedStringConverter();
        String plaintext = "same-token-value";

        String first = converter.convertToDatabaseColumn(plaintext);
        String second = converter.convertToDatabaseColumn(plaintext);

        assertThat(first).isNotEqualTo(second);
        assertThat(converter.convertToEntityAttribute(first)).isEqualTo(plaintext);
        assertThat(converter.convertToEntityAttribute(second)).isEqualTo(plaintext);
    }

    @Test
    void handlesNullValues() {
        EncryptedStringConverter converter = new EncryptedStringConverter();
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
