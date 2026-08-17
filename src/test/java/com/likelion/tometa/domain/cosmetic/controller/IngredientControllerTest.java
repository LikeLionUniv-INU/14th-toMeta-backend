package com.likelion.tometa.domain.cosmetic.controller;

import com.likelion.tometa.domain.cosmetic.code.IngredientErrorCode;
import com.likelion.tometa.domain.cosmetic.service.IngredientSearchService;
import com.likelion.tometa.global.exception.GeneralException;
import com.likelion.tometa.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class IngredientControllerTest {

    @Mock
    private IngredientSearchService ingredientSearchService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        IngredientController controller = new IngredientController(ingredientSearchService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void searchIngredients_returnsMatchedIngredients() throws Exception {
        when(ingredientSearchService.search("히알루론"))
                .thenReturn(List.of("히알루론산", "히알루론산나트륨"));

        mockMvc.perform(get("/api/ingredients/search")
                        .param("keyword", "히알루론"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "isSuccess": true,
                          "code": "COMMON_200",
                          "message": "요청에 성공했습니다.",
                          "result": ["히알루론산", "히알루론산나트륨"]
                        }
                        """));

        verify(ingredientSearchService).search("히알루론");
    }

    @Test
    void searchIngredients_returnsEmptyListWhenNoIngredientMatches() throws Exception {
        when(ingredientSearchService.search("없는성분")).thenReturn(List.of());

        mockMvc.perform(get("/api/ingredients/search")
                        .param("keyword", "없는성분"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "isSuccess": true,
                          "code": "COMMON_200",
                          "message": "요청에 성공했습니다.",
                          "result": []
                        }
                        """));
    }

    @Test
    void searchIngredients_rejectsMissingKeyword() throws Exception {
        doThrow(new GeneralException(IngredientErrorCode.SEARCH_KEYWORD_REQUIRED))
                .when(ingredientSearchService)
                .search(null);

        mockMvc.perform(get("/api/ingredients/search"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {
                          "isSuccess": false,
                          "code": "INGREDIENT_4001",
                          "message": "성분 검색어를 입력해주세요.",
                          "result": null
                        }
                        """));
    }

    @Test
    void searchIngredients_rejectsBlankKeyword() throws Exception {
        doThrow(new GeneralException(IngredientErrorCode.SEARCH_KEYWORD_REQUIRED))
                .when(ingredientSearchService)
                .search("   ");

        mockMvc.perform(get("/api/ingredients/search")
                        .param("keyword", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {
                          "isSuccess": false,
                          "code": "INGREDIENT_4001",
                          "message": "성분 검색어를 입력해주세요.",
                          "result": null
                        }
                        """));
    }
}
