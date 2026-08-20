package com.likelion.tometa.domain.cosmetic.controller;

import com.likelion.tometa.domain.cosmetic.dto.response.CosmeticSearchResponseDto;
import com.likelion.tometa.domain.cosmetic.service.CosmeticSearchService;
import com.likelion.tometa.global.exception.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CosmeticSearchControllerTest {

    @Mock
    private CosmeticSearchService cosmeticSearchService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CosmeticSearchController(cosmeticSearchService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void searchCosmetics_returnsDisplayNamesWithAndWithoutBrand() throws Exception {
        when(cosmeticSearchService.search("진정", "session-token"))
                .thenReturn(new CosmeticSearchResponseDto(
                        "search-id",
                        List.of(
                                new CosmeticSearchResponseDto.Item(
                                        1,
                                        "아누아 어성초 토너",
                                        "아누아",
                                        "skin_toner",
                                        "https://example.com/toner.jpg"
                                ),
                                new CosmeticSearchResponseDto.Item(
                                        2,
                                        "진정 크림",
                                        "-",
                                        "cream",
                                        null
                                )
                        )
                ));

        mockMvc.perform(get("/api/cosmetics/search")
                        .param("keyword", "진정")
                        .cookie(new Cookie("anonymous_session", "session-token")))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "isSuccess": true,
                          "code": "COMMON_200",
                          "message": "요청에 성공했습니다.",
                          "result": {
                            "searchId": "search-id",
                            "items": [
                              {
                                "itemId": 1,
                                "productName": "아누아 어성초 토너",
                                "brandName": "아누아",
                                "productType": "skin_toner",
                                "imageUrl": "https://example.com/toner.jpg"
                              },
                              {
                                "itemId": 2,
                                "productName": "진정 크림",
                                "brandName": "-",
                                "productType": "cream",
                                "imageUrl": null
                              }
                            ]
                          }
                        }
                        """));
    }
}
