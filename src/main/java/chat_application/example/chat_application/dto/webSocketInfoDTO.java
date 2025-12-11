package chat_application.example.chat_application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class webSocketInfoDTO {

    private String rawEndpoint;
    private List<String> subscribeTopics;
    private String sendDestination;

}
