package chat_application.example.chat_application.dto.response;


import chat_application.example.chat_application.dto.webSocketInfoDTO;
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
public class chatSessionResponseDTO {

    private Long id;
    private Long roomId;
    private Long userId;
    private String sessionToken;
    private Boolean isActive;
    private PresenceStatus presenceStatus;
    private LocalDateTime lastActivityAt;
    private LocalDateTime connectedAt;
    private webSocketInfoDTO webSocketInfo;
}
