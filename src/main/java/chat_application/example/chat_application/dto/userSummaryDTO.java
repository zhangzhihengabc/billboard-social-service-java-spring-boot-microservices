package chat_application.example.chat_application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class userSummaryDTO {
    private Long id;
    private String username;
    private Boolean isOnline;
}
