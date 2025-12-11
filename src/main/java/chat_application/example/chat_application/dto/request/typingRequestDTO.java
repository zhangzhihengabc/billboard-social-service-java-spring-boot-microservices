package chat_application.example.chat_application.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class typingRequestDTO {
    private Long userId;
    private String username;
    private Boolean isTyping;
}
