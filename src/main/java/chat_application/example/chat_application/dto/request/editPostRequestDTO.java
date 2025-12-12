package chat_application.example.chat_application.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class editPostRequestDTO {

    @Size(max = 5000, message = "Content must not exceed 5000 characters")
    private String content;

    private String privacy;

    private String location;

    private embedRequestDTO embed;

    private List<Long> taggedUserIds;
}
