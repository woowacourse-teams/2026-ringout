package com.ringout.api.file.status;

import com.ringout.api.common.response.code.BaseErrorCode;
import com.ringout.api.common.response.code.ErrorReasonResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum FileErrorStatus implements BaseErrorCode {

    IMAGE_FILE_INVALID(HttpStatus.BAD_REQUEST, "FILE400", "이미지 파일이 올바르지 않습니다."),
    IMAGE_DIRECTORY_INVALID(HttpStatus.BAD_REQUEST, "FILE400_1", "이미지 저장 경로가 올바르지 않습니다."),
    IMAGE_FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "FILE404", "이미지 파일을 찾을 수 없습니다."),
    IMAGE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "FILE500", "이미지 업로드에 실패했습니다."),
    IMAGE_URL_CREATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "FILE500_1", "이미지 조회 URL 생성에 실패했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    @Override
    public ErrorReasonResponse getReason() {
        return new ErrorReasonResponse(null, false, code, message);
    }

    @Override
    public ErrorReasonResponse getReasonHttpStatus() {
        return new ErrorReasonResponse(httpStatus, false, code, message);
    }
}
