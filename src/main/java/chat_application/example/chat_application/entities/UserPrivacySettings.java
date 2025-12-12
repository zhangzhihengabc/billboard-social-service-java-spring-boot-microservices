package chat_application.example.chat_application.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_privacy_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPrivacySettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /**
     * Who can post on user's wall
     * Default: FRIENDS
     */
    @Column(name = "wall_post_privacy", length = 30)
    @Builder.Default
    private String wallPostPrivacy = "FRIENDS";

    /**
     * Who can see posts on user's wall
     * Default: EVERYONE
     */
    @Column(name = "wall_view_privacy", length = 30)
    @Builder.Default
    private String wallViewPrivacy = "EVERYONE";

    /**
     * Who can comment on user's posts
     * Default: EVERYONE
     */
    @Column(name = "comment_privacy", length = 30)
    @Builder.Default
    private String commentPrivacy = "EVERYONE";

    /**
     * Who can tag user in posts
     * Default: FRIENDS
     */
    @Column(name = "tag_privacy", length = 30)
    @Builder.Default
    private String tagPrivacy = "FRIENDS";

    /**
     * Who can view user's profile
     * Default: EVERYONE
     */
    @Column(name = "profile_view_privacy", length = 30)
    @Builder.Default
    private String profileViewPrivacy = "EVERYONE";

    /**
     * Who can see user's friends list
     * Default: FRIENDS
     */
    @Column(name = "friends_list_privacy", length = 30)
    @Builder.Default
    private String friendsListPrivacy = "FRIENDS";

    /**
     * Who can see user's photos/albums
     * Default: FRIENDS
     */
    @Column(name = "photos_privacy", length = 30)
    @Builder.Default
    private String photosPrivacy = "FRIENDS";

    /**
     * Who can send messages to user
     * Default: EVERYONE
     */
    @Column(name = "message_privacy", length = 30)
    @Builder.Default
    private String messagePrivacy = "EVERYONE";

    /**
     * Who can add user to group chats
     * Default: FRIENDS
     */
    @Column(name = "group_add_privacy", length = 30)
    @Builder.Default
    private String groupAddPrivacy = "FRIENDS";

    /**
     * Who can send friend requests
     * Default: EVERYONE
     */
    @Column(name = "friend_request_privacy", length = 30)
    @Builder.Default
    private String friendRequestPrivacy = "EVERYONE";

    /**
     * Can user be found in search results
     * Default: true
     */
    @Column(name = "searchable")
    @Builder.Default
    private Boolean searchable = true;

    /**
     * Show online status to others
     * Default: true
     */
    @Column(name = "show_online_status")
    @Builder.Default
    private Boolean showOnlineStatus = true;

    /**
     * Show last seen timestamp
     * Default: true
     */
    @Column(name = "show_last_seen")
    @Builder.Default
    private Boolean showLastSeen = true;

    /**
     * Show read receipts in messages
     * Default: true
     */
    @Column(name = "show_read_receipts")
    @Builder.Default
    private Boolean showReadReceipts = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Check if a user can post on this user's wall
     */
    public boolean canPostOnWall(boolean isFriend, boolean isFriendOfFriend) {
        return checkPrivacy(wallPostPrivacy, isFriend, isFriendOfFriend);
    }

    /**
     * Check if a user can view this user's wall
     */
    public boolean canViewWall(boolean isFriend, boolean isFriendOfFriend) {
        return checkPrivacy(wallViewPrivacy, isFriend, isFriendOfFriend);
    }

    /**
     * Check if a user can comment on this user's posts
     */
    public boolean canComment(boolean isFriend, boolean isFriendOfFriend) {
        return checkPrivacy(commentPrivacy, isFriend, isFriendOfFriend);
    }

    /**
     * Check if a user can tag this user
     */
    public boolean canTag(boolean isFriend, boolean isFriendOfFriend) {
        return checkPrivacy(tagPrivacy, isFriend, isFriendOfFriend);
    }

    /**
     * Check if a user can send messages to this user
     */
    public boolean canMessage(boolean isFriend, boolean isFriendOfFriend) {
        return checkPrivacy(messagePrivacy, isFriend, isFriendOfFriend);
    }

    /**
     * Check if a user can send friend request
     */
    public boolean canSendFriendRequest(boolean isFriendOfFriend) {
        return checkPrivacy(friendRequestPrivacy, false, isFriendOfFriend);
    }

    /**
     * Generic privacy check helper
     */
    private boolean checkPrivacy(String privacySetting, boolean isFriend, boolean isFriendOfFriend) {
        if (privacySetting == null) {
            return true; // Default allow if not set
        }

        return switch (privacySetting.toUpperCase()) {
            case "EVERYONE" -> true;
            case "FRIENDS" -> isFriend;
            case "FRIENDS_OF_FRIENDS" -> isFriend || isFriendOfFriend;
            case "NOBODY" -> false;
            default -> true;
        };
    }
}
