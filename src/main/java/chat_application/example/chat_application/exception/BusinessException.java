package chat_application.example.chat_application.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown for business logic violations (400)
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
