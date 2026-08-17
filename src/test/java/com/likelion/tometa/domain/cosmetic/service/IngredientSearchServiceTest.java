package com.likelion.tometa.domain.cosmetic.service;

import com.likelion.tometa.domain.cosmetic.code.IngredientErrorCode;
import com.likelion.tometa.domain.cosmetic.entity.Ingredient;
import com.likelion.tometa.domain.cosmetic.repository.IngredientRepository;
import com.likelion.tometa.global.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngredientSearchServiceTest {

    @Mock
    private IngredientRepository ingredientRepository;

    @InjectMocks
    private IngredientSearchService ingredientSearchService;

    @Test
    void search_trimsKeywordAndReturnsIngredientNames() {
        when(ingredientRepository.findTop10ByNameStartingWithOrderByNameAsc("히알루론"))
                .thenReturn(List.of(
                        ingredient("히알루론산"),
                        ingredient("히알루론산나트륨")
                ));

        List<String> result = ingredientSearchService.search("  히알루론  ");

        assertEquals(List.of("히알루론산", "히알루론산나트륨"), result);
        verify(ingredientRepository)
                .findTop10ByNameStartingWithOrderByNameAsc("히알루론");
    }

    @Test
    void search_returnsEmptyListWhenNoIngredientMatches() {
        when(ingredientRepository.findTop10ByNameStartingWithOrderByNameAsc("없는성분"))
                .thenReturn(List.of());

        List<String> result = ingredientSearchService.search("없는성분");

        assertEquals(List.of(), result);
    }

    @Test
    void search_rejectsNullKeyword() {
        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> ingredientSearchService.search(null)
        );

        assertSame(IngredientErrorCode.SEARCH_KEYWORD_REQUIRED, exception.getErrorCode());
        verifyNoInteractions(ingredientRepository);
    }

    @Test
    void search_rejectsBlankKeyword() {
        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> ingredientSearchService.search("   ")
        );

        assertSame(IngredientErrorCode.SEARCH_KEYWORD_REQUIRED, exception.getErrorCode());
        verifyNoInteractions(ingredientRepository);
    }

    private Ingredient ingredient(String name) {
        return Ingredient.builder()
                .name(name)
                .build();
    }
}
