package com.likelion.tometa.domain.cosmetic.service;

import com.likelion.tometa.domain.cosmetic.code.IngredientErrorCode;
import com.likelion.tometa.domain.cosmetic.entity.Ingredient;
import com.likelion.tometa.domain.cosmetic.repository.IngredientRepository;
import com.likelion.tometa.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class IngredientSearchService {

    private final IngredientRepository ingredientRepository;

    @Transactional(readOnly = true)
    public List<String> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new GeneralException(IngredientErrorCode.SEARCH_KEYWORD_REQUIRED);
        }

        String normalizedKeyword = keyword.trim();

        return ingredientRepository
                .findTop10ByNameStartingWithOrderByNameAsc(normalizedKeyword)
                .stream()
                .map(Ingredient::getName)
                .toList();
    }
}
