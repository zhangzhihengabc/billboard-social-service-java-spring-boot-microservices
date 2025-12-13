package chat_application.example.chat_application.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReadRequestDTO {

    private Long userId;
    private String username;
    private Long lastReadMessageId;
    private List<Long> messageIds;
}
