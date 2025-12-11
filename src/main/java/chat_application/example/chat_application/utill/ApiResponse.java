package chat_application.example.chat_application.utill;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;

    private String message;

    private T data;

    private String error;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> ApiResponse<T> success(String message) {
        return success(message, null);
    }

    public static <T> ApiResponse<T> error(String message, String errorType) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .error(errorType)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> ApiResponse<T> error(Exception e) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(e.getMessage())
                .error(e.getClass().getSimpleName())
                .timestamp(LocalDateTime.now())
                .build();
    }
}
