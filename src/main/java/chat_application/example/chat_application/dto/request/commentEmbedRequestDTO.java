package chat_application.example.chat_application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class commentEmbedRequestDTO {

    @NotBlank(message = "Embed type is required")
    @Size(max = 50, message = "Embed type must not exceed 50 characters")
    private String type;

    @NotBlank(message = "Embed URL is required")
    @Size(max = 500, message = "Embed URL must not exceed 500 characters")
    private String url;

    @Size(max = 255, message = "Embed title must not exceed 255 characters")
    private String title;

    @Size(max = 500, message = "Embed description must not exceed 500 characters")
    private String description;

    @Size(max = 500, message = "Embed thumbnail URL must not exceed 500 characters")
    private String thumbnail;
}
