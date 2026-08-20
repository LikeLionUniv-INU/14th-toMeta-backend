package com.likelion.tometa.domain.cosmetic.client;

import com.likelion.tometa.domain.cosmetic.code.CosmeticErrorCode;
import com.likelion.tometa.domain.cosmetic.enums.ProductType;
import com.likelion.tometa.domain.cosmetic.support.CosmeticSearchCandidate;
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
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiCosmeticSearchClient {

    private static final int MAX_RESULT_COUNT = 5;
    private static final int MAX_MAIN_INGREDIENT_COUNT = 3;
    private static final List<String> PRODUCT_TYPES = ProductType.supportedValues();

    private static final String INSTRUCTIONS = """
            너는 한국에서 판매되는 기초 화장품을 검색하는 제품 검색기다.
            반드시 web_search를 사용하여 현재 웹 정보를 확인한다.

            사용자의 검색어와 관련 있는 실제 스킨케어 제품을 최대 5개 반환한다.
            검색어와 관련성이 높은 제품부터 반환한다.
            동일한 제품은 중복해서 반환하지 않는다.
            색조 화장품, 향수, 건강기능식품은 제외한다.

            productName:
            실제 웹에서 확인할 수 있는 정식 제품명을 반환한다.
            존재하지 않는 제품을 추측해서 만들지 않는다.

            brandName:
            실제 웹에서 확인할 수 있는 해당 제품의 브랜드명을 반환한다.
            공식 브랜드명을 우선한다.
            브랜드명을 명확하게 확인할 수 없으면 빈 문자열을 반환한다.
            존재하지 않는 브랜드명을 추측해서 만들지 않는다.

            productType:
            반드시 다음 값 중 하나만 사용한다.
            스킨/토너, 토너패드, 미스트, 앰플, 세럼, 에센스,
            수분크림, 진정크림, 보습크림, 로션/에멀전, 아이크림, 기타

            imageUrl:
            해당 제품을 대표하는 실제 제품 이미지의 직접 URL을 반환한다.
            제품 상세 페이지 URL은 반환하지 않는다.
            실제 이미지 URL을 명확하게 확인할 수 없으면 빈 문자열을 반환한다.
            존재하지 않는 URL을 추측해서 만들지 않는다.

            benefit:
            해당 제품의 대표적인 피부 효능 또는 사용 목적을 하나만 짧게 반환한다.
            예: 진정, 수분공급, 보습, 피지조절, 장벽강화, 주름개선, 속건조 개선
            실제 제품 정보에서 확인 가능한 내용만 반환한다.

            mainIngredients:
            실제 제품에서 확인할 수 있는 대표적인 주요 성분을 최대 3개 반환한다.
            전성분의 공식 명칭보다 사용자가 쉽게 이해할 수 있는 대표 성분명을 우선 사용한다.
            예: 약모밀꽃/잎/줄기수 → 어성초, 병풀잎추출물 → 병풀

            효능 또는 주요 성분을 확인할 수 없는 제품은 결과에서 제외한다.
            검색 결과가 없으면 items에 빈 배열을 반환한다.
            """;

    private final RestClient openAiRestClient;
    private final OpenAiProperties properties;
    private final JsonMapper jsonMapper;

    public List<CosmeticSearchCandidate> search(String keyword) {
        try {
            JsonNode response = openAiRestClient.post()
                    .uri("/responses")
                    .body(createRequestBody(keyword))
                    .retrieve()
                    .body(JsonNode.class);

            validateResponse(response);

            log.debug("OpenAI 화장품 검색 응답. status={}, usage={}", response.path("status").asText(), response.path("usage"));

            String outputText = extractOutputText(response);
            SearchPayload payload = jsonMapper.readValue(outputText, SearchPayload.class);
            validatePayload(payload);

            return normalize(payload.items());
        } catch (RestClientException | JacksonException e) {
            log.warn("OpenAI 화장품 검색 실패: {}", e.getMessage());
            throw new GeneralException(CosmeticErrorCode.COSMETIC_SEARCH_FAILED);
        }
    }

    private Map<String, Object> createRequestBody(String keyword) {
        Map<String, Object> body = new LinkedHashMap<>();

        body.put("model", properties.model());
        body.put("reasoning", Map.of("effort", "low"));
        body.put("tools", List.of(Map.of("type", "web_search", "search_context_size", "low")));
        body.put("tool_choice", "required");
        body.put("instructions", INSTRUCTIONS);
        body.put("input", "검색할 화장품: " + keyword);
        body.put("text", Map.of("format", createResponseFormat()));
        body.put("max_output_tokens", 1800);
        body.put("store", false);

        return body;
    }

    private Map<String, Object> createResponseFormat() {
        Map<String, Object> itemProperties = new LinkedHashMap<>();

        itemProperties.put("productName", Map.of("type", "string"));
        itemProperties.put("brandName", Map.of("type", "string"));
        itemProperties.put("productType", Map.of("type", "string", "enum", PRODUCT_TYPES));
        itemProperties.put("imageUrl", Map.of("type", "string"));
        itemProperties.put("benefit", Map.of("type", "string"));
        itemProperties.put("mainIngredients", Map.of(
                "type", "array",
                "items", Map.of("type", "string"),
                "minItems", 1,
                "maxItems", MAX_MAIN_INGREDIENT_COUNT
        ));

        Map<String, Object> itemSchema = new LinkedHashMap<>();
        itemSchema.put("type", "object");
        itemSchema.put("properties", itemProperties);
        itemSchema.put("required", List.of("productName", "brandName", "productType", "imageUrl", "benefit", "mainIngredients"));
        itemSchema.put("additionalProperties", false);

        Map<String, Object> itemsSchema = new LinkedHashMap<>();
        itemsSchema.put("type", "array");
        itemsSchema.put("items", itemSchema);
        itemsSchema.put("maxItems", MAX_RESULT_COUNT);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of("items", itemsSchema));
        schema.put("required", List.of("items"));
        schema.put("additionalProperties", false);

        return Map.of("type", "json_schema", "name", "cosmetic_search_results", "strict", true, "schema", schema);
    }

    private void validateResponse(JsonNode response) {
        if (response == null) {
            log.warn("OpenAI 화장품 검색 응답이 null입니다.");
            throw new GeneralException(CosmeticErrorCode.COSMETIC_SEARCH_FAILED);
        }

        String status = response.path("status").asText();

        if ("incomplete".equals(status)) {
            String reason = response.path("incomplete_details").path("reason").asText("unknown");
            log.warn("OpenAI 화장품 검색 응답이 완료되지 않았습니다. reason={}, usage={}", reason, response.path("usage"));
            throw new GeneralException(CosmeticErrorCode.COSMETIC_SEARCH_FAILED);
        }

        if (!"completed".equals(status)) {
            log.warn("OpenAI 화장품 검색 비정상 응답. status={}", status);
            throw new GeneralException(CosmeticErrorCode.COSMETIC_SEARCH_FAILED);
        }
    }

    private void validatePayload(SearchPayload payload) {
        if (payload == null || payload.items() == null) {
            log.warn("OpenAI 화장품 검색 structured output 형식이 올바르지 않습니다.");
            throw new GeneralException(CosmeticErrorCode.COSMETIC_SEARCH_FAILED);
        }
    }

    private String extractOutputText(JsonNode response) {
        JsonNode outputNode = response.path("output");

        if (!outputNode.isArray()) {
            log.warn("OpenAI 응답에 output 배열이 없습니다.");
            throw new GeneralException(CosmeticErrorCode.COSMETIC_SEARCH_FAILED);
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

        log.warn("OpenAI 응답에서 output_text를 찾을 수 없습니다.");
        throw new GeneralException(CosmeticErrorCode.COSMETIC_SEARCH_FAILED);
    }

    private List<CosmeticSearchCandidate> normalize(List<SearchItem> items) {
        if (items.isEmpty()) {
            return List.of();
        }

        Map<String, CosmeticSearchCandidate> uniqueItems = new LinkedHashMap<>();

        for (SearchItem item : items) {
            CosmeticSearchCandidate normalized = normalizeItem(item);

            if (normalized == null) {
                continue;
            }

            uniqueItems.putIfAbsent(normalized.productName().toLowerCase(Locale.ROOT), normalized);

            if (uniqueItems.size() >= MAX_RESULT_COUNT) {
                break;
            }
        }

        return List.copyOf(uniqueItems.values());
    }

    private CosmeticSearchCandidate normalizeItem(SearchItem item) {
        if (item == null
                || item.productName() == null
                || item.productName().isBlank()
                || item.benefit() == null
                || item.benefit().isBlank()
                || !ProductType.supports(item.productType())) {
            return null;
        }

        List<String> ingredients = normalizeIngredients(item.mainIngredients());

        if (ingredients.isEmpty()) {
            return null;
        }

        return new CosmeticSearchCandidate(
                item.productName().trim(),
                normalizeBrandName(item.brandName()),
                item.productType(),
                normalizeImageUrl(item.imageUrl()),
                item.benefit().trim(),
                ingredients
        );
    }

    private List<String> normalizeIngredients(List<String> ingredients) {
        if (ingredients == null) {
            return List.of();
        }

        return ingredients.stream()
                .filter(ingredient -> ingredient != null && !ingredient.isBlank())
                .map(String::trim)
                .distinct()
                .limit(MAX_MAIN_INGREDIENT_COUNT)
                .toList();
    }

    private String normalizeBrandName(String brandName) {
        return brandName == null || brandName.isBlank() ? null : brandName.trim();
    }

    private String normalizeImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }

        String normalized = imageUrl.trim();
        return normalized.startsWith("https://") || normalized.startsWith("http://") ? normalized : null;
    }

    private record SearchPayload(List<SearchItem> items) {
    }

    private record SearchItem(
            String productName,
            String brandName,
            String productType,
            String imageUrl,
            String benefit,
            List<String> mainIngredients
    ) {
    }
}
