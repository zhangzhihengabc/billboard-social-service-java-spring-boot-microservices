package chat_application.example.chat_application.dto.request.post;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SharePostRequestDTO {

    @NotNull(message = "Post ID is required")
    private Long postId;

    @Size(max = 2000, message = "Comment must not exceed 2000 characters")
    private String comment;

    private String privacy = "PUBLIC";

    // Optional: share to specific user's wall
    private Long targetUserId;

    // Optional: share to group
    private Long targetGroupId;
}
