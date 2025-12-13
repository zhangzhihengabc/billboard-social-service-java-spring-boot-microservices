package chat_application.example.chat_application.dto.response;

import chat_application.example.chat_application.dto.UserSummaryDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnlineUsersResponseDTO {
    private Long roomId;
    private Integer onlineCount;
    private List<UserSummaryDTO> onlineUsers;
}
