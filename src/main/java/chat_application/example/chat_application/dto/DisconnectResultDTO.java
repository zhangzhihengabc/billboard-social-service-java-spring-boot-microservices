package chat_application.example.chat_application.dto;

import chat_application.example.chat_application.entities.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisconnectResultDTO {
    private List<Long> roomIds;
    private List<Long> sessionIdsToDelete;
    private User user;
}
