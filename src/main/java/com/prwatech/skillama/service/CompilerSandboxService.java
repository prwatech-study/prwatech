package com.prwatech.skillama.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Invokes a skillama-compiler-* Lambda. The family (python, java, native, …) selects the
 * function. The runtime id inside the payload selects the version. Validation of which
 * runtime a course may use happens in {@link CompilerCatalog} before this is called.
 */
@Service
@RequiredArgsConstructor
public class CompilerSandboxService {

    private final LambdaClient lambdaClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.lambda.compiler-function-prefix:skillama-compiler-}")
    private String functionPrefix;

    public PracticalSandboxService.SandboxResult execute(
            String lambdaFamily, String runtime, String code, String stdin, boolean preferPytest) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("runtime", runtime);
        payload.put("code", code);
        payload.put("runner", preferPytest ? "auto" : "script");
        if (stdin != null && !stdin.isBlank()) {
            // Scanner.nextLine(), input(), and cin see a line only after a newline.
            // The editor's input box is a single line, so it never includes one.
            if (!stdin.endsWith("\n")) {
                stdin = stdin + "\n";
            }
            payload.put("stdin", stdin);
        }

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build compiler invoke payload", e);
        }

        InvokeResponse response;
        try {
            response = lambdaClient.invoke(InvokeRequest.builder()
                    .functionName(functionPrefix + lambdaFamily)
                    .payload(SdkBytes.fromUtf8String(payloadJson))
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("Compiler execution unavailable: " + e.getMessage(), e);
        }

        String responseBody = response.payload().asUtf8String();
        if (response.functionError() != null) {
            throw new IllegalStateException("Compiler execution failed: " + responseBody);
        }

        JsonNode json;
        try {
            json = objectMapper.readTree(responseBody);
        } catch (Exception e) {
            throw new IllegalStateException("Compiler returned an unreadable response: " + responseBody, e);
        }

        return PracticalSandboxService.SandboxResult.builder()
                .status(json.path("status").asText(null))
                .stdout(json.path("stdout").asText(null))
                .error(json.path("error").asText(null))
                .violations(List.of())
                .figures(List.of())
                .build();
    }
}
