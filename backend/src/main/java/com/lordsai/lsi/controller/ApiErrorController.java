package com.lordsai.lsi.controller;

import com.lordsai.lsi.dto.ApiResponse;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Renders errors that happen outside a controller (a filter failed, the server's request firewall
 * refused a URL, the container rejected a request) in the usual ApiResponse shape.
 *
 * Without this, such a failure was forwarded to /error, where Spring Security no longer saw the
 * login token and answered "Authentication required. Please sign in." — so a logged-in student was
 * told to sign in again (and the page threw the session away) when the real problem was different.
 * The cause is logged here; the client only ever gets a generic message.
 */
@RestController
public class ApiErrorController implements ErrorController {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorController.class);

    @RequestMapping("/error")
    public ResponseEntity<ApiResponse<Void>> error(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus status = HttpStatus.resolve(code instanceof Integer i ? i : 500);
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        Object uri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        Throwable cause = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        if (status.is5xxServerError()) {
            log.error("[ERROR] {} {} failed with {}", request.getMethod(), uri, status.value(), cause);
        } else {
            log.info("[ERROR] {} {} answered {}{}", request.getMethod(), uri, status.value(),
                    cause == null ? "" : " — " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
        }
        return ResponseEntity.status(status).body(ApiResponse.error(messageFor(status)));
    }

    private static String messageFor(HttpStatus status) {
        return switch (status) {
            case UNAUTHORIZED -> "Authentication required. Please sign in.";
            case FORBIDDEN -> "You do not have permission to perform this action.";
            case NOT_FOUND -> "The requested resource was not found.";
            case METHOD_NOT_ALLOWED -> "This action is not supported.";
            case PAYLOAD_TOO_LARGE -> "The uploaded file is too large.";
            case TOO_MANY_REQUESTS -> "Too many requests. Please wait a few minutes and try again.";
            default -> status.is4xxClientError()
                    ? "The request could not be understood. Please check the submitted values."
                    : "Something went wrong. Please try again later.";
        };
    }
}
