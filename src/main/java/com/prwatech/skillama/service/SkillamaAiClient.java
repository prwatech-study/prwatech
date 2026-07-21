package com.prwatech.skillama.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prwatech.skillama.dto.GeneratedImageDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Backend -> ai-tutor (Flask) client. Currently used for AI lecture-image generation.
 * Routes to the dev-ai host when the platform devMode flag is on, mirroring the
 * frontend's AI environment resolution.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SkillamaAiClient {

    private final AiUsageService aiUsageService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${skillama.ai.base-url:https://ai.prwatech.com}")
    private String aiBaseUrl;

    @Value("${skillama.ai.dev-base-url:https://dev-ai.prwatech.com}")
    private String aiDevBaseUrl;

    private String resolveBaseUrl() {
        boolean devMode = false;
        try {
            devMode = aiUsageService.loadSettings().isDevModeEnabled();
        } catch (Exception e) {
            log.warn("Could not resolve AI dev-mode flag; defaulting to prod AI host", e);
        }
        String base = devMode ? aiDevBaseUrl : aiBaseUrl;
        return base != null && base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /**
     * Generates ONE Napkin-style diagram (SVG) from a lecture script. Blocking call.
     *
     * @param variant 0..N — rotates theme/type so regenerations look different.
     */
    public GeneratedImageDTO generateImage(String label, String course, String scriptText, int variant) {
        String url = resolveBaseUrl() + "/generate_image";

        Map<String, Object> body = new HashMap<>();
        body.put("label", label != null ? label : "");
        body.put("course", course != null ? course : "");
        body.put("scriptText", scriptText != null ? scriptText : "");
        body.put("variant", variant);
        body.put("format", "svg");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("AI image service returned " + response.getStatusCode());
        }
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.hasNonNull("error")) {
                throw new IllegalStateException("AI image service error: " + root.path("error").asText());
            }
            JsonNode usage = root.path("usage");
            return GeneratedImageDTO.builder()
                    .imageBase64(root.path("image_base64").asText(null))
                    .contentType(root.path("content_type").asText("image/svg+xml"))
                    .diagramType(root.path("diagram_type").asText(""))
                    .title(root.path("title").asText(""))
                    .modelId(root.path("model_id").asText(null))
                    .inputTokens(usage.path("inputTokens").asInt(0))
                    .outputTokens(usage.path("outputTokens").asInt(0))
                    .totalTokens(usage.path("totalTokens").asInt(0))
                    .build();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse AI image service response", e);
        }
    }

    /**
     * Generates ONE 16:9 course-thumbnail image (raster) from the course name +
     * description. Blocking call. Mirrors {@link #generateImage} but targets the
     * thumbnail endpoint, which returns a photographic/graphic raster (PNG) rather
     * than an SVG diagram.
     *
     * <p><b>ai-tutor (Flask) contract — POST {aiBaseUrl}/generate_thumbnail</b>
     * <pre>
     * Request JSON:  { "name": String, "description": String, "variant": int,
     *                  "format": "png", "aspect_ratio": "16:9" }
     * Response JSON: { "image_base64": String,           // required
     *                  "content_type": String,           // e.g. "image/png"
     *                  "model_id": String,               // for cost estimation
     *                  "usage": { "inputTokens": int, "outputTokens": int, "totalTokens": int },
     *                  "error": String                   // optional; present on failure
     *                }
     * </pre>
     *
     * @param variant 0..N — rotates the prompt so regenerations look different.
     */
    public GeneratedImageDTO generateThumbnail(String name, String description, int variant) {
        String url = resolveBaseUrl() + "/generate_thumbnail";

        Map<String, Object> body = new HashMap<>();
        body.put("name", name != null ? name : "");
        body.put("description", description != null ? description : "");
        body.put("variant", variant);
        body.put("format", "png");
        body.put("aspect_ratio", "16:9");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("AI thumbnail service returned " + response.getStatusCode());
        }
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.hasNonNull("error")) {
                throw new IllegalStateException("AI thumbnail service error: " + root.path("error").asText());
            }
            JsonNode usage = root.path("usage");
            return GeneratedImageDTO.builder()
                    .imageBase64(root.path("image_base64").asText(null))
                    .contentType(root.path("content_type").asText("image/png"))
                    .diagramType(root.path("diagram_type").asText(""))
                    .title(root.path("title").asText(""))
                    .modelId(root.path("model_id").asText(null))
                    .inputTokens(usage.path("inputTokens").asInt(0))
                    .outputTokens(usage.path("outputTokens").asInt(0))
                    .totalTokens(usage.path("totalTokens").asInt(0))
                    .build();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse AI thumbnail service response", e);
        }
    }
}
