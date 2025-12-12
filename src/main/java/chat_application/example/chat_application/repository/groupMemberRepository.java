package chat_application.example.chat_application.repository;

public interface groupMemberRepository {
    boolean existsByGroupIdAndUserId(Long groupId, Long userId);


}
