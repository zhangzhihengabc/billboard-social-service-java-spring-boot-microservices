package chat_application.example.chat_application.dto.response;

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
public class ReadMessageResponseDTO {

    private Long userId;
    private Long roomId;
    private List<Long> markedMessageIds;
    private Integer markedCount;
    private LocalDateTime readAt;
}
