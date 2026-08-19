package com.likelion.tometa.domain.record.code;

import com.likelion.tometa.global.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum RecordImageErrorCode implements BaseErrorCode {

    UNSUPPORTED_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "IMAGE_4001", "지원하지 않는 이미지 형식입니다."),
    IMAGE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "IMAGE_4002", "이미지 파일 크기는 0바이트보다 크고 허용된 최대 크기 이하여야 합니다."),
    INVALID_IMAGE_COUNT(HttpStatus.BAD_REQUEST, "IMAGE_4003", "이미지는 1장 이상, 최대 5장까지 업로드할 수 있습니다."),
    INVALID_IMAGE_KEY(HttpStatus.BAD_REQUEST, "IMAGE_4004", "유효하지 않은 이미지 키가 포함되어 있습니다."),
    IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "IMAGE_4041", "업로드된 이미지를 찾을 수 없습니다."),
    IMAGE_ALREADY_USED(HttpStatus.CONFLICT, "IMAGE_4091", "이미 다른 기록에서 사용 중인 이미지입니다."),
    PRESIGNED_URL_ISSUE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "IMAGE_5001", "이미지 업로드 URL 발급에 실패했습니다."),
    IMAGE_STORAGE_ACCESS_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "IMAGE_5002", "이미지 저장소 확인에 실패했습니다."),
    PRESIGNED_DOWNLOAD_URL_ISSUE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "IMAGE_5003",
            "이미지 조회 URL 발급에 실패했습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
