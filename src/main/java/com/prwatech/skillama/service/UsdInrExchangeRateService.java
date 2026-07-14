package com.prwatech.skillama.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fetches live USD → INR from a public FX API (cached ~1h). No manual rate in admin settings.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UsdInrExchangeRateService {

    private static final long CACHE_TTL_MS = 60 * 60 * 1000L;

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${skillama.exchange-rate.api-url:https://api.frankfurter.app/latest?from=USD&to=INR}")
    private String apiUrl;

    private final AtomicReference<CachedRate> cache = new AtomicReference<>();

    @PostConstruct
    void warmCache() {
        try {
            refresh();
        } catch (Exception e) {
            log.warn("Initial USD/INR rate fetch failed; will retry on next request", e);
        }
    }

    public double getUsdToInrRate() {
        CachedRate current = cache.get();
        if (current == null || current.isStale()) {
            refresh();
            current = cache.get();
        }
        if (current == null || current.rate() <= 0) {
            throw new IllegalStateException("USD/INR exchange rate is temporarily unavailable");
        }
        return current.rate();
    }

    public String getRateAsOfDate() {
        CachedRate current = cache.get();
        return current != null ? current.asOfDate() : null;
    }

    public Instant getRateFetchedAt() {
        CachedRate current = cache.get();
        return current != null ? current.fetchedAt() : null;
    }

    private synchronized void refresh() {
        CachedRate current = cache.get();
        if (current != null && !current.isStale()) {
            return;
        }
        CachedRate fetched = fetchFromApi();
        cache.set(fetched);
        log.info("USD/INR rate updated: {} (as of {})", fetched.rate(), fetched.asOfDate());
    }

    private CachedRate fetchFromApi() {
        ResponseEntity<String> response = restTemplate.getForEntity(apiUrl, String.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("Exchange rate API returned " + response.getStatusCode());
        }
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode inr = root.path("rates").path("INR");
            if (!inr.isNumber() || inr.asDouble() <= 0) {
                throw new IllegalStateException("Exchange rate API response missing INR rate");
            }
            String asOf = root.path("date").asText(null);
            return new CachedRate(inr.asDouble(), asOf, Instant.now());
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse exchange rate API response", e);
        }
    }

    private record CachedRate(double rate, String asOfDate, Instant fetchedAt) {
        boolean isStale() {
            return fetchedAt == null
                    || Instant.now().toEpochMilli() - fetchedAt.toEpochMilli() > CACHE_TTL_MS;
        }
    }
}
