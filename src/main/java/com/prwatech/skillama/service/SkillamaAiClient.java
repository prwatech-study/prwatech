package com.prwatech.skillama.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prwatech.skillama.dto.ExamRecommendationResponseDTO;
import com.prwatech.skillama.dto.GeneratedImageDTO;
import com.prwatech.skillama.dto.GeneratedQuizDTO;
import com.prwatech.skillama.dto.ModuleQuizQuestionDTO;
import com.prwatech.skillama.dto.ProxiedAudioDTO;
import com.prwatech.skillama.model.ExamDifficulty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

        ResponseEntity<String> response;
        try {
            response = restTemplate.postForEntity(url, entity, String.class);
        } catch (org.springframework.web.client.RestClientException e) {
            // A 4xx/5xx (e.g. the endpoint not yet deployed) or a transport error — surface as an
            // AI-service failure so the controller returns 502 rather than a confusing 400.
            throw new IllegalStateException("thumbnail request to " + url + " failed: " + e.getMessage(), e);
        }
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

    /**
     * Generates quiz/exam questions server-side. Blocking call. This is the ONLY place
     * questions + their answer key should ever be requested from the AI service — the
     * answer key must never round-trip through the browser (that was the integrity hole
     * in the original client-orchestrated Module Quiz flow). Shared by both Module Quiz
     * and AI Exam generation.
     *
     * <p><b>ai-tutor (Flask) contract — POST {aiBaseUrl}/generate_quiz</b>
     * <pre>
     * Request JSON:  { "course": String, "module_name": String, "topics": [String],
     *                  "num_questions": int, "difficulty": String (optional) }
     * Response JSON: { "quiz_title": String,
     *                  "questions": [ { "id": int, "question": String,
     *                                   "options": [ {"key": String, "text": String} ],
     *                                   "correctKey": String, "explanation": String } ],
     *                  "model_id": String,
     *                  "usage": { "inputTokens": int, "outputTokens": int, "totalTokens": int },
     *                  "error": String  // optional; present on failure
     *                }
     * </pre>
     */
    public GeneratedQuizDTO generateQuizQuestions(
            String course, String moduleName, List<String> topics, int numQuestions, String difficulty) {
        String url = resolveBaseUrl() + "/generate_quiz";

        Map<String, Object> body = new HashMap<>();
        body.put("course", course != null ? course : "");
        body.put("module_name", moduleName != null ? moduleName : "");
        body.put("topics", topics != null ? topics : new ArrayList<>());
        body.put("num_questions", numQuestions);
        if (difficulty != null) {
            body.put("difficulty", difficulty);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response;
        try {
            response = restTemplate.postForEntity(url, entity, String.class);
        } catch (org.springframework.web.client.RestClientException e) {
            throw new IllegalStateException("quiz generation request to " + url + " failed: " + e.getMessage(), e);
        }
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("AI quiz service returned " + response.getStatusCode());
        }
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.has("data") ? root.path("data") : root;
            if (data.hasNonNull("error")) {
                throw new IllegalStateException("AI quiz service error: " + data.path("error").asText());
            }

            List<ModuleQuizQuestionDTO> questions = new ArrayList<>();
            for (JsonNode q : data.path("questions")) {
                questions.add(objectMapper.treeToValue(q, ModuleQuizQuestionDTO.class));
            }

            JsonNode usage = data.path("usage");
            return GeneratedQuizDTO.builder()
                    .quizTitle(data.path("quiz_title").asText(null))
                    .questions(questions)
                    .modelId(data.path("model_id").asText(null))
                    .inputTokens(usage.path("inputTokens").asInt(0))
                    .outputTokens(usage.path("outputTokens").asInt(0))
                    .totalTokens(usage.path("totalTokens").asInt(0))
                    .build();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse AI quiz service response", e);
        }
    }

    private static final java.util.regex.Pattern DIFFICULTY_LINE =
            java.util.regex.Pattern.compile("(?i)DIFFICULTY:\\s*(\\w+)");
    private static final java.util.regex.Pattern TOPIC_LINE =
            java.util.regex.Pattern.compile("(?i)TOPIC:\\s*(.+)");
    private static final java.util.regex.Pattern REASONING_LINE =
            java.util.regex.Pattern.compile("(?i)REASONING:\\s*(.+)");
    private static final java.util.regex.Pattern MINUTES_LINE =
            java.util.regex.Pattern.compile("(?i)ESTIMATED_MINUTES:\\s*(\\d+)");
    private static final java.util.regex.Pattern SCORE_LINE =
            java.util.regex.Pattern.compile("(?i)EXPECTED_SCORE:\\s*(\\d+)");

    /**
     * Best-effort "AI Recommended Test" — there is no dedicated recommendation endpoint
     * on the ai-tutor service, so this reuses the generic {@code handle_query} endpoint
     * with a structured-reply prompt and parses the response defensively. Every field
     * falls back to a safe default if the model doesn't follow the requested format —
     * this call must never fail the exam-center page just because parsing came up short.
     */
    public ExamRecommendationResponseDTO getExamRecommendation(
            String courseName, Double completionPercent, Double avgQuizScorePercent) {
        String url = resolveBaseUrl() + "/handle_query";

        String progressLine = completionPercent != null
                ? "Their course completion is " + Math.round(completionPercent) + "%. "
                : "Their course completion is not yet known. ";
        String scoreLine = avgQuizScorePercent != null
                ? "Their average quiz score so far is " + Math.round(avgQuizScorePercent) + "%. "
                : "They have no quiz history yet. ";

        String prompt = "You are recommending a practice exam for a student learning " + courseName + ". "
                + progressLine + scoreLine
                + "Reply with EXACTLY this format, one field per line, no extra commentary:\n"
                + "DIFFICULTY: <Beginner|Intermediate|Advanced|Expert>\n"
                + "TOPIC: <a specific topic within " + courseName + ">\n"
                + "REASONING: <one short sentence>\n"
                + "ESTIMATED_MINUTES: <integer>\n"
                + "EXPECTED_SCORE: <integer percent>";

        Map<String, Object> body = new HashMap<>();
        body.put("query", prompt);
        body.put("topic", "General " + courseName);
        body.put("prev_topic_list", new ArrayList<>());
        body.put("course", courseName);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        String responseText = "";
        String modelId = null;
        int inputTokens = 0;
        int outputTokens = 0;
        int totalTokens = 0;
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode data = root.has("data") ? root.path("data") : root;
                responseText = data.path("response_text").asText("");
                modelId = data.path("model_id").asText(null);
                JsonNode usage = data.path("usage");
                inputTokens = usage.path("inputTokens").asInt(0);
                outputTokens = usage.path("outputTokens").asInt(0);
                totalTokens = usage.path("totalTokens").asInt(0);
            }
        } catch (Exception e) {
            log.warn("Exam recommendation call failed; falling back to defaults", e);
        }

        ExamDifficulty difficulty = parseDifficulty(responseText);
        String topic = extractGroup(TOPIC_LINE, responseText);
        String reasoning = extractGroup(REASONING_LINE, responseText);
        Integer estimatedMinutes = parseInt(extractGroup(MINUTES_LINE, responseText));
        Integer expectedScore = parseInt(extractGroup(SCORE_LINE, responseText));

        return ExamRecommendationResponseDTO.builder()
                .difficulty(difficulty != null ? difficulty : ExamDifficulty.INTERMEDIATE)
                .topic(topic != null ? topic : "General " + courseName)
                .reasoning(reasoning != null ? reasoning
                        : "Based on your current progress, this level should be a good challenge.")
                .estimatedMinutes(estimatedMinutes != null ? estimatedMinutes : 15)
                .expectedScorePercent(expectedScore != null ? expectedScore : 75)
                .modelId(modelId)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .totalTokens(totalTokens)
                .build();
    }

    private ExamDifficulty parseDifficulty(String text) {
        String raw = extractGroup(DIFFICULTY_LINE, text);
        if (raw == null) return null;
        try {
            return ExamDifficulty.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String extractGroup(java.util.regex.Pattern pattern, String text) {
        if (text == null) return null;
        java.util.regex.Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private Integer parseInt(String value) {
        if (value == null) return null;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Fetches spoken-answer audio bytes server-side so callers can proxy playback to an
     * authenticated, ownership-checked client instead of ever handing out the underlying
     * ai-tutor URL — that URL is unauthenticated and permanent, so anyone who obtained it
     * any other way (logs, a DB export, a shared link) could otherwise play it forever
     * with no login required.
     */
    public ProxiedAudioDTO fetchAudioBytes(String audioUrl) {
        if (audioUrl == null || audioUrl.isBlank()) {
            throw new IllegalArgumentException("audioUrl is required");
        }
        ResponseEntity<byte[]> response;
        try {
            response = restTemplate.getForEntity(audioUrl, byte[].class);
        } catch (org.springframework.web.client.RestClientException e) {
            throw new IllegalStateException("audio fetch failed: " + e.getMessage(), e);
        }
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("AI audio service returned " + response.getStatusCode());
        }
        String contentType = response.getHeaders().getContentType() != null
                ? response.getHeaders().getContentType().toString()
                : "audio/mpeg";
        return ProxiedAudioDTO.builder()
                .data(response.getBody())
                .contentType(contentType)
                .build();
    }
}
