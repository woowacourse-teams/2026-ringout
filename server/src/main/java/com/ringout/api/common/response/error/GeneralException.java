package com.ringout.api.common.response.error;

import com.ringout.api.common.response.code.BaseErrorCode;
import com.ringout.api.common.response.code.ErrorReasonResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GeneralException extends RuntimeException {

  private BaseErrorCode code;

  public ErrorReasonResponse getErrorReason() {
    return this.code.getReason();
  }

  public ErrorReasonResponse getErrorReasonHttpStatus(){
    return this.code.getReasonHttpStatus();
  }
}
