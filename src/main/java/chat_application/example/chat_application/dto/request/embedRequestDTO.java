package chat_application.example.chat_application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class embedRequestDTO {

    @NotBlank(message = "Embed URL is required")
    @Size(max = 500, message = "URL must not exceed 500 characters")
    private String url;

    @Size(max = 50, message = "Type must not exceed 50 characters")
    private String type; // link, video, image, audio

    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @Size(max = 500, message = "Thumbnail URL must not exceed 500 characters")
    private String thumbnail;
}
