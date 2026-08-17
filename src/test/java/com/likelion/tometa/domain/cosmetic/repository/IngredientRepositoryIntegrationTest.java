package com.likelion.tometa.domain.cosmetic.repository;

import com.likelion.tometa.domain.cosmetic.entity.Ingredient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false"
})
class IngredientRepositoryIntegrationTest {

    @Autowired
    private IngredientRepository ingredientRepository;

    @Test
    void prefixSearch_returnsAtMostTenIngredientsInNameOrder() {
        List<Ingredient> ingredients = IntStream.rangeClosed(1, 12)
                .mapToObj(number -> Ingredient.builder()
                        .name("테스트성분%02d".formatted(number))
                        .build())
                .toList();
        ingredientRepository.saveAllAndFlush(ingredients);

        List<String> result = ingredientRepository
                .findTop10ByNameStartingWithOrderByNameAsc("테스트성분")
                .stream()
                .map(Ingredient::getName)
                .toList();

        assertEquals(10, result.size());
        assertEquals(
                IntStream.rangeClosed(1, 10)
                        .mapToObj(number -> "테스트성분%02d".formatted(number))
                        .toList(),
                result
        );
    }

    @Test
    void prefixSearch_treatsUnderscoreAsLiteralText() {
        ingredientRepository.saveAllAndFlush(List.of(
                Ingredient.builder().name("A_성분").build(),
                Ingredient.builder().name("AB성분").build()
        ));

        List<String> result = ingredientRepository
                .findTop10ByNameStartingWithOrderByNameAsc("A_")
                .stream()
                .map(Ingredient::getName)
                .toList();

        assertEquals(List.of("A_성분"), result);
    }

}
