package chat_application.example.chat_application.dto.request.post;


import chat_application.example.chat_application.dto.request.EmbedRequestDTO;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserWallPostRequestDTO {
    @NotNull(message = "Target user ID is required")
    private Long targetUserId;

    @Size(max = 5000, message = "Content must not exceed 5000 characters")
    private String content;

    private String privacy = "FRIENDS";

    private String location;

    private EmbedRequestDTO embed;

    private List<Long> taggedUserIds;
}
