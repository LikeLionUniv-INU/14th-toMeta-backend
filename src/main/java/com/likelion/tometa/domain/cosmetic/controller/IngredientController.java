package com.likelion.tometa.domain.cosmetic.controller;

import com.likelion.tometa.domain.cosmetic.service.IngredientSearchService;
import com.likelion.tometa.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ingredients")
public class IngredientController {

    private final IngredientSearchService ingredientSearchService;

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<String>>> searchIngredients(
            @RequestParam(name = "keyword", required = false) String keyword
    ) {
        List<String> ingredients = ingredientSearchService.search(keyword);

        return ResponseEntity.ok(ApiResponse.success(ingredients));
    }
}
