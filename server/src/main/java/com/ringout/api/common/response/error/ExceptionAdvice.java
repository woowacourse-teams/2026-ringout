package com.ringout.api.common.response.error;

import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.common.response.code.ErrorReasonResponse;
import com.ringout.api.common.response.code.status.ErrorStatus;
import jakarta.validation.ConstraintViolationException;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice(annotations = {RestController.class})
public class ExceptionAdvice {

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<CustomResponse<Void>> handleRequestConstraintViolation(ConstraintViolationException e) {
        String errorMessage = e.getConstraintViolations().stream()
            .map(constraintViolation -> constraintViolation.getMessage())
            .findFirst()
            .orElseThrow(() -> new RuntimeException("ConstraintViolationException 추출 도중 에러 발생"));

        return fail(ErrorStatus.valueOf(errorMessage));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CustomResponse<Map<String, String>>> handleMethodArgumentNotValid(
        MethodArgumentNotValidException e
    ) {
        Map<String, String> errors = new LinkedHashMap<>();

        e.getBindingResult().getFieldErrors().stream()
            .forEach(fieldError -> {
                String fieldName = fieldError.getField();
                String errorMessage = Optional.ofNullable(fieldError.getDefaultMessage()).orElse("");
                errors.merge(fieldName, errorMessage,
                    (existingErrorMessage, newErrorMessage) -> existingErrorMessage + ", " + newErrorMessage);
            });

        return fail(ErrorStatus._BAD_REQUEST, errors);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<CustomResponse<String>> handleMissingServletRequestParameter(
        MissingServletRequestParameterException e
    ) {
        String errorPoint = String.format("%s 파라미터가 누락되었습니다.", e.getParameterName());

        return fail(ErrorStatus._BAD_REQUEST, errorPoint);
    }

    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<CustomResponse<Void>> handleTypeMismatch(TypeMismatchException e) {
        return fail(ErrorStatus._BAD_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CustomResponse<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        return fail(ErrorStatus._BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CustomResponse<String>> exception(Exception e) {
        e.printStackTrace();

        return fail(ErrorStatus._INTERNAL_SERVER_ERROR, e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<CustomResponse<String>> handleIllegalStateException(
        IllegalStateException illegalStateException
    ) {
        return fail(
            ErrorStatus.STAMP_ALREADY_CREATED,
            illegalStateException.getMessage());
    }

    @ExceptionHandler(GeneralException.class)
    public ResponseEntity<CustomResponse<Void>> onThrowException(GeneralException generalException) {
        ErrorReasonResponse errorReasonHttpStatus = generalException.getErrorReasonHttpStatus();
        return fail(errorReasonHttpStatus);
    }

    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<CustomResponse<String>> handleDateTimeParseException(
        DateTimeParseException dateTimeParseException
    ) {
        return fail(
            ErrorStatus._BAD_REQUEST,
            dateTimeParseException.getMessage()
        );
    }

    private ResponseEntity<CustomResponse<Void>> fail(ErrorReasonResponse reason) {
        return ResponseEntity.status(reason.httpStatus())
            .body(CustomResponse.onFailure(reason.code(), reason.message(), null));
    }

    private ResponseEntity<CustomResponse<Void>> fail(ErrorStatus errorStatus) {
        return ResponseEntity.status(errorStatus.getHttpStatus())
            .body(CustomResponse.onFailure(errorStatus.getCode(), errorStatus.getMessage(), null));
    }

    private ResponseEntity<CustomResponse<String>> fail(ErrorStatus errorStatus, String errorPoint) {
        return ResponseEntity.status(errorStatus.getHttpStatus())
            .body(CustomResponse.onFailure(errorStatus.getCode(), errorStatus.getMessage(), errorPoint));
    }

    private ResponseEntity<CustomResponse<Map<String, String>>> fail(
        ErrorStatus errorStatus,
        Map<String, String> errorArgs
    ) {
        return ResponseEntity.status(errorStatus.getHttpStatus())
            .body(CustomResponse.onFailure(errorStatus.getCode(), errorStatus.getMessage(), errorArgs));
    }
}
