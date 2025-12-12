package chat_application.example.chat_application.repository;

import chat_application.example.chat_application.entities.UserPrivacySettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface userPrivacySettingsRepository extends JpaRepository<UserPrivacySettings, Long> {

    Optional<UserPrivacySettings> findByUserId(Long userId);

    /**
     * Check if user exists with privacy settings
     */
    boolean existsByUserId(Long userId);

    /**
     * Get wall post privacy setting for a user
     */
    @Query("SELECT ups.wallPostPrivacy FROM UserPrivacySettings ups WHERE ups.user.id = :userId")
    String findWallPostPrivacyByUserId(@Param("userId") Long userId);

    /**
     * Get message privacy setting for a user
     */
    @Query("SELECT ups.messagePrivacy FROM UserPrivacySettings ups WHERE ups.user.id = :userId")
    String findMessagePrivacyByUserId(@Param("userId") Long userId);

    /**
     * Check if user allows wall posts from friends
     */
    @Query("SELECT CASE WHEN ups.wallPostPrivacy IN ('EVERYONE', 'FRIENDS', 'FRIENDS_OF_FRIENDS') THEN true ELSE false END " +
            "FROM UserPrivacySettings ups WHERE ups.user.id = :userId")
    Boolean canReceiveWallPostsFromFriends(@Param("userId") Long userId);

}
