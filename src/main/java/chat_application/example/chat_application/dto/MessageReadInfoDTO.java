package chat_application.example.chat_application.dto;

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
public class MessageReadInfoDTO {

    private Long messageId;
    private Long roomId;
    private int totalMembers;
    private int viewedCount;
    private int notViewedCount;

    private List<UserReadStatus> viewedBy;
    private List<UserReadStatus> notViewedBy;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserReadStatus {
        private Long userId;
        private String username;
        private String profilePicture;
        private Long lastReadMessageId;
        private LocalDateTime lastReadAt;
    }
}
