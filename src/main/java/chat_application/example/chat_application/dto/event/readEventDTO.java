package chat_application.example.chat_application.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class readEventDTO {

    private Long roomId;
    private Long userId;
    private String username;

    private Long lastReadMessageId;

    private List<Long> messageIds;

    private LocalDateTime readAt;
}
