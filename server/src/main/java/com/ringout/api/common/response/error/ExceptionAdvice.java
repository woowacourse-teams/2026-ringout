package com.ringout.api.common.response.error;

import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.common.response.code.ErrorReasonResponse;
import com.ringout.api.common.response.code.status.ErrorStatus;
import com.ringout.api.config.security.CustomUserDetails;
import com.ringout.api.file.status.FileErrorStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@Slf4j
@RestControllerAdvice(annotations = {RestController.class})
public class ExceptionAdvice {

    private static final String UNEXPECTED_EXCEPTION_EVENT = "unexpected_exception_occurred";
    private static final String INTERNAL_SERVER_ERROR_REASON = "INTERNAL_SERVER_ERROR";
    private static final String CLIENT_REQUEST_FAILURE_EVENT = "client_request_failed";
    private static final String MASKED_VALUE = "[MASKED]";
    private static final Set<String> SENSITIVE_PARAMETER_NAMES = Set.of(
        "authorization", "password", "token", "access_token", "refresh_token", "secret"
    );

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<CustomResponse<Void>> handleRequestConstraintViolation(
        ConstraintViolationException e,
        HttpServletRequest request
    ) {
        String errorMessage = e.getConstraintViolations().stream()
            .map(constraintViolation -> constraintViolation.getMessage())
            .findFirst()
            .orElseThrow(() -> new RuntimeException("ConstraintViolationException 추출 도중 에러 발생"));

        ErrorStatus errorStatus = ErrorStatus.valueOf(errorMessage);
        logClientRequestFailure(errorStatus, request);
        return fail(errorStatus);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CustomResponse<Map<String, String>>> handleMethodArgumentNotValid(
        MethodArgumentNotValidException e,
        HttpServletRequest request
    ) {
        Map<String, String> errors = new LinkedHashMap<>();

        e.getBindingResult().getFieldErrors().stream()
            .forEach(fieldError -> {
                String fieldName = fieldError.getField();
                String errorMessage = Optional.ofNullable(fieldError.getDefaultMessage()).orElse("");
                errors.merge(fieldName, errorMessage,
                    (existingErrorMessage, newErrorMessage) -> existingErrorMessage + ", " + newErrorMessage);
            });

        logClientRequestFailure(ErrorStatus.BAD_REQUEST, request);
        return fail(ErrorStatus.BAD_REQUEST, errors);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<CustomResponse<String>> handleMissingServletRequestParameter(
        MissingServletRequestParameterException e,
        HttpServletRequest request
    ) {
        String errorPoint = String.format("%s 파라미터가 누락되었습니다.", e.getParameterName());

        logClientRequestFailure(ErrorStatus.BAD_REQUEST, request);
        return fail(ErrorStatus.BAD_REQUEST, errorPoint);
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MaxUploadSizeExceededException.class})
    public ResponseEntity<CustomResponse<Void>> handleInvalidMultipart(
        Exception exception,
        HttpServletRequest request
    ) {
        logClientRequestFailure(FileErrorStatus.IMAGE_FILE_INVALID.getReasonHttpStatus(), request);
        return fail(FileErrorStatus.IMAGE_FILE_INVALID.getReasonHttpStatus());
    }

    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<CustomResponse<Void>> handleTypeMismatch(TypeMismatchException e, HttpServletRequest request) {
        logClientRequestFailure(ErrorStatus.BAD_REQUEST, request);
        return fail(ErrorStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CustomResponse<Void>> handleHttpMessageNotReadable(
        HttpMessageNotReadableException e,
        HttpServletRequest request
    ) {
        logClientRequestFailure(ErrorStatus.BAD_REQUEST, request);
        return fail(ErrorStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CustomResponse<String>> exception(Exception e, HttpServletRequest request) {
        log.atError()
            .addKeyValue("event", UNEXPECTED_EXCEPTION_EVENT)
            .addKeyValue("reason", INTERNAL_SERVER_ERROR_REASON)
            .addKeyValue("exception", e.getClass().getName())
            .addKeyValue("method", request.getMethod())
            .addKeyValue("path", request.getRequestURI())
            .addKeyValue("userId", resolveAuthenticatedUserId())
            .addKeyValue("requestParameters", resolveRequestParameters(request))
            .setCause(e)
            .log("예상하지 못한 서버 예외 발생");

        return fail(ErrorStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    @ExceptionHandler(GeneralException.class)
    public ResponseEntity<CustomResponse<Void>> onThrowException(
        GeneralException generalException,
        HttpServletRequest request
    ) {
        ErrorReasonResponse errorReasonHttpStatus = generalException.getErrorReasonHttpStatus();
        logClientRequestFailure(errorReasonHttpStatus, request);
        return fail(errorReasonHttpStatus);
    }

    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<CustomResponse<String>> handleDateTimeParseException(
        DateTimeParseException dateTimeParseException,
        HttpServletRequest request
    ) {
        logClientRequestFailure(ErrorStatus.BAD_REQUEST, request);
        return fail(
            ErrorStatus.BAD_REQUEST,
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

    private void logClientRequestFailure(ErrorStatus errorStatus, HttpServletRequest request) {
        logClientRequestFailure(
            errorStatus.getCode(),
            errorStatus.getHttpStatus().value(),
            request
        );
    }

    private void logClientRequestFailure(ErrorReasonResponse errorReason, HttpServletRequest request) {
        logClientRequestFailure(errorReason.code(), errorReason.httpStatus().value(), request);
    }

    private void logClientRequestFailure(String reason, int httpStatus, HttpServletRequest request) {
        log.atWarn()
            .addKeyValue("event", CLIENT_REQUEST_FAILURE_EVENT)
            .addKeyValue("reason", reason)
            .addKeyValue("httpStatus", httpStatus)
            .addKeyValue("method", request.getMethod())
            .addKeyValue("path", request.getRequestURI())
            .addKeyValue("userId", resolveAuthenticatedUserId())
            .addKeyValue("requestParameters", resolveRequestParameters(request))
            .log("클라이언트 요청 처리 실패");
    }

    private Map<String, Object> resolveRequestParameters(HttpServletRequest request) {
        try {
            Map<String, Object> requestParameters = new LinkedHashMap<>();
            request.getParameterMap().forEach((name, values) -> requestParameters.put(name,
                isSensitiveParameter(name) ? MASKED_VALUE : List.of(values)));

            if (request instanceof MultipartHttpServletRequest multipartRequest) {
                multipartRequest.getMultiFileMap().forEach((name, files) -> requestParameters.put(name,
                    files.stream().map(this::fileMetadata).toList()));
            }

            return requestParameters;
        } catch (RuntimeException exception) {
            return Map.of("unavailable", exception.getClass().getSimpleName());
        }
    }

    private Map<String, Object> fileMetadata(MultipartFile file) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("originalFilename", file.getOriginalFilename());
        metadata.put("size", file.getSize());
        metadata.put("contentType", file.getContentType());
        return metadata;
    }

    private boolean isSensitiveParameter(String name) {
        return SENSITIVE_PARAMETER_NAMES.contains(name.toLowerCase());
    }

    private Long resolveAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            return null;
        }

        return userDetails.getUserId();
    }
}
