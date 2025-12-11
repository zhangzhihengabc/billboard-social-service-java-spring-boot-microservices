package chat_application.example.chat_application.dto.response;

import chat_application.example.chat_application.dto.userSummaryDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class onlineUsersResponseDTO {
    private Long roomId;
    private Integer onlineCount;
    private List<userSummaryDTO> onlineUsers;
}
