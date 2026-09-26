package com.chris64233.railpath.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * 统一异常处理：所有错误都返回相同结构的 {@link ErrorResponse}，
 * 并归入校验 / 资源 / 容量 / 冲突四类。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private final Clock clock;

    public ApiExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletRequest request) {
        return build(ex.getType().getHttpStatus(), ex.getType().name(), ex.getCode(),
                ex.getMessage(), null, ex.getConflicts());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<ErrorResponse.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(),
                        fe.getDefaultMessage() == null ? "不合法" : fe.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION", "VALIDATION_REQUEST",
                "请求参数校验失败", fields, null);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleUnreadable(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION", "VALIDATION_MALFORMED",
                "请求体格式不合法或参数类型错误", null, null);
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ErrorResponse> handleOptimisticLock(Exception ex) {
        return build(HttpStatus.CONFLICT, "CONFLICT", "CONFLICT_CONCURRENT_MODIFICATION",
                "径路正被其他操作修改，请重试", null, null);
    }

    /** 数据库唯一/检查约束的兜底映射（正常应由业务校验提前拦截）。 */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        return build(HttpStatus.CONFLICT, "CONFLICT", "CONFLICT_DATA_INTEGRITY",
                "数据约束冲突：资源或径路可能已存在，或违反数据库约束", null, null);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String error, String code,
                                                String message, List<ErrorResponse.FieldError> fields,
                                                List<ConflictDetail> conflicts) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(clock), status.value(), error, code, message, fields,
                (conflicts == null || conflicts.isEmpty()) ? null : conflicts);
        return ResponseEntity.status(status).body(body);
    }
}
