package com.likelion.tometa.domain.cosmetic.code;

import com.likelion.tometa.global.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum IngredientErrorCode implements BaseErrorCode {

    SEARCH_KEYWORD_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "INGREDIENT_4001",
            "성분 검색어를 입력해주세요."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
