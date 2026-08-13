package com.ringout.api.common.response.error;

import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.common.response.code.ErrorReasonResponse;
import com.ringout.api.common.response.code.status.ErrorStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice(annotations = {RestController.class})
public class ExceptionAdvice extends ResponseEntityExceptionHandler {

  @ExceptionHandler
  public ResponseEntity<Object> validation(ConstraintViolationException e, WebRequest request) {
    String errorMessage = e.getConstraintViolations().stream()
        .map(constraintViolation -> constraintViolation.getMessage())
        .findFirst()
        .orElseThrow(() -> new RuntimeException("ConstraintViolationException 추출 도중 에러 발생"));

    return handleExceptionInternalConstraint(e, ErrorStatus.valueOf(errorMessage), HttpHeaders.EMPTY,request);
  }

  @Override
  public ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

    Map<String, String> errors = new LinkedHashMap<>();

    e.getBindingResult().getFieldErrors().stream()
        .forEach(fieldError -> {
          String fieldName = fieldError.getField();
          String errorMessage = Optional.ofNullable(fieldError.getDefaultMessage()).orElse("");
          errors.merge(fieldName, errorMessage, (existingErrorMessage, newErrorMessage) -> existingErrorMessage + ", " + newErrorMessage);
        });

    return handleExceptionInternalArgs(e,HttpHeaders.EMPTY,ErrorStatus.valueOf("_BAD_REQUEST"),request,errors);
  }

  @Override
  public ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException e,
      HttpHeaders headers, HttpStatusCode status, WebRequest request) {

    String errorPoint = String.format("%s 파라미터가 누락되었습니다.", e.getParameterName());

    return handleExceptionInternalFalse(e, ErrorStatus._BAD_REQUEST, HttpHeaders.EMPTY,
        ErrorStatus._BAD_REQUEST.getHttpStatus(), request, errorPoint);
  }

  @ExceptionHandler
  public ResponseEntity<Object> exception(Exception e, WebRequest request) {
    e.printStackTrace();

    return handleExceptionInternalFalse(e, ErrorStatus._INTERNAL_SERVER_ERROR, HttpHeaders.EMPTY, ErrorStatus._INTERNAL_SERVER_ERROR.getHttpStatus(),request, e.getMessage());
  }

  @ExceptionHandler(value = IllegalStateException.class)
  public ResponseEntity handleIllegalStateException(IllegalStateException illegalStateException,
      HttpServletRequest request) {
    WebRequest webRequest = new ServletWebRequest(request);
    return handleExceptionInternalFalse(
        illegalStateException,
        ErrorStatus.STAMP_ALREADY_CREATED,
        HttpHeaders.EMPTY,
        ErrorStatus.STAMP_ALREADY_CREATED.getHttpStatus(),
        webRequest,
        illegalStateException.getMessage());
  }

  @ExceptionHandler(value = GeneralException.class)
  public ResponseEntity onThrowException(GeneralException generalException, HttpServletRequest request) {
    ErrorReasonResponse errorReasonHttpStatus = generalException.getErrorReasonHttpStatus();
    return handleExceptionInternal(generalException, errorReasonHttpStatus, null,request);
  }

  @ExceptionHandler(value = DateTimeParseException.class)
  public ResponseEntity handleDateTimeParseException(DateTimeParseException dateTimeParseException,
      HttpServletRequest request) {
    WebRequest webRequest = new ServletWebRequest(request);
    return handleExceptionInternalFalse(
        dateTimeParseException,
        ErrorStatus._BAD_REQUEST,
        HttpHeaders.EMPTY,
        ErrorStatus._BAD_REQUEST.getHttpStatus(),
        webRequest,
        dateTimeParseException.getMessage()
    );
  }

  private ResponseEntity<Object> handleExceptionInternal(Exception e, ErrorReasonResponse reason,
      HttpHeaders headers, HttpServletRequest request) {

    CustomResponse<Object> body = CustomResponse.onFailure(reason.code(),reason.message(),null);

    WebRequest webRequest = new ServletWebRequest(request);
    return super.handleExceptionInternal(
        e,
        body,
        headers,
        reason.httpStatus(),
        webRequest
    );
  }

  private ResponseEntity<Object> handleExceptionInternalFalse(Exception e, ErrorStatus errorCommonStatus,
      HttpHeaders headers, HttpStatus status, WebRequest request, String errorPoint) {
    CustomResponse<Object> body = CustomResponse.onFailure(errorCommonStatus.getCode(),errorCommonStatus.getMessage(),errorPoint);
    return super.handleExceptionInternal(
        e,
        body,
        headers,
        status,
        request
    );
  }

  private ResponseEntity<Object> handleExceptionInternalArgs(Exception e, HttpHeaders headers, ErrorStatus errorCommonStatus,
      WebRequest request, Map<String, String> errorArgs) {
    CustomResponse<Object> body = CustomResponse.onFailure(errorCommonStatus.getCode(),errorCommonStatus.getMessage(),errorArgs);
    return super.handleExceptionInternal(
        e,
        body,
        headers,
        errorCommonStatus.getHttpStatus(),
        request
    );
  }

  private ResponseEntity<Object> handleExceptionInternalConstraint(Exception e, ErrorStatus errorCommonStatus,
      HttpHeaders headers, WebRequest request) {
    CustomResponse<Object> body = CustomResponse.onFailure(errorCommonStatus.getCode(), errorCommonStatus.getMessage(), null);
    return super.handleExceptionInternal(
        e,
        body,
        headers,
        errorCommonStatus.getHttpStatus(),
        request
    );
  }
}
