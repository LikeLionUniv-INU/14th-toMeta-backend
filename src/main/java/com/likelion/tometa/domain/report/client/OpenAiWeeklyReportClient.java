package com.likelion.tometa.domain.report.client;

import com.likelion.tometa.domain.report.code.ReportErrorCode;
import com.likelion.tometa.domain.report.support.WeeklyReportAiResult;
import com.likelion.tometa.domain.report.support.WeeklyReportGenerationContext;
import com.likelion.tometa.global.config.openai.OpenAiProperties;
import com.likelion.tometa.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiWeeklyReportClient {

    private static final String INSTRUCTIONS = """
            너는 사용자의 일주일간 피부 상태, 생활 기록, 일간 AI 분석,
            건강 데이터를 종합하여 피부 관리 주간 리포트를 작성하는 AI다.

            입력은 월요일부터 일요일까지 최대 7일의 데이터를 포함한다.
            데이터가 없는 날짜는 분석 근거로 사용하지 않는다.

            건강 데이터에는 다음 정보가 포함될 수 있다.
            - 수면 시간
            - 평균 피부온도
            - 운동 시간
            - 총 칼로리 소모량
            - 여성 사용자의 생리주기
              - menstrualCycleDay: 값이 있을 때 현재 생리주기의 몇 일째인지
              - menstrualCycleLength: menstrualCycleDay가 있으면 28, 없으면 null
              - menstrualCycleDay가 없으면 생리주기를 추론하거나 분석 근거로 사용하지 않는다.
            - 남성 사용자의 평균 산소포화도

            하루의 데이터만 반복해서 설명하지 말고
            여러 날짜 사이에서 반복되거나 변화한 패턴을 우선 분석한다.

            피부 상태와 생활 습관 또는 건강 데이터 사이의 관계는
            확정적인 인과관계로 단정하지 않는다.

            질병을 진단하거나 의학적 치료 및 약물 복용을 지시하지 않는다.
            제공되지 않은 정보를 추측해서 만들지 않는다.

            표현 방식:
            - 사용자에게 행동을 직접 지시하는 "~하세요", "~하지 마세요", "~해야 해요",
              "~피하세요" 등의 명령형 표현은 사용하지 않는다.
            - 피부 관리나 생활 습관에 관한 행동을 제안할 때는
              "~하는 것을 추천해요", "~해보는 것을 추천해요"와 같은 부드러운 추천형 표현을 사용한다.
            - 분석 내용은 일주일 동안 관찰된 패턴과 관련 가능성을 설명하는 데 집중하고,
              분석 중 행동 제안이 필요한 경우에도 명령형이 아닌 추천형으로 표현한다.

            weeklySummary:
            이번 주의 피부 상태와 생활 패턴을 종합한 핵심 요약을
            1~2문장으로 작성한다.

            analyses:
            이번 주에서 의미 있는 피부 및 생활 패턴을 최대 3개 반환한다.
            서로 같은 내용을 반복하지 않는다.
            여러 날짜의 변화를 비교할 수 있다면 이를 우선한다.

            personalizedSolution:
            다음 주에 사용자가 실천할 수 있는 구체적인 피부 관리 및
            생활 습관 개선 방법을 2~3문장으로 작성한다.
            반드시 직접적인 명령형이 아닌 추천형 표현으로 작성한다.

            모든 결과는 자연스러운 한국어로 작성한다.
            """;

    private final RestClient openAiRestClient;
    private final OpenAiProperties properties;
    private final JsonMapper jsonMapper;

    public WeeklyReportAiResult generate(
            WeeklyReportGenerationContext context
    ) {
        try {
            JsonNode response = openAiRestClient.post()
                    .uri("/responses")
                    .body(createRequestBody(context))
                    .retrieve()
                    .body(JsonNode.class);

            validateResponse(response);

            log.debug(
                    "OpenAI 주간 리포트 생성 응답. status={}, usage={}",
                    response.path("status").asText(),
                    response.path("usage")
            );

            String outputText = extractOutputText(response);
            ReportPayload payload =
                    jsonMapper.readValue(outputText, ReportPayload.class);

            validatePayload(payload);

            return new WeeklyReportAiResult(
                    payload.weeklySummary().trim(),
                    payload.analyses().stream()
                            .map(String::trim)
                            .toList(),
                    payload.personalizedSolution().trim()
            );
        } catch (RestClientException | JacksonException e) {
            log.warn("OpenAI 주간 리포트 생성 실패: {}", e.getMessage());
            throw new GeneralException(
                    ReportErrorCode.WEEKLY_REPORT_AI_GENERATION_FAILED
            );
        }
    }

    private Map<String, Object> createRequestBody(
            WeeklyReportGenerationContext context
    ) throws JacksonException {
        Map<String, Object> body = new LinkedHashMap<>();

        body.put("model", properties.model());
        body.put("reasoning", Map.of("effort", "low"));
        body.put("instructions", INSTRUCTIONS);
        body.put("input", jsonMapper.writeValueAsString(context));
        body.put("text", Map.of("format", createResponseFormat()));
        body.put("max_output_tokens", 1200);
        body.put("store", false);

        return body;
    }

    private Map<String, Object> createResponseFormat() {
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put(
                "weeklySummary",
                Map.of("type", "string")
        );

        properties.put(
                "analyses",
                Map.of(
                        "type", "array",
                        "items", Map.of("type", "string"),
                        "minItems", 1,
                        "maxItems", 3
                )
        );

        properties.put(
                "personalizedSolution",
                Map.of("type", "string")
        );

        Map<String, Object> schema = new LinkedHashMap<>();

        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put(
                "required",
                List.of(
                        "weeklySummary",
                        "analyses",
                        "personalizedSolution"
                )
        );
        schema.put("additionalProperties", false);

        return Map.of(
                "type", "json_schema",
                "name", "weekly_report",
                "strict", true,
                "schema", schema
        );
    }

    private void validateResponse(JsonNode response) {
        if (response == null) {
            throw new GeneralException(
                    ReportErrorCode.WEEKLY_REPORT_AI_GENERATION_FAILED
            );
        }

        String status = response.path("status").asText();

        if (!"completed".equals(status)) {
            log.warn(
                    "OpenAI 주간 리포트 비정상 응답. status={}, details={}",
                    status,
                    response.path("incomplete_details")
            );

            throw new GeneralException(
                    ReportErrorCode.WEEKLY_REPORT_AI_GENERATION_FAILED
            );
        }
    }

    private void validatePayload(ReportPayload payload) {
        if (payload == null
                || payload.weeklySummary() == null
                || payload.weeklySummary().isBlank()
                || payload.analyses() == null
                || payload.analyses().isEmpty()
                || payload.analyses().stream()
                .anyMatch(value ->
                        value == null || value.isBlank())
                || payload.personalizedSolution() == null
                || payload.personalizedSolution().isBlank()) {
            throw new GeneralException(
                    ReportErrorCode.WEEKLY_REPORT_AI_GENERATION_FAILED
            );
        }
    }

    private String extractOutputText(JsonNode response) {
        JsonNode outputNode = response.path("output");

        if (!outputNode.isArray()) {
            throw new GeneralException(
                    ReportErrorCode.WEEKLY_REPORT_AI_GENERATION_FAILED
            );
        }

        for (JsonNode output : outputNode) {
            if (!"message".equals(output.path("type").asText())) {
                continue;
            }

            JsonNode contentNode = output.path("content");

            if (!contentNode.isArray()) {
                continue;
            }

            for (JsonNode content : contentNode) {
                if (!"output_text".equals(
                        content.path("type").asText())) {
                    continue;
                }

                String text = content.path("text").asText();

                if (!text.isBlank()) {
                    return text;
                }
            }
        }

        throw new GeneralException(
                ReportErrorCode.WEEKLY_REPORT_AI_GENERATION_FAILED
        );
    }

    private record ReportPayload(
            String weeklySummary,
            List<String> analyses,
            String personalizedSolution
    ) {
    }
}
