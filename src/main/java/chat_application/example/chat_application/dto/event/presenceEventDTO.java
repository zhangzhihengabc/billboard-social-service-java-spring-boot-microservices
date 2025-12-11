package chat_application.example.chat_application.dto.event;

import chat_application.example.chat_application.dto.userSummaryDTO;
import chat_application.example.chat_application.entities.enums.PresenceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class presenceEventDTO {
    private String eventType;
    private Long roomId;
    private userSummaryDTO user;
    private PresenceStatus status;
    private LocalDateTime timestamp;
}
