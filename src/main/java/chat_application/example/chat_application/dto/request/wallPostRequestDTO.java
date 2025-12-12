package chat_application.example.chat_application.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class wallPostRequestDTO {

    @Size(max = 5000, message = "Content must not exceed 5000 characters")
    private String content;

    private String privacy = "PUBLIC"; // PUBLIC, FRIENDS, PRIVATE

    private String location;

    // Embed data
    private String embedUrl;
    private String embedType;
    private String embedTitle;
    private String embedDescription;
    private String embedThumbnail;

    // Tagged users
    private List<Long> taggedUserIds;
}
