package chat_application.example.chat_application.utill;

import chat_application.example.chat_application.dto.response.wallPostResponseDTO;
import chat_application.example.chat_application.entities.Post;
import chat_application.example.chat_application.entities.User;
import chat_application.example.chat_application.entities.UserPrivacySettings;
import chat_application.example.chat_application.entities.group.Group;
import chat_application.example.chat_application.exception.ForbiddenException;
import chat_application.example.chat_application.repository.friendRepository;
import chat_application.example.chat_application.repository.userPrivacySettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class wallPostUtil {

    private final friendRepository friendRepository;
    private final userPrivacySettingsRepository userPrivacySettingsRepository;

    public boolean areFriends(Long userId1, Long userId2) {
        if (friendRepository == null) {
            return true;
        }
        return friendRepository.existsFriendship(userId1, userId2);
    }

    public boolean isFriendOfFriend(Long targetUserId, Long checkUserId) {
        if (friendRepository == null) {
            return false;
        }

        List<Long> targetFriends = friendRepository.findFriendIds(targetUserId);

        for (Long friendId : targetFriends) {
            if (friendRepository.existsFriendship(friendId, checkUserId)) {
                return true;
            }
        }

        return false;
    }

    public void verifyWallPostPrivacy(Long targetUserId, Long posterId, boolean isFriend) {
        UserPrivacySettings privacySettings = userPrivacySettingsRepository
                .findByUserId(targetUserId)
                .orElse(null);

        if (privacySettings == null) {
            if (!isFriend) {
                throw new ForbiddenException("This user only allows friends to post on their wall");
            }
            return;
        }

        String wallPostPrivacy = privacySettings.getWallPostPrivacy();

        if (wallPostPrivacy == null) {
            wallPostPrivacy = "FRIENDS";
        }

        boolean canPost = switch (wallPostPrivacy.toUpperCase()) {
            case "EVERYONE" -> true;
            case "FRIENDS" -> isFriend;
            case "FRIENDS_OF_FRIENDS" -> isFriend || isFriendOfFriend(targetUserId, posterId);
            case "NOBODY" -> false;
            default -> isFriend;
        };

        if (!canPost) {
            throw new ForbiddenException("This user does not allow you to post on their wall");
        }
    }

    public wallPostResponseDTO buildResponse(Post post, Long currentUserId) {
        wallPostResponseDTO response = new wallPostResponseDTO();
        response.setId(post.getId());
        response.setContent(post.getContent());
        response.setPostType(post.getPostType());
        response.setPrivacy(post.getPrivacy());
        response.setLocation(post.getLocation());
        response.setLikeCount(post.getLikesCount());
        response.setCommentCount(post.getCommentsCount());
        response.setShareCount(post.getSharesCount() != null ? post.getSharesCount().longValue() : 0L);
        response.setIsEdited(post.getIsEdited());
        response.setEditedAt(post.getEditedAt());
        response.setIsPinned(post.getIsPinned());
        response.setCreatedAt(post.getCreatedAt());
        response.setUpdatedAt(post.getUpdatedAt());

        response.setOwner(buildUserSummary(post.getOwner()));
        response.setIsOwnPost(currentUserId != null && post.getOwner().getId().equals(currentUserId));

        if (post.getTargetUser() != null) {
            response.setTargetUser(buildUserSummary(post.getTargetUser()));
        }

        if (post.getTargetGroup() != null) {
            response.setTargetGroup(buildGroupSummary(post.getTargetGroup()));
        }

        if (post.getEmbedUrl() != null) {
            response.setEmbed(buildEmbedInfo(post));
        }

        if (post.getOriginalPost() != null) {
            response.setOriginalPost(buildOriginalPost(post.getOriginalPost()));
        }

        return response;
    }


    private wallPostResponseDTO.userSummaryDTO buildUserSummary(User user) {
        wallPostResponseDTO.userSummaryDTO summary = new wallPostResponseDTO.userSummaryDTO();
        summary.setId(user.getId());
        summary.setName(user.getName());
        summary.setEmail(user.getEmail());
        return summary;
    }

    private wallPostResponseDTO.groupSummaryDTO buildGroupSummary(Group group) {
        wallPostResponseDTO.groupSummaryDTO summary = new wallPostResponseDTO.groupSummaryDTO();
        summary.setId(group.getId());
        summary.setName(group.getName());
        return summary;
    }

    private wallPostResponseDTO.embedInfoDTO buildEmbedInfo(Post post) {
        wallPostResponseDTO.embedInfoDTO embedInfo = new wallPostResponseDTO.embedInfoDTO();
        embedInfo.setUrl(post.getEmbedUrl());
        embedInfo.setType(post.getEmbedType());
        embedInfo.setTitle(post.getEmbedTitle());
        embedInfo.setDescription(post.getEmbedDescription());
        embedInfo.setThumbnail(post.getEmbedThumbnail());
        return embedInfo;
    }

    private wallPostResponseDTO.originalPostDTO buildOriginalPost(Post post) {
        wallPostResponseDTO.originalPostDTO original = new wallPostResponseDTO.originalPostDTO();
        original.setId(post.getId());
        original.setOwner(buildUserSummary(post.getOwner()));
        original.setContent(post.getContent());
        original.setPostType(post.getPostType());
        original.setLikeCount(post.getLikesCount());
        original.setCommentCount(post.getCommentsCount());
        original.setCreatedAt(post.getCreatedAt());
        return original;
    }


}
