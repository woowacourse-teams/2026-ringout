package com.ringout.api.common.response.code.status;

import com.ringout.api.common.response.code.BaseErrorCode;
import com.ringout.api.common.response.code.ErrorReasonResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorStatus implements BaseErrorCode {

  _INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON500", "서버 에러, 관리자에게 문의 바랍니다."),
  _BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMMON400", "잘못된 요청입니다."),
  _UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON401", "인증이 필요합니다."),
  _FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON403", "금지된 요청입니다."),
  _METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON405", "지원하지 않는 HTTP 메서드입니다."),

  MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER404", "존재하지 않는 회원입니다."),
  MEMBER_ALREADY_EXISTS(HttpStatus.CONFLICT, "MEMBER409", "이미 가입한 회원입니다."),
  MEMBER_NICKNAME_INVALID(HttpStatus.BAD_REQUEST, "MEMBER400", "닉네임 형식이 올바르지 않습니다."),

  TERMS_NOT_FOUND(HttpStatus.NOT_FOUND, "TERMS404", "존재하지 않는 약관입니다."),
  TERMS_NOT_AGREED(HttpStatus.BAD_REQUEST, "TERMS400", "필수 약관에 모두 동의해야 합니다."),
  TERMS_NOT_EFFECTIVE(HttpStatus.BAD_REQUEST, "TERMS400", "현재 시행 중인 약관이 아닙니다."),
  TERMS_AGREED_AT_INVALID(HttpStatus.BAD_REQUEST, "TERMS400", "동의 시각이 올바르지 않습니다."),
  TERMS_ALREADY_AGREED(HttpStatus.CONFLICT, "TERMS409", "이미 동의한 약관입니다."),

  STAMP_ALREADY_CREATED(HttpStatus.CONFLICT, "STAMP409", "이미 기록된 날짜입니다."),

  DESTINATION_NOT_FOUND(HttpStatus.NOT_FOUND, "DESTINATION404", "존재하지 않는 목적지입니다.");

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
