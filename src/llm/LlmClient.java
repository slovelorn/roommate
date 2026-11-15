package llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class LlmClient {
    private static final String URL = "https://llm.deepaibit.com/v1/chat/completions";

    /** API 키가 없거나 잘못되었을 때 발생하는 예외 (다른 오류와 구분해서 안내하기 위함) */
    public static class ApiKeyException extends IllegalStateException {
        public ApiKeyException(String message) {
            super(message);
        }
    }

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();
    private final String apiKey;
    private final String model;

    public LlmClient(String model) {
        String key = System.getenv("SUNMOON_API_KEY");
        if (key == null || key.isBlank()) {
            throw new ApiKeyException("API 키가 설정되지 않았습니다. (SUNMOON_API_KEY 환경변수 확인)");
        }
        this.apiKey = key.trim();
        this.model = model;
    }

    /**
     * @param systemPrompt 모델에게 주는 역할/규칙 지시 (없으면 null)
     * @param userMessage  사용자 입력
     * @return 모델의 답변 문자열
     */
    public String chat(String systemPrompt, String userMessage) throws Exception {
        JsonArray messages = new JsonArray();
        if (systemPrompt != null) {
            messages.add(msg("system", systemPrompt));
        }
        messages.add(msg("user", userMessage));

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", 512);
        body.addProperty("stream", false);
        body.add("messages", messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(URL))
                .timeout(Duration.ofSeconds(120))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = http.send(
                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        // 401(인증 실패) / 403(권한 없음) 은 키 문제로 구분
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new ApiKeyException("API 키가 올바르지 않습니다. (HTTP " + response.statusCode() + ")");
        }
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException(
                    "API 요청 실패: HTTP " + response.statusCode() + "\n" + response.body());
        }

        return JsonParser.parseString(response.body()).getAsJsonObject()
                .getAsJsonArray("choices").get(0).getAsJsonObject()
                .getAsJsonObject("message")
                .get("content").getAsString();
    }

    private static JsonObject msg(String role, String content) {
        JsonObject m = new JsonObject();
        m.addProperty("role", role);
        m.addProperty("content", content);
        return m;
    }
}