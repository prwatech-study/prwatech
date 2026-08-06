package com.prwatech.skillama.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prwatech.skillama.dto.AiGenerationUsage;
import com.prwatech.skillama.dto.AiQueryReplyDTO;
import com.prwatech.skillama.dto.AiUsageRecordRequestDTO;
import com.prwatech.skillama.dto.ConfirmationResponseDTO;
import com.prwatech.skillama.dto.ExamFeedbackResponseDTO;
import com.prwatech.skillama.dto.ExamRecommendationResponseDTO;
import com.prwatech.skillama.dto.GeneratedCodeAssistDTO;
import com.prwatech.skillama.dto.GeneratedImageDTO;
import com.prwatech.skillama.dto.GeneratedLectureDTO;
import com.prwatech.skillama.dto.GeneratedPracticeCodeDTO;
import com.prwatech.skillama.dto.GeneratedPracticalCodeDTO;
import com.prwatech.skillama.dto.LectureStartInstructionResponseDTO;
import com.prwatech.skillama.dto.TextToAudioResponseDTO;
import com.prwatech.skillama.dto.TutorIntroResponseDTO;
import com.prwatech.skillama.dto.GeneratedQuizDTO;
import com.prwatech.skillama.dto.ModuleQuizQuestionDTO;
import com.prwatech.skillama.dto.ProxiedAudioDTO;
import com.prwatech.skillama.dto.UserNameResponseDTO;
import com.prwatech.skillama.model.ExamDifficulty;
import com.prwatech.skillama.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Backend -> ai-tutor (Flask) client. Currently used for AI lecture-image generation.
 * Routes to the dev-ai host when the platform devMode flag is on, mirroring the
 * frontend's AI environment resolution.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SkillamaAiClient {

    private static final int AI_CONNECT_TIMEOUT_MS = 5_000;
    private static final int AI_READ_TIMEOUT_MS = 60_000;

    private final AiUsageService aiUsageService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = buildRestTemplate();

    private static RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(AI_CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(AI_READ_TIMEOUT_MS);
        return new RestTemplate(factory);
    }

    private static boolean isTimeout(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof SocketTimeoutException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    @Value("${skillama.ai.base-url:https://ai.prwatech.com}")
    private String aiBaseUrl;

    @Value("${skillama.ai.dev-base-url:https://dev-ai.prwatech.com}")
    private String aiDevBaseUrl;

    @Value("${skillama.ai.internal-key:}")
    private String aiInternalKey;

    /**
     * ai-tutor rejects this header only once its own ENFORCE_INTERNAL_AUTH toggle is on;
     * until then it's advisory (logged, not enforced) so rollout can't break the
     * still-browser-direct ai-tutor routes that don't send this header at all yet.
     */
    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (aiInternalKey != null && !aiInternalKey.isBlank()) {
            headers.set("X-Internal-Service-Key", aiInternalKey);
        }
        return headers;
    }

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
     * Single choke point for "gate + call + record" — every learner-facing generation
     * method below routes through here instead of inlining its own assertWithinBudget/
     * recordUsage pair, so a future method added to this client is metered automatically
     * just by using this wrapper, and there's exactly one place that pairing can drift.
     *
     * <p>{@code user} is nullable: guest-accessible callers (e.g. Chat) pass null to skip
     * metering entirely, matching how guests are governed by a separate question-count
     * cap rather than the AI wallet.
     */
    private <T extends AiGenerationUsage> T meteredCall(User user, String endpoint, String courseId, Supplier<T> call) {
        if (user != null) {
            aiUsageService.assertWithinBudget(user);
        }
        T result = call.get();
        if (user != null) {
            aiUsageService.recordUsage(AiUsageRecordRequestDTO.builder()
                    .userId(user.getId())
                    .endpoint(endpoint)
                    .courseId(courseId)
                    .modelId(result.getModelId())
                    .inputTokens(result.getInputTokens())
                    .outputTokens(result.getOutputTokens())
                    .totalTokens(result.getTotalTokens())
                    .build());
        }
        return result;
    }

    /**
     * ai-tutor's audio/download routes return host-relative paths (e.g. "/get_audio/x.mp3").
     * Callers that persist the URL for a later server-side re-fetch (fetchAudioBytes needs
     * an absolute URI) must resolve it through here; callers that hand the path straight to
     * the browser (which resolves it itself, e.g. getFullUrl in skillama-lms) should not.
     */
    private String resolveMediaUrl(String path) {
        if (path == null || path.isBlank() || path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        String base = resolveBaseUrl();
        return path.startsWith("/") ? base + path : base + "/" + path;
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

        HttpHeaders headers = buildHeaders();
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

        HttpHeaders headers = buildHeaders();
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
            User user, String endpoint, String courseId,
            String course, String moduleName, List<String> topics, int numQuestions, String difficulty) {
        return meteredCall(user, endpoint, courseId,
                () -> generateQuizQuestionsRaw(course, moduleName, topics, numQuestions, difficulty));
    }

    private GeneratedQuizDTO generateQuizQuestionsRaw(
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

        HttpHeaders headers = buildHeaders();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response;
        try {
            response = restTemplate.postForEntity(url, entity, String.class);
        } catch (org.springframework.web.client.RestClientException e) {
            log.error("Quiz generation request to {} failed", url, e);
            String message = isTimeout(e)
                    ? "The quiz is taking longer than expected to generate. Please try again in a moment."
                    : "We couldn't generate the quiz right now. Please try again in a moment.";
            throw new IllegalStateException(message, e);
        }
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            log.error("AI quiz service returned {} for {}", response.getStatusCode(), url);
            throw new IllegalStateException("We couldn't generate the quiz right now. Please try again in a moment.");
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
    private static final java.util.regex.Pattern OVERALL_LINE =
            java.util.regex.Pattern.compile("(?i)OVERALL:\\s*(.+)");
    private static final java.util.regex.Pattern RECOMMENDATION_LINE =
            java.util.regex.Pattern.compile("(?i)RECOMMENDATION:\\s*(.+)");

    /**
     * Best-effort "AI Recommended Test" — there is no dedicated recommendation endpoint
     * on the ai-tutor service, so this reuses the generic {@code handle_query} endpoint
     * with a structured-reply prompt and parses the response defensively. Every field
     * falls back to a safe default if the model doesn't follow the requested format —
     * this call must never fail the exam-center page just because parsing came up short.
     */
    public ExamRecommendationResponseDTO getExamRecommendation(
            User user, String courseId, String courseName, Double completionPercent, Double avgQuizScorePercent) {
        return meteredCall(user, "ai_exam_recommendation", courseId,
                () -> getExamRecommendationRaw(courseName, completionPercent, avgQuizScorePercent));
    }

    private ExamRecommendationResponseDTO getExamRecommendationRaw(
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

        HttpHeaders headers = buildHeaders();
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

    /**
     * Best-effort AI feedback on a just-graded exam attempt. Same free-text-prompt
     * pattern as {@link #getExamRecommendation}: never throws — any failure falls
     * back to a generic, still-useful message so a flaky AI call never blocks a
     * submission that has already been graded.
     */
    public ExamFeedbackResponseDTO getExamFeedback(
            User user, String courseId, String courseName, String topicOrModule, int score, int maxScore,
            double percentage, List<String> wrongTopics) {
        return meteredCall(user, "ai_exam_feedback", courseId,
                () -> getExamFeedbackRaw(courseName, topicOrModule, score, maxScore, percentage, wrongTopics));
    }

    private ExamFeedbackResponseDTO getExamFeedbackRaw(
            String courseName, String topicOrModule, int score, int maxScore,
            double percentage, List<String> wrongTopics) {
        String url = resolveBaseUrl() + "/handle_query";

        String missedLine = wrongTopics != null && !wrongTopics.isEmpty()
                ? "They missed questions on: " + String.join(", ", wrongTopics) + ". "
                : "They answered every question correctly. ";

        String prompt = "A student just finished an exam on " + topicOrModule + " in " + courseName + ". "
                + "They scored " + score + "/" + maxScore + " (" + Math.round(percentage) + "%). "
                + missedLine
                + "Write encouraging, constructive feedback. Never call the student weak, bad, or "
                + "failing at anything — phrase any gap as an area to focus on next, not a shortcoming. "
                + "Reply with EXACTLY this format, one field per line, no extra commentary:\n"
                + "OVERALL: <one short sentence on their overall performance>\n"
                + "RECOMMENDATION: <one short sentence recommending what to focus on next>";

        Map<String, Object> body = new HashMap<>();
        body.put("query", prompt);
        body.put("topic", topicOrModule);
        body.put("prev_topic_list", new ArrayList<>());
        body.put("course", courseName);

        HttpHeaders headers = buildHeaders();
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
            log.warn("Exam feedback call failed; falling back to a generic message", e);
        }

        String overall = extractGroup(OVERALL_LINE, responseText);
        String recommendation = extractGroup(RECOMMENDATION_LINE, responseText);

        return ExamFeedbackResponseDTO.builder()
                .overallFeedback(overall != null ? overall
                        : "You scored " + Math.round(percentage) + "% on " + topicOrModule + ".")
                .recommendationText(recommendation != null ? recommendation
                        : "Review the topics you missed and try again.")
                .modelId(modelId)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .totalTokens(totalTokens)
                .build();
    }

    /**
     * Answers a free-text learner question (Chat, AI Mentor) server-side. Previously this
     * ran directly from the browser to ai-tutor, so nothing server-side ever gated it
     * against the AI wallet budget (see DoubtService/UserProfileService for the gate this
     * closes). Unlike {@link #getExamRecommendation}/{@link #getExamFeedback}, this is NOT
     * best-effort — a failed call must surface as an error, not a silent fallback, since
     * the caller is waiting on an actual answer.
     *
     * <p><b>ai-tutor (Flask) contract — POST {aiBaseUrl}/handle_query</b>
     * <pre>
     * Request JSON:  { "query": String, "topic": String, "course": String,
     *                  "prev_topic_list": [String], "last_ai_reply": String (optional) }
     * Response JSON: { "response_text": String, "audio_url": String, "subtitle_path": String,
     *                  "usage": { "inputTokens": int, "outputTokens": int, "totalTokens": int },
     *                  "error": String  // optional; present on failure
     *                }
     * </pre>
     */
    public AiQueryReplyDTO answerQuery(
            User user, String endpoint, String courseId,
            String query, String topic, String course, List<String> prevTopicList, String lastAiReply) {
        return meteredCall(user, endpoint, courseId,
                () -> answerQueryRaw(query, topic, course, prevTopicList, lastAiReply));
    }

    private AiQueryReplyDTO answerQueryRaw(
            String query, String topic, String course, List<String> prevTopicList, String lastAiReply) {
        String url = resolveBaseUrl() + "/handle_query";

        Map<String, Object> body = new HashMap<>();
        body.put("query", query != null ? query : "");
        body.put("topic", topic != null ? topic : "");
        body.put("course", course != null ? course : "");
        body.put("prev_topic_list", prevTopicList != null ? prevTopicList : new ArrayList<>());
        if (lastAiReply != null && !lastAiReply.isBlank()) {
            body.put("last_ai_reply", lastAiReply);
        }

        HttpHeaders headers = buildHeaders();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response;
        try {
            response = restTemplate.postForEntity(url, entity, String.class);
        } catch (org.springframework.web.client.RestClientException e) {
            String message = isTimeout(e)
                    ? "That's taking longer than expected to answer. Please try again in a moment."
                    : "We couldn't get an answer right now. Please try again in a moment.";
            throw new IllegalStateException(message, e);
        }
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException(
                    "We couldn't get an answer right now. Please try again in a moment.");
        }
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.has("data") ? root.path("data") : root;
            if (data.hasNonNull("error")) {
                throw new IllegalStateException("AI query service error: " + data.path("error").asText());
            }
            JsonNode usage = data.path("usage");
            return AiQueryReplyDTO.builder()
                    .responseText(data.path("response_text").asText(null))
                    .audioUrl(resolveMediaUrl(data.path("audio_url").asText(null)))
                    .subtitlePath(data.path("subtitle_path").asText(null))
                    .modelId(data.path("model_id").asText(null))
                    .inputTokens(usage.path("inputTokens").asInt(0))
                    .outputTokens(usage.path("outputTokens").asInt(0))
                    .totalTokens(usage.path("totalTokens").asInt(0))
                    .build();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse AI query service response", e);
        }
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
    /**
     * Runs the Debug/Code Execution AI call server-side. Both the Debug tab's "explain my
     * code" step and the Code Execution tab's "run" step hit the same ai-tutor endpoint —
     * the caller distinguishes them only for its own persistence/tracking, not in this call.
     * Previously this request was made directly from the browser to ai-tutor, so the backend
     * never saw the code, output, or explanation (see CodeAssistService for the tracking gap
     * this closes). Timeout is generous since code execution can be slow.
     *
     * <p><b>ai-tutor (Flask) contract — POST {aiBaseUrl}/generate_output</b>
     * <pre>
     * Request JSON:  { "code": String, "course": String,
     *                  "real_output": String (optional), "real_error": String (optional) }
     * Response JSON: { "code_output": String, "corrected_code": String, "response_text": String,
     *                  "audio_url": String, "subtitle_path": String, "model_id": String,
     *                  "usage": { "inputTokens": int, "outputTokens": int, "totalTokens": int },
     *                  "error": String  // optional; present on failure
     *                }
     * </pre>
     *
     * @param realOutput real sandbox stdout, when prwatech already executed this code for real
     *                   (Python courses only — see CodeAssistService); null otherwise, in which
     *                   case ai-tutor falls back to its own hallucinated "execution" as before.
     * @param realError  real sandbox error, mutually exclusive with realOutput
     */
    public GeneratedCodeAssistDTO runCodeAssist(
            User user, String endpoint, String courseId, String code, String course,
            String realOutput, String realError) {
        return meteredCall(user, endpoint, courseId, () -> runCodeAssistRaw(code, course, realOutput, realError));
    }

    private GeneratedCodeAssistDTO runCodeAssistRaw(String code, String course, String realOutput, String realError) {
        String url = resolveBaseUrl() + "/generate_output";

        Map<String, Object> body = new HashMap<>();
        body.put("code", code != null ? code : "");
        body.put("course", course != null ? course : "");
        if (realOutput != null) {
            body.put("real_output", realOutput);
        }
        if (realError != null) {
            body.put("real_error", realError);
        }

        HttpHeaders headers = buildHeaders();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(url, org.springframework.http.HttpMethod.POST, entity, String.class);
        } catch (org.springframework.web.client.RestClientException e) {
            throw new IllegalStateException("code assist request to " + url + " failed: " + e.getMessage(), e);
        }
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("AI code assist service returned " + response.getStatusCode());
        }
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.has("data") ? root.path("data") : root;
            if (data.hasNonNull("error")) {
                throw new IllegalStateException("AI code assist service error: " + data.path("error").asText());
            }
            JsonNode usage = data.path("usage");
            return GeneratedCodeAssistDTO.builder()
                    .codeOutput(data.path("code_output").asText(null))
                    .correctedCode(data.path("corrected_code").asText(null))
                    .responseText(data.path("response_text").asText(null))
                    .audioUrl(resolveMediaUrl(data.path("audio_url").asText(null)))
                    .subtitlePath(data.path("subtitle_path").asText(null))
                    .modelId(data.path("model_id").asText(null))
                    .inputTokens(usage.path("inputTokens").asInt(0))
                    .outputTokens(usage.path("outputTokens").asInt(0))
                    .totalTokens(usage.path("totalTokens").asInt(0))
                    .build();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse AI code assist service response", e);
        }
    }

    /**
     * Generates (but does not execute) Python code for a practical exercise. The AI receives
     * only {@code dataset_id}/{@code dataset_name}/{@code task} — never a bucket name, storage
     * path, or credential; the generated code is validated and actually executed by
     * {@link PracticalSandboxService}, not by ai-tutor or an LLM pretending to run it.
     *
     * <p><b>ai-tutor (Flask) contract — POST {aiBaseUrl}/generate_practical_code</b>
     * <pre>
     * Request JSON:  { "dataset_id": String, "dataset_name": String, "task": String }
     * Response JSON: { "code": String,
     *                  "usage": { "inputTokens": int, "outputTokens": int, "totalTokens": int },
     *                  "error": String  // optional; present on failure
     *                }
     * </pre>
     */
    public GeneratedPracticalCodeDTO generatePracticalCode(
            User user, String courseId, String datasetId, String datasetName, String task) {
        return meteredCall(user, "generate_practical_code", courseId,
                () -> generatePracticalCodeRaw(datasetId, datasetName, task));
    }

    private GeneratedPracticalCodeDTO generatePracticalCodeRaw(String datasetId, String datasetName, String task) {
        String url = resolveBaseUrl() + "/generate_practical_code";

        Map<String, Object> body = new HashMap<>();
        body.put("dataset_id", datasetId != null ? datasetId : "");
        body.put("dataset_name", datasetName != null ? datasetName : "");
        body.put("task", task != null ? task : "");

        HttpHeaders headers = buildHeaders();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(url, org.springframework.http.HttpMethod.POST, entity, String.class);
        } catch (org.springframework.web.client.RestClientException e) {
            throw new IllegalStateException("practical code generation request to " + url + " failed: " + e.getMessage(), e);
        }
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("AI practical code generation service returned " + response.getStatusCode());
        }
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.has("data") ? root.path("data") : root;
            if (data.hasNonNull("error")) {
                throw new IllegalStateException("AI practical code generation error: " + data.path("error").asText());
            }
            JsonNode usage = data.path("usage");
            return GeneratedPracticalCodeDTO.builder()
                    .code(data.path("code").asText(null))
                    .modelId(data.path("model_id").asText(null))
                    .inputTokens(usage.path("inputTokens").asInt(0))
                    .outputTokens(usage.path("outputTokens").asInt(0))
                    .totalTokens(usage.path("totalTokens").asInt(0))
                    .build();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse AI practical code generation response", e);
        }
    }

    /**
     * Generates one lecture (script text + Polly audio + SRT captions) server-side.
     * Previously this ran directly from the browser to ai-tutor, so nothing server-side
     * ever gated it against the AI wallet budget (see LectureService for the gate this closes).
     *
     * <p><b>ai-tutor (Flask) contract — POST {aiBaseUrl}/generate_lecture</b>
     * <pre>
     * Request JSON:  { "section": String, "course": String }
     * Response JSON: { "lecture_text": String, "audio_url": String, "subtitle_path": String,
     *                  "usage": { "inputTokens": int, "outputTokens": int, "totalTokens": int },
     *                  "error": String  // optional; present on failure
     *                }
     * </pre>
     */
    public GeneratedLectureDTO generateLecture(User user, String courseId, String section, String course) {
        return meteredCall(user, "lecture_generation", courseId, () -> generateLectureRaw(section, course));
    }

    private GeneratedLectureDTO generateLectureRaw(String section, String course) {
        String url = resolveBaseUrl() + "/generate_lecture";

        Map<String, Object> body = new HashMap<>();
        body.put("section", section != null ? section : "");
        body.put("course", course != null ? course : "");

        HttpHeaders headers = buildHeaders();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response;
        try {
            response = restTemplate.postForEntity(url, entity, String.class);
        } catch (org.springframework.web.client.RestClientException e) {
            String message = isTimeout(e)
                    ? "The lecture is taking longer than expected to generate. Please try again in a moment."
                    : "We couldn't generate the lecture right now. Please try again in a moment.";
            throw new IllegalStateException(message, e);
        }
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException(
                    "We couldn't generate the lecture right now. Please try again in a moment.");
        }
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.has("data") ? root.path("data") : root;
            if (data.hasNonNull("error")) {
                throw new IllegalStateException("AI lecture service error: " + data.path("error").asText());
            }
            JsonNode usage = data.path("usage");
            return GeneratedLectureDTO.builder()
                    .lectureText(data.path("lecture_text").asText(null))
                    .audioUrl(data.path("audio_url").asText(null))
                    .subtitlePath(data.path("subtitle_path").asText(null))
                    .modelId(data.path("model_id").asText(null))
                    .inputTokens(usage.path("inputTokens").asInt(0))
                    .outputTokens(usage.path("outputTokens").asInt(0))
                    .totalTokens(usage.path("totalTokens").asInt(0))
                    .build();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse AI lecture service response", e);
        }
    }

    /**
     * Generates practice code server-side. Previously this ran directly from the browser
     * to ai-tutor, so nothing server-side ever gated it against the AI wallet budget (see
     * PracticeCodeService for the gate this closes).
     *
     * <p><b>ai-tutor (Flask) contract — POST {aiBaseUrl}/generate_code</b>
     * <pre>
     * Request JSON:  { "query": String, "code_instruction": String, "course": String }
     * Response JSON: { "code_result": String, "audio_url": String, "subtitle_path": String,
     *                  "usage": { "inputTokens": int, "outputTokens": int, "totalTokens": int },
     *                  "error": String  // optional; present on failure
     *                }
     * </pre>
     */
    public GeneratedPracticeCodeDTO generatePracticeCode(
            User user, String courseId, String query, String codeInstruction, String course) {
        return meteredCall(user, "practice_code_generation", courseId,
                () -> generatePracticeCodeRaw(query, codeInstruction, course));
    }

    private GeneratedPracticeCodeDTO generatePracticeCodeRaw(String query, String codeInstruction, String course) {
        String url = resolveBaseUrl() + "/generate_code";

        Map<String, Object> body = new HashMap<>();
        body.put("query", query != null ? query : "");
        body.put("code_instruction", codeInstruction != null ? codeInstruction : "");
        body.put("course", course != null ? course : "");

        HttpHeaders headers = buildHeaders();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response;
        try {
            response = restTemplate.postForEntity(url, entity, String.class);
        } catch (org.springframework.web.client.RestClientException e) {
            String message = isTimeout(e)
                    ? "That's taking longer than expected to generate. Please try again in a moment."
                    : "We couldn't generate the code right now. Please try again in a moment.";
            throw new IllegalStateException(message, e);
        }
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException(
                    "We couldn't generate the code right now. Please try again in a moment.");
        }
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.has("data") ? root.path("data") : root;
            if (data.hasNonNull("error")) {
                throw new IllegalStateException("AI practice code service error: " + data.path("error").asText());
            }
            JsonNode usage = data.path("usage");
            return GeneratedPracticeCodeDTO.builder()
                    .codeResult(data.path("code_result").asText(null))
                    .audioUrl(data.path("audio_url").asText(null))
                    .subtitlePath(data.path("subtitle_path").asText(null))
                    .modelId(data.path("model_id").asText(null))
                    .inputTokens(usage.path("inputTokens").asInt(0))
                    .outputTokens(usage.path("outputTokens").asInt(0))
                    .totalTokens(usage.path("totalTokens").asInt(0))
                    .build();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse AI practice code service response", e);
        }
    }

    /**
     * Tutor welcome intro — templated text + Polly TTS, no LLM call. Proxied purely to
     * close the browser-direct-to-ai-tutor bypass (see Phase 1 internal-key lockdown);
     * not budget-gated since there's no AI generation cost here to meter.
     *
     * <p><b>ai-tutor (Flask) contract — POST {aiBaseUrl}/introduce_tutor</b>
     * <pre>
     * Request JSON:  { "course": String }
     * Response JSON: { "introduction_text": String, "audio_url": String, "subtitle_path": String }
     * </pre>
     */
    public TutorIntroResponseDTO introduceTutor(String course) {
        String url = resolveBaseUrl() + "/introduce_tutor";
        Map<String, Object> body = new HashMap<>();
        body.put("course", course != null ? course : "");
        JsonNode data = postForJson(url, body, "tutor introduction");
        return TutorIntroResponseDTO.builder()
                .introductionText(data.path("introduction_text").asText(null))
                .audioUrl(data.path("audio_url").asText(null))
                .subtitlePath(data.path("subtitle_path").asText(null))
                .build();
    }

    /**
     * Lecture-session opening narration — templated text + Polly TTS, no LLM call. Proxied
     * purely to close the browser-direct-to-ai-tutor bypass; not budget-gated (no AI
     * generation cost here).
     *
     * <p><b>ai-tutor (Flask) contract — POST {aiBaseUrl}/lecture_start_instruction</b>
     * <pre>
     * Request JSON:  { "topics": [String] }
     * Response JSON: { "lecture_introduction_text": String, "audio_url": String, "subtitle_path": String }
     * </pre>
     */
    public LectureStartInstructionResponseDTO lectureStartInstruction(List<String> topics) {
        String url = resolveBaseUrl() + "/lecture_start_instruction";
        Map<String, Object> body = new HashMap<>();
        body.put("topics", topics != null ? topics : new ArrayList<>());
        JsonNode data = postForJson(url, body, "lecture start instruction");
        return LectureStartInstructionResponseDTO.builder()
                .lectureIntroductionText(data.path("lecture_introduction_text").asText(null))
                .audioUrl(data.path("audio_url").asText(null))
                .subtitlePath(data.path("subtitle_path").asText(null))
                .build();
    }

    /**
     * Generic text-to-speech — no LLM call. Proxied purely to close the browser-direct-to-
     * ai-tutor bypass; not budget-gated (no AI generation cost here).
     *
     * <p><b>ai-tutor (Flask) contract — POST {aiBaseUrl}/text_to_audio</b>
     * <pre>
     * Request JSON:  { "code": String }  (the field is misleadingly named on the ai-tutor side — plain text)
     * Response JSON: { "audio_url": String, "subtitle_path": String }
     * </pre>
     */
    public TextToAudioResponseDTO textToAudio(String text) {
        String url = resolveBaseUrl() + "/text_to_audio";
        Map<String, Object> body = new HashMap<>();
        body.put("code", text != null ? text : "");
        JsonNode data = postForJson(url, body, "text-to-audio");
        return TextToAudioResponseDTO.builder()
                .audioUrl(data.path("audio_url").asText(null))
                .subtitlePath(data.path("subtitle_path").asText(null))
                .build();
    }

    /** Shared POST-JSON-and-unwrap helper for the zero-cost, non-budget-gated utility calls above. */
    private JsonNode postForJson(String url, Map<String, Object> body, String label) {
        HttpHeaders headers = buildHeaders();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response;
        try {
            response = restTemplate.postForEntity(url, entity, String.class);
        } catch (org.springframework.web.client.RestClientException e) {
            throw new IllegalStateException(
                    "We couldn't reach the " + label + " service right now. Please try again in a moment.", e);
        }
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException(label + " service returned " + response.getStatusCode());
        }
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.has("data") ? root.path("data") : root;
            if (data.hasNonNull("error")) {
                throw new IllegalStateException(label + " service error: " + data.path("error").asText());
            }
            return data;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse " + label + " service response", e);
        }
    }

    private HttpHeaders buildMultipartHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (aiInternalKey != null && !aiInternalKey.isBlank()) {
            headers.set("X-Internal-Service-Key", aiInternalKey);
        }
        return headers;
    }

    private MultiValueMap<String, Object> audioBody(byte[] audioBytes, String filename) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource resource = new ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() {
                return filename != null && !filename.isBlank() ? filename : "recording.webm";
            }
        };
        body.add("audio", resource);
        return body;
    }

    /**
     * Transcribes a mic recording (AWS Transcribe, no LLM). Proxied purely to close the
     * browser-direct-to-ai-tutor bypass; not budget-gated — it supports an already-billed
     * subsequent action (the question it feeds into), so metering it separately would
     * double-charge a single user action.
     *
     * <p><b>ai-tutor (Flask) contract — POST {aiBaseUrl}/audio_to_text</b>
     * <pre>
     * Request: multipart/form-data, field "audio" = recording bytes
     * Response JSON: { "transcript": String, "error": String (optional; present on failure) }
     * </pre>
     */
    public String transcribeAudio(byte[] audioBytes, String filename) {
        String url = resolveBaseUrl() + "/audio_to_text";
        HttpEntity<MultiValueMap<String, Object>> entity =
                new HttpEntity<>(audioBody(audioBytes, filename), buildMultipartHeaders());

        ResponseEntity<String> response;
        try {
            response = restTemplate.postForEntity(url, entity, String.class);
        } catch (org.springframework.web.client.RestClientException e) {
            throw new IllegalStateException("We couldn't transcribe that. Please try again.", e);
        }
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("Transcription service returned " + response.getStatusCode());
        }
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.has("data") ? root.path("data") : root;
            if (data.hasNonNull("error")) {
                throw new IllegalStateException("Transcription service error: " + data.path("error").asText());
            }
            return data.path("transcript").asText("");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse transcription service response", e);
        }
    }

    /**
     * Classifies a spoken yes/no agreement (AWS Transcribe + keyword match, LLM fallback only
     * when keywords are unclear). Proxied purely to close the browser-direct-to-ai-tutor
     * bypass; not budget-gated — a small onboarding classification step, same category as
     * introduceTutor/lectureStartInstruction.
     *
     * <p><b>ai-tutor (Flask) contract — POST {aiBaseUrl}/confirmation</b>
     * <pre>
     * Request: multipart/form-data, field "audio" = recording bytes
     * Response JSON: { "response_text": boolean, "audio_url": String, "subtitle_path": String,
     *                  "transcript": String, "error": String (optional; present on failure) }
     * </pre>
     */
    public ConfirmationResponseDTO confirmAgreement(byte[] audioBytes, String filename) {
        String url = resolveBaseUrl() + "/confirmation";
        HttpEntity<MultiValueMap<String, Object>> entity =
                new HttpEntity<>(audioBody(audioBytes, filename), buildMultipartHeaders());

        ResponseEntity<String> response;
        try {
            response = restTemplate.postForEntity(url, entity, String.class);
        } catch (org.springframework.web.client.RestClientException e) {
            throw new IllegalStateException("We couldn't process that. Please try again.", e);
        }
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("Confirmation service returned " + response.getStatusCode());
        }
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.has("data") ? root.path("data") : root;
            if (data.hasNonNull("error")) {
                throw new IllegalStateException("Confirmation service error: " + data.path("error").asText());
            }
            return ConfirmationResponseDTO.builder()
                    .responseText(data.path("response_text").asBoolean(false))
                    .audioUrl(data.path("audio_url").asText(null))
                    .subtitlePath(data.path("subtitle_path").asText(null))
                    .transcript(data.path("transcript").asText(null))
                    .build();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse confirmation service response", e);
        }
    }

    /**
     * Extracts the learner's name from a spoken introduction (AWS Transcribe + a small LLM
     * extraction call). Proxied purely to close the browser-direct-to-ai-tutor bypass; not
     * budget-gated — a one-time onboarding step, same category as introduceTutor.
     *
     * <p><b>ai-tutor (Flask) contract — POST {aiBaseUrl}/get_user_name</b>
     * <pre>
     * Request: multipart/form-data, field "audio" = recording bytes
     * Response JSON: { "welcome_text": String, "audio_url": String, "subtitle_path": String,
     *                  "error": String (optional; present on failure) }
     * </pre>
     */
    public UserNameResponseDTO extractUserName(byte[] audioBytes, String filename) {
        String url = resolveBaseUrl() + "/get_user_name";
        HttpEntity<MultiValueMap<String, Object>> entity =
                new HttpEntity<>(audioBody(audioBytes, filename), buildMultipartHeaders());

        ResponseEntity<String> response;
        try {
            response = restTemplate.postForEntity(url, entity, String.class);
        } catch (org.springframework.web.client.RestClientException e) {
            throw new IllegalStateException("We couldn't process that. Please try again.", e);
        }
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("User-name service returned " + response.getStatusCode());
        }
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.has("data") ? root.path("data") : root;
            if (data.hasNonNull("error")) {
                throw new IllegalStateException("User-name service error: " + data.path("error").asText());
            }
            return UserNameResponseDTO.builder()
                    .welcomeText(data.path("welcome_text").asText(null))
                    .audioUrl(data.path("audio_url").asText(null))
                    .subtitlePath(data.path("subtitle_path").asText(null))
                    .build();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse user-name service response", e);
        }
    }

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
