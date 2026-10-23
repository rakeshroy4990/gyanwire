package com.gyanwire.config;

import com.gyanwire.actionplan.ActionPlanException;
import com.gyanwire.auth.AuthException;
import com.gyanwire.billing.BillingException;
import com.gyanwire.controller.dto.StandardApiResponse;
import com.gyanwire.research.ResearchException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<StandardApiResponse<Object>> auth(AuthException e) {
        return ResponseEntity.status(e.getStatus())
                .body(StandardApiResponse.error(e.getMessage(), e.getErrorCode()));
    }

    @ExceptionHandler(BillingException.class)
    public ResponseEntity<StandardApiResponse<Object>> billing(BillingException e) {
        return ResponseEntity.status(e.getStatus())
                .body(StandardApiResponse.error(e.getMessage(), e.getErrorCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<StandardApiResponse<Object>> invalid(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest()
                .body(StandardApiResponse.error("Check the form and try again.", "VALIDATION_ERROR"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<StandardApiResponse<Object>> typeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest()
                .body(StandardApiResponse.error("Send a valid id.", "VALIDATION_ERROR"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<StandardApiResponse<Object>> notFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(StandardApiResponse.error("That path was not found.", "NOT_FOUND"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<StandardApiResponse<Object>> methodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(StandardApiResponse.error("That HTTP method is not allowed.", "METHOD_NOT_ALLOWED"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<StandardApiResponse<Object>> mediaType(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(StandardApiResponse.error("Use application/json.", "UNSUPPORTED_MEDIA_TYPE"));
    }

    @ExceptionHandler(ResearchException.class)
    public ResponseEntity<StandardApiResponse<Object>> research(ResearchException e) {
        return ResponseEntity.status(e.getStatus())
                .body(StandardApiResponse.error(e.getMessage(), e.getErrorCode()));
    }

    @ExceptionHandler(PlanLimitException.class)
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> limit(PlanLimitException e) {
        StandardApiResponse<Map<String, Object>> body = StandardApiResponse.error(e.getMessage(), e.getErrorCode());
        body.setDataObject(e.getData());
        return ResponseEntity.status(402).body(body);
    }

    @ExceptionHandler(ActionPlanException.class)
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> actionPlan(ActionPlanException e) {
        StandardApiResponse<Map<String, Object>> body = StandardApiResponse.error(e.getMessage(), e.getErrorCode());
        body.setDataObject(e.getData());
        return ResponseEntity.status(e.getStatus()).body(body);
    }

    /**
     * Client left during an NDJSON stream. Nothing left to write, and the
     * content type is already application/x-ndjson so a JSON error body fails.
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void clientGone(AsyncRequestNotUsableException e) {
        // no-op
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<StandardApiResponse<Object>> generic(Exception e, HttpServletResponse response) {
        if (ClientDisconnect.isGone(e) || response.isCommitted()) {
            // No body: content type may already be application/x-ndjson.
            return ResponseEntity.status(response.getStatus() > 0 ? response.getStatus() : 500).build();
        }
        e.printStackTrace();
        return ResponseEntity.status(500)
                .body(StandardApiResponse.error(
                        "Something went wrong. Try again in a moment.",
                        "INTERNAL_ERROR"));
    }
}
