package com.likelion.tometa.domain.cosmetic.code;

import com.likelion.tometa.global.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum CosmeticErrorCode implements BaseErrorCode {

    MAIN_INGREDIENTS_LIMIT_EXCEEDED(
            HttpStatus.BAD_REQUEST,
            "COSMETIC_4001",
            "주요 성분은 최대 3개까지 입력할 수 있습니다."
    ),
    COSMETIC_SEARCH_KEYWORD_INVALID(
            HttpStatus.BAD_REQUEST,
            "COSMETIC_4002",
            "화장품 검색어는 2자 이상 100자 이하로 입력해주세요."
    ),
    COSMETIC_SET_ITEMS_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "COSMETIC_SET_4001",
            "세트에 포함할 화장품을 선택해주세요."
    ),
    COSMETIC_SET_DUPLICATE_ITEM(
            HttpStatus.BAD_REQUEST,
            "COSMETIC_SET_4002",
            "세트에 동일한 화장품을 중복으로 선택할 수 없습니다."
    ),
    COSMETIC_SET_MIN_ITEMS_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "COSMETIC_SET_4003",
            "화장품 세트는 최소 2개의 화장품으로 구성해야 합니다."
    ),
    COSMETIC_SET_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "COSMETIC_SET_4041",
            "화장품 세트를 찾을 수 없습니다."
    ),
    USER_COSMETIC_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "COSMETIC_4042",
            "등록된 화장품을 찾을 수 없습니다."
    ),
    COSMETIC_SEARCH_RESULT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "COSMETIC_4043",
            "검색 결과가 만료되었거나 존재하지 않습니다. 다시 검색해주세요."
    ),
    COSMETIC_SEARCH_FAILED(
            HttpStatus.BAD_GATEWAY,
            "COSMETIC_5021",
            "화장품 검색 중 외부 서비스 오류가 발생했습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
