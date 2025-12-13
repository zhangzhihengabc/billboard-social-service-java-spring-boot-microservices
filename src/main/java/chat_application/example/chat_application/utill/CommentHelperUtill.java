package chat_application.example.chat_application.utill;

import chat_application.example.chat_application.dto.response.CommentResponseDTO;
import chat_application.example.chat_application.entities.User;
import chat_application.example.chat_application.entities.commentEntities.Comment;
import chat_application.example.chat_application.exception.ForbiddenException;
import chat_application.example.chat_application.repository.CommentLikeRepository;
import chat_application.example.chat_application.repository.GroupMessageMemberRepository;
import chat_application.example.chat_application.repository.GroupMessageRoomRepository;
import chat_application.example.chat_application.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentHelperUtill {

    private final CommentLikeRepository commentLikeRepository;
    private final PostRepository postRepository;
    private final GroupMessageMemberRepository groupMessageMemberRepository;
    private final GroupMessageRoomRepository groupMessageRoomRepository;

    public void verifyCanPinOrHighlight(Comment comment, Long userId) {
        String entityType = comment.getEntityType();
        Long entityId = comment.getEntityId();

        if ("room".equalsIgnoreCase(entityType)) {
            if (!groupMessageMemberRepository.isRoomAdmin(entityId, userId)) {
                throw new ForbiddenException("Only room admins can pin/highlight comments");
            }
            return;
        }

        if ("chatroom".equalsIgnoreCase(entityType) || "groupmessageroom".equalsIgnoreCase(entityType)) {
            Long roomOwnerId = groupMessageRoomRepository.findOwnerIdById(entityId);
            if (roomOwnerId == null) {
                throw new ForbiddenException("Room not found");
            }
            if (!roomOwnerId.equals(userId) && !groupMessageMemberRepository.isRoomAdmin(entityId, userId)) {
                throw new ForbiddenException("Only room owner or admins can pin/highlight comments");
            }
            return;
        }

        Long entityOwnerId = getEntityOwnerId(entityType, entityId);

        if (entityOwnerId == null) {
            log.warn("Entity ownership verification skipped for {} {}", entityType, entityId);
            throw new ForbiddenException("Unable to verify entity ownership");
        }

        if (!entityOwnerId.equals(userId)) {
            throw new ForbiddenException("Only the content owner can pin/highlight comments");
        }
    }

    private Long getEntityOwnerId(String entityType, Long entityId) {
        return switch (entityType.toLowerCase()) {
            case "post" -> postRepository.findOwnerIdById(entityId);
            case "room", "chatroom", "groupmessageroom" -> groupMessageRoomRepository.findOwnerIdById(entityId);
            default -> {
                log.warn("Unknown entity type for ownership check: {}", entityType);
                yield null;
            }
        };
    }


    public String resolveMentionedUserIds(String mentionedUserIds, List<Long> mentionedUserIdList) {
        if (mentionedUserIds != null && !mentionedUserIds.isEmpty()) {
            return mentionedUserIds;
        }
        if (mentionedUserIdList != null && !mentionedUserIdList.isEmpty()) {
            return mentionedUserIdList.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
        }
        return null;
    }

    private List<Long> parseMentionedUserIds(String mentionedUserIds) {
        if (mentionedUserIds == null || mentionedUserIds.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(mentionedUserIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .toList();
    }

    public CommentResponseDTO buildCommentResponse(Comment comment, Long currentUserId) {
        boolean isLiked = currentUserId != null &&
                commentLikeRepository.existsByCommentIdAndUserId(comment.getId(), currentUserId);
        boolean isOwn = currentUserId != null &&
                comment.getOwner().getId().equals(currentUserId);

        var builder = CommentResponseDTO.builder()
                .id(comment.getId())
                .content(comment.getContent())
                .entityType(comment.getEntityType())
                .entityId(comment.getEntityId())
                .objectType(comment.getObjectType())
                .objectId(comment.getObjectId())
                .parentId(comment.getParent() != null ? comment.getParent().getId() : null)
                .timestampSeconds(comment.getTimestampSeconds())
                .mentionedUserIds(comment.getMentionedUserIds())
                .mentionedUserIdList(parseMentionedUserIds(comment.getMentionedUserIds()))
                .likeCount(comment.getLikeCount())
                .replyCount(comment.getReplyCount())
                .isPinned(comment.getIsPinned())
                .isHighlighted(comment.getIsHighlighted())
                .isEdited(comment.getIsEdited())
                .editedAt(comment.getEditedAt())
                .isDeleted(comment.getIsDeleted())
                .deletedAt(comment.getDeletedAt())
                .isLikedByCurrentUser(isLiked)
                .isOwnComment(isOwn)
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt());

        User owner = comment.getOwner();
        var ownerDTO = CommentResponseDTO.OwnerDTO.builder()
                .id(owner.getId())
                .name(owner.getName())
                .email(owner.getEmail())
                .phoneNumber(owner.getPhoneNumber())
                .gender(owner.getGender())
                .build();

        builder.owner(ownerDTO);

        if (comment.getEmbedUrl() != null) {
            var embedDTO = CommentResponseDTO.EmbedDTO.builder()
                    .type(comment.getEmbedType())
                    .url(comment.getEmbedUrl())
                    .title(comment.getEmbedTitle())
                    .description(comment.getEmbedDescription())
                    .thumbnail(comment.getEmbedThumbnail())
                    .build();
            builder.embed(embedDTO);
        }

        if (comment.getAttachmentUrl() != null) {
            var attachmentDTO = CommentResponseDTO.AttachmentDTO.builder()
                    .url(comment.getAttachmentUrl())
                    .type(comment.getAttachmentType())
                    .build();
            builder.attachment(attachmentDTO);
        }

        return builder.build();
    }

}
