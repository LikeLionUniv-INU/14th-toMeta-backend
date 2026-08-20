package com.likelion.tometa.domain.report.client;

import com.likelion.tometa.domain.report.code.ReportErrorCode;
import com.likelion.tometa.domain.report.support.DailyReportAiResult;
import com.likelion.tometa.domain.report.support.DailyReportGenerationContext;
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
public class OpenAiDailyReportClient {

    private static final String INSTRUCTIONS = """
            너는 사용자의 하루 생활 기록과 건강 데이터를 기반으로 피부 관리 일간 리포트를 작성하는 AI다.

            입력 데이터에는 다음 정보가 포함될 수 있다.
            - 사용자가 기록한 피부 상태
            - 아침과 밤에 사용한 화장품, 제품 유형, 주요 성분
            - 사용자가 먹은 음식
            - 사용자가 작성한 특이사항
            - 수면 시간
            - 평균 피부온도
            - 운동 시간
            - 총 칼로리 소모량
            - 여성 사용자의 생리주기
            - 남성 사용자의 평균 산소포화도

            반드시 입력으로 제공된 정보만 사용한다.
            null이거나 비어 있는 데이터는 분석 근거로 사용하지 않는다.
            확인되지 않은 생활 습관이나 건강 상태를 추측해서 만들지 않는다.

            피부 상태와 생활 데이터 사이의 관계는 확정적인 인과관계로 표현하지 않는다.
            "~때문이다", "~가 원인이다"처럼 단정하지 말고
            "~와 관련이 있을 수 있다", "~한 영향을 줄 가능성이 있다"처럼 표현한다.

            질병을 진단하거나 의학적 치료, 약물 복용을 지시하지 않는다.
            화장품 성분은 입력에 포함된 성분만 언급한다.

            표현 방식:
            - 사용자에게 행동을 직접 지시하는 "~하세요", "~하지 마세요", "~해야 해요",
              "~피하세요" 등의 명령형 표현은 사용하지 않는다.
            - 피부 관리나 생활 습관에 관한 행동을 제안할 때는
              "~하는 것을 추천해요", "~해보는 것을 추천해요"와 같은 부드러운 추천형 표현을 사용한다.
            - 분석 내용은 관찰된 정보와 관련 가능성을 설명하는 데 집중하고,
              분석 중 행동 제안이 필요한 경우에도 명령형이 아닌 추천형으로 표현한다.

            aiSummary:
            오늘의 피부 상태와 생활 데이터를 종합한 핵심 요약을 한 문장으로 작성한다.
            홈 화면에서도 사용할 수 있도록 짧고 이해하기 쉽게 작성한다.

            aiAnalysis:
            피부 상태와 오늘의 기록 및 건강 데이터 중 의미 있는 정보를 연결해 1~2문장으로 설명한다.
            모든 데이터를 억지로 언급하지 말고 실제로 의미 있는 정보만 사용한다.

            personalizedSolution:
            사용자가 오늘 또는 다음 날 바로 실천할 수 있는 구체적인 피부 관리 및 생활 습관 제안을 1~2문장으로 작성한다.
            반드시 직접적인 명령형이 아닌 추천형 표현으로 작성한다.
            과도한 치료 표현이나 불필요한 공포 표현은 사용하지 않는다.

            모든 결과는 자연스러운 한국어로 작성한다.
            """;

    private final RestClient openAiRestClient;
    private final OpenAiProperties properties;
    private final JsonMapper jsonMapper;

    public DailyReportAiResult generate(DailyReportGenerationContext context) {
        try {
            JsonNode response = openAiRestClient.post()
                    .uri("/responses")
                    .body(createRequestBody(context))
                    .retrieve()
                    .body(JsonNode.class);

            validateResponse(response);

            log.debug(
                    "OpenAI 일간 리포트 생성 응답. status={}, usage={}",
                    response.path("status").asText(),
                    response.path("usage")
            );

            String outputText = extractOutputText(response);
            ReportPayload payload = jsonMapper.readValue(outputText, ReportPayload.class);
            validatePayload(payload);

            return new DailyReportAiResult(
                    payload.aiSummary().trim(),
                    payload.aiAnalysis().trim(),
                    payload.personalizedSolution().trim()
            );
        } catch (RestClientException | JacksonException e) {
            log.warn("OpenAI 일간 리포트 생성 실패: {}", e.getMessage());
            throw new GeneralException(ReportErrorCode.DAILY_REPORT_AI_GENERATION_FAILED);
        }
    }

    private Map<String, Object> createRequestBody(
            DailyReportGenerationContext context
    ) throws JacksonException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("reasoning", Map.of("effort", "low"));
        body.put("instructions", INSTRUCTIONS);
        body.put("input", jsonMapper.writeValueAsString(context));
        body.put("text", Map.of("format", createResponseFormat()));
        body.put("max_output_tokens", 800);
        body.put("store", false);
        return body;
    }

    private Map<String, Object> createResponseFormat() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("aiSummary", Map.of("type", "string"));
        properties.put("aiAnalysis", Map.of("type", "string"));
        properties.put("personalizedSolution", Map.of("type", "string"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put(
                "required",
                List.of("aiSummary", "aiAnalysis", "personalizedSolution")
        );
        schema.put("additionalProperties", false);

        return Map.of(
                "type", "json_schema",
                "name", "daily_report",
                "strict", true,
                "schema", schema
        );
    }

    private void validateResponse(JsonNode response) {
        if (response == null) {
            throw new GeneralException(
                    ReportErrorCode.DAILY_REPORT_AI_GENERATION_FAILED
            );
        }

        String status = response.path("status").asText();

        if (!"completed".equals(status)) {
            log.warn(
                    "OpenAI 일간 리포트 비정상 응답. status={}, details={}",
                    status,
                    response.path("incomplete_details")
            );
            throw new GeneralException(
                    ReportErrorCode.DAILY_REPORT_AI_GENERATION_FAILED
            );
        }
    }

    private void validatePayload(ReportPayload payload) {
        if (payload == null
                || payload.aiSummary() == null
                || payload.aiSummary().isBlank()
                || payload.aiAnalysis() == null
                || payload.aiAnalysis().isBlank()
                || payload.personalizedSolution() == null
                || payload.personalizedSolution().isBlank()) {
            throw new GeneralException(
                    ReportErrorCode.DAILY_REPORT_AI_GENERATION_FAILED
            );
        }
    }

    private String extractOutputText(JsonNode response) {
        JsonNode outputNode = response.path("output");

        if (!outputNode.isArray()) {
            throw new GeneralException(
                    ReportErrorCode.DAILY_REPORT_AI_GENERATION_FAILED
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
                if (!"output_text".equals(content.path("type").asText())) {
                    continue;
                }

                String text = content.path("text").asText();

                if (!text.isBlank()) {
                    return text;
                }
            }
        }

        throw new GeneralException(
                ReportErrorCode.DAILY_REPORT_AI_GENERATION_FAILED
        );
    }

    private record ReportPayload(
            String aiSummary,
            String aiAnalysis,
            String personalizedSolution
    ) {
    }
}
