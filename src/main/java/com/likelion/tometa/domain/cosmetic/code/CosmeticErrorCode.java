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
            "주요 성분은 최대 5개까지 입력할 수 있습니다."
    ),
    USER_COSMETIC_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "COSMETIC_4042",
            "등록된 화장품을 찾을 수 없습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
