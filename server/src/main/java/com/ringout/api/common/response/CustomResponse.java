package com.ringout.api.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.ringout.api.common.response.code.BaseCode;
import com.ringout.api.common.response.code.ReasonResponse;
import com.ringout.api.common.response.code.status.SuccessStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@JsonPropertyOrder({"isSuccess", "code", "message", "result"})
public class CustomResponse<T> {

  @JsonProperty("isSuccess")
  private final Boolean isSuccess;
  private final String code;
  private final String message;
  @JsonInclude(Include.NON_NULL)
  private T result;

  public static <T> CustomResponse<T> onSuccess(T result) {
    return new CustomResponse<>(true, SuccessStatus._OK.getCode(), SuccessStatus._OK.getMessage(), result);
  }

  public static <T> CustomResponse<T> of(BaseCode code, T result){
    ReasonResponse reason = code.getReason();
    return new CustomResponse<>(true, reason.code(), reason.message(), result);
  }

  public static <T> CustomResponse<T> onFailure(String code, String message, T data){
    return new CustomResponse<>(false, code, message, data);
  }
}
