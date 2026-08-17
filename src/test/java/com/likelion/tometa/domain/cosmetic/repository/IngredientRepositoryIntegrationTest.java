package com.likelion.tometa.domain.cosmetic.repository;

import com.likelion.tometa.domain.cosmetic.entity.Ingredient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.sql.init.mode=never"
})
class IngredientRepositoryIntegrationTest {

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    @Test
    void ingredientSeed_isIdempotent() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("data.sql")
        );

        populator.execute(dataSource);
        populator.execute(dataSource);

        Integer ingredientCount = jdbcTemplate.queryForObject(
                "select count(*) from ingredients",
                Integer.class
        );
        Integer distinctNameCount = jdbcTemplate.queryForObject(
                "select count(distinct name) from ingredients",
                Integer.class
        );

        assertEquals(100, ingredientCount);
        assertEquals(100, distinctNameCount);
    }
}
