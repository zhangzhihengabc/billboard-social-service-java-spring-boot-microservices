package chat_application.example.chat_application.dto.event;

import chat_application.example.chat_application.dto.UserSummaryDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TypingEventDTO {

    private Long roomId;
    private UserSummaryDTO user;
    private Boolean isTyping;
    private LocalDateTime timestamp;

}
