package com.prwatech.skillama.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Invokes the code-execution sandbox Lambda — the same function backs both the dataset-scoped
 * practical-exercise flow and ad-hoc (no dataset) execution for the Debug/Code-Execution feature
 * on Python courses. All validation (allowed imports, restricted namespace, resource limits)
 * happens inside the Lambda itself; this service only builds the invoke payload and parses the
 * result.
 */
@Service
@RequiredArgsConstructor
public class PracticalSandboxService {

    private final LambdaClient lambdaClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.lambda.practical-sandbox-function-name:skillama-practical-sandbox-python}")
    private String sandboxFunctionName;

    /**
     * Practical-exercise mode: code runs against the dataset at storageKey, exposed both as
     * `df` and — since datasetName is passed here too — as an ephemeral /tmp/&lt;datasetName&gt;
     * file, so code that calls pd.read_csv('&lt;that exact name&gt;') directly also works. That
     * file exists only inside the Lambda invocation; S3 remains the only permanent copy.
     */
    public SandboxResult executeWithDataset(String datasetId, String storageKey, String datasetName, String code) {
        return invoke(datasetId, storageKey, datasetName, code);
    }

    /** Ad-hoc mode: no dataset — the general Debug/Code-Execution feature, Python courses only. */
    public SandboxResult executeAdHoc(String code) {
        return invoke(null, null, null, code);
    }

    private SandboxResult invoke(String datasetId, String storageKey, String datasetName, String code) {
        Map<String, Object> payload = new HashMap<>();
        if (datasetId != null) {
            payload.put("dataset_id", datasetId);
        }
        if (storageKey != null) {
            payload.put("storage_key", storageKey);
        }
        if (datasetName != null) {
            payload.put("dataset_filename", datasetName);
        }
        payload.put("code", code);

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build sandbox invoke payload", e);
        }

        InvokeResponse response;
        try {
            response = lambdaClient.invoke(InvokeRequest.builder()
                    .functionName(sandboxFunctionName)
                    .payload(SdkBytes.fromUtf8String(payloadJson))
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("Sandbox execution unavailable: " + e.getMessage(), e);
        }

        String responseBody = response.payload().asUtf8String();
        if (response.functionError() != null) {
            throw new IllegalStateException("Sandbox execution failed: " + responseBody);
        }

        JsonNode json;
        try {
            json = objectMapper.readTree(responseBody);
        } catch (Exception e) {
            throw new IllegalStateException("Sandbox returned an unreadable response: " + responseBody, e);
        }

        List<String> violations = new ArrayList<>();
        if (json.has("violations")) {
            json.get("violations").forEach(v -> violations.add(v.asText()));
        }
        List<String> figures = new ArrayList<>();
        if (json.has("figures")) {
            json.get("figures").forEach(f -> figures.add(f.asText()));
        }

        return SandboxResult.builder()
                .status(json.path("status").asText(null))
                .stdout(json.path("stdout").asText(null))
                .result(json.hasNonNull("result") ? json.get("result").asText() : null)
                .error(json.path("error").asText(null))
                .violations(violations)
                .figures(figures)
                .build();
    }

    @Getter
    @Builder
    public static class SandboxResult {
        private String status; // ok | rejected | error
        private String stdout;
        private String result;
        private String error;
        private List<String> violations;
        private List<String> figures;

        public boolean isOk() {
            return "ok".equals(status);
        }
    }
}
