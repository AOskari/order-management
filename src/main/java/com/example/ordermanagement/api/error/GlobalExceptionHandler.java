package com.example.ordermanagement.api.error;

import com.example.ordermanagement.domain.exception.InvalidStatusTransitionException;
import com.example.ordermanagement.domain.exception.OrderNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ErrorResponse handleValidation(
        MethodArgumentNotValidException exception,
        HttpServletRequest request
    ) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : exception
            .getBindingResult()
            .getFieldErrors()) {
            errors.putIfAbsent(
                fieldError.getField(),
                fieldError.getDefaultMessage()
            );
        }
        return validationError(exception, errors, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ErrorResponse handleMethodValidation(
        HandlerMethodValidationException exception,
        HttpServletRequest request
    ) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ParameterValidationResult result : exception.getParameterValidationResults()) {
            String parameter = result.getMethodParameter().getParameterName();
            String key = parameter != null ? parameter : "parameter";
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                errors.putIfAbsent(key, error.getDefaultMessage());
            }
        }
        return validationError(exception, errors, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ErrorResponse handleConstraintViolation(
        ConstraintViolationException exception,
        HttpServletRequest request
    ) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            String path = violation.getPropertyPath().toString();
            String key = path.substring(path.lastIndexOf('.') + 1);
            errors.putIfAbsent(key, violation.getMessage());
        }
        return validationError(exception, errors, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ErrorResponse handleUnreadableBody(
        HttpMessageNotReadableException exception,
        HttpServletRequest request
    ) {
        return error(
            exception,
            HttpStatus.BAD_REQUEST,
            "Malformed request",
            "The request body is missing or malformed",
            request
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ErrorResponse handleTypeMismatch(
        MethodArgumentTypeMismatchException exception,
        HttpServletRequest request
    ) {
        return error(
            exception,
            HttpStatus.BAD_REQUEST,
            "Invalid parameter",
            "Invalid value for parameter '" + exception.getName() + "'",
            request
        );
    }

    @ExceptionHandler(OrderNotFoundException.class)
    ErrorResponse handleNotFound(
        OrderNotFoundException exception,
        HttpServletRequest request
    ) {
        return error(
            exception,
            HttpStatus.NOT_FOUND,
            "Order not found",
            exception.getMessage(),
            request
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ErrorResponse handleNoResource(
        NoResourceFoundException exception,
        HttpServletRequest request
    ) {
        return error(
            exception,
            HttpStatus.NOT_FOUND,
            "Not found",
            "The requested resource was not found",
            request
        );
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    ErrorResponse handleInvalidTransition(
        InvalidStatusTransitionException exception,
        HttpServletRequest request
    ) {
        return error(
            exception,
            HttpStatus.CONFLICT,
            "Invalid status transition",
            exception.getMessage(),
            request
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ErrorResponse handleDataIntegrity(
        DataIntegrityViolationException exception,
        HttpServletRequest request
    ) {
        log.warn(
            "Data integrity violation while processing {}",
            request.getRequestURI(),
            exception
        );
        return error(
            exception,
            HttpStatus.CONFLICT,
            "Request conflict",
            "The request conflicts with the current state",
            request
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    ErrorResponse handleAccessDenied(
        AccessDeniedException exception,
        HttpServletRequest request
    ) {
        return error(
            exception,
            HttpStatus.FORBIDDEN,
            "Forbidden",
            "Access is denied",
            request
        );
    }

    @ExceptionHandler(InsufficientAuthenticationException.class)
    ErrorResponse handleInsufficientAuthentication(
        InsufficientAuthenticationException exception,
        HttpServletRequest request
    ) {
        return error(
            exception,
            HttpStatus.UNAUTHORIZED,
            "Unauthorized",
            "Authentication is required",
            request
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ErrorResponse handleMethodNotSupported(
        HttpRequestMethodNotSupportedException exception,
        HttpServletRequest request
    ) {
        return error(
            exception,
            HttpStatus.METHOD_NOT_ALLOWED,
            "Method not allowed",
            "The request method is not supported for this resource",
            request
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ErrorResponse handleMediaTypeNotSupported(
        HttpMediaTypeNotSupportedException exception,
        HttpServletRequest request
    ) {
        return error(
            exception,
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "Unsupported media type",
            "The request content type is not supported",
            request
        );
    }

    @ExceptionHandler(Exception.class)
    ErrorResponse handleUnexpected(
        Exception exception,
        HttpServletRequest request
    ) {
        log.error(
            "Unexpected error while processing {}",
            request.getRequestURI(),
            exception
        );
        return error(
            exception,
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Internal server error",
            "An unexpected error occurred",
            request
        );
    }

    private ErrorResponse validationError(
        Throwable throwable,
        Map<String, String> errors,
        HttpServletRequest request
    ) {
        return ErrorResponse.builder(
            throwable,
            HttpStatus.BAD_REQUEST,
            "Request validation failed"
        )
            .title("Validation failed")
            .instance(URI.create(request.getRequestURI()))
            .property("errors", errors)
            .build();
    }

    private ErrorResponse error(
        Throwable throwable,
        HttpStatus status,
        String title,
        String detail,
        HttpServletRequest request
    ) {
        return ErrorResponse.builder(throwable, status, detail)
            .title(title)
            .instance(URI.create(request.getRequestURI()))
            .build();
    }
}
