package com.example.backend.auth.infrastructure.turnstile;

import com.example.backend.auth.application.port.out.HumanVerificationPort;
import com.example.backend.auth.infrastructure.config.AuthProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Validates Turnstile tokens against Cloudflare's siteverify endpoint with the reactive WebClient (non-blocking
 * I/O and a hard timeout). Fails closed: if Cloudflare cannot be reached, the sign-in is refused.
 */
@Component
class CloudflareTurnstileAdapter implements HumanVerificationPort {
    private static final Logger LOGGER = LoggerFactory.getLogger(CloudflareTurnstileAdapter.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final AuthProperties.Turnstile settings;
    private final WebClient client;

    CloudflareTurnstileAdapter(AuthProperties properties, WebClient.Builder builder) {
        this.settings = properties.turnstile();
        this.client = builder.baseUrl(settings.verifyUrl()).build();
    }

    @Override
    public boolean isEnabled() {
        return settings.enabled();
    }

    @Override
    public boolean verify(String token, String clientIp) {
        if (token == null || token.isBlank()) {
            return false;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", settings.secretKey());
        form.add("response", token);
        if (clientIp != null) {
            form.add("remoteip", clientIp);
        }
        try {
            SiteVerifyResponse response = client.post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(SiteVerifyResponse.class)
                    .timeout(TIMEOUT)
                    .block();
            if (response == null || !response.success()) {
                LOGGER.info("Turnstile rejected a sign-in: {}", response == null ? "empty response" : response.errorCodes());
                return false;
            }
            return true;
        } catch (RuntimeException exception) {
            LOGGER.warn("Turnstile verification unavailable: {}", exception.getMessage());
            return false;
        }
    }

    record SiteVerifyResponse(boolean success, @JsonProperty("error-codes") List<String> errorCodes) {
    }
}
