package chat_application.example.chat_application.service;

import chat_application.example.chat_application.dto.event.RealTimeCommentsStatusDTO;
import chat_application.example.chat_application.dto.request.RealTimeStatusRequestDTO;
import chat_application.example.chat_application.dto.request.comment.*;
import chat_application.example.chat_application.dto.response.CommentResponseDTO;
import chat_application.example.chat_application.entities.User;
import chat_application.example.chat_application.entities.commentEntities.Comment;
import chat_application.example.chat_application.exception.BadRequestException;
import chat_application.example.chat_application.exception.ResourceNotFoundException;
import chat_application.example.chat_application.repository.CommentRepository;
import chat_application.example.chat_application.repository.UserRepository;
import chat_application.example.chat_application.utill.CommentHelperUtill;
import chat_application.example.chat_application.utill.RedisUtill;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentService {

    private final CommentHelperUtill commentHelperUtill;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;
    private final RedisUtill redisUtill;

    @Value("${chat.session.ttl-seconds}")
    private Long REALTIME_TTL_MINUTES;

    public CommentResponseDTO addComment(Long postId, Long userId, @Valid CreateCommentRequestDTO request) {

        return addEntityComment(userId, EntityCommentRequestDTO.builder()
                .entityType("post")
                .entityId(postId)
                .content(request.getContent())
                .parentId(request.getParentId())
                .timestampSeconds(request.getTimestampSeconds())
                .mentionedUserIds(request.getMentionedUserIds())
                .mentionedUserIdList(request.getMentionedUserIdList())
                .embed(request.getEmbed())
                .attachment(request.getAttachment())
                .build());
    }

    @Transactional
    public CommentResponseDTO addEntityComment(Long userId, EntityCommentRequestDTO request) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        Comment.CommentBuilder builder = Comment.builder()
                .owner(owner)
                .entityType(request.getEntityType())
                .entityId(request.getEntityId())
                .content(request.getContent())
                .timestampSeconds(request.getTimestampSeconds());

        String mentionedIds = commentHelperUtill.resolveMentionedUserIds(request.getMentionedUserIds(), request.getMentionedUserIdList());
        if (mentionedIds != null) {
            builder.mentionedUserIds(mentionedIds);
        }

        if (request.getParentId() != null) {
            Comment parent = commentRepository.findById(request.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent comment", request.getParentId()));
            builder.parent(parent);
            parent.incrementReplies();
            commentRepository.save(parent);
        }

        if (request.getEmbed() != null) {
            builder.embedType(request.getEmbed().getType())
                    .embedUrl(request.getEmbed().getUrl())
                    .embedTitle(request.getEmbed().getTitle())
                    .embedDescription(request.getEmbed().getDescription())
                    .embedThumbnail(request.getEmbed().getThumbnail());
        }

        if (request.getAttachment() != null) {
            builder.attachmentUrl(request.getAttachment().getUrl())
                    .attachmentType(request.getAttachment().getType());
        }

        Comment comment = commentRepository.save(builder.build());
        log.info("Comment {} added by user {} on {} {}", comment.getId(), userId, request.getEntityType(), request.getEntityId());

        return commentHelperUtill.buildCommentResponse(comment, userId);
    }


    @Transactional
    public CommentResponseDTO addObjectComment(Long userId, ObjectCommentRequestDTO request) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        Comment.CommentBuilder builder = Comment.builder()
                .owner(owner)
                .entityType(request.getEntityType())
                .entityId(request.getEntityId())
                .objectType(request.getObjectType())
                .objectId(request.getObjectId())
                .content(request.getContent())
                .timestampSeconds(request.getTimestampSeconds());

        String mentionedIds = commentHelperUtill.resolveMentionedUserIds(request.getMentionedUserIds(), request.getMentionedUserIdList());
        if (mentionedIds != null) {
            builder.mentionedUserIds(mentionedIds);
        }

        if (request.getParentId() != null) {
            Comment parent = commentRepository.findById(request.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent comment", request.getParentId()));
            builder.parent(parent);
            parent.incrementReplies();
            commentRepository.save(parent);
        }

        if (request.getEmbed() != null) {
            builder.embedType(request.getEmbed().getType())
                    .embedUrl(request.getEmbed().getUrl())
                    .embedTitle(request.getEmbed().getTitle())
                    .embedDescription(request.getEmbed().getDescription())
                    .embedThumbnail(request.getEmbed().getThumbnail());
        }

        if (request.getAttachment() != null) {
            builder.attachmentUrl(request.getAttachment().getUrl())
                    .attachmentType(request.getAttachment().getType());
        }

        Comment comment = commentRepository.save(builder.build());
        log.info("Object comment {} added by user {}", comment.getId(), userId);

        return commentHelperUtill.buildCommentResponse(comment, userId);
    }

    @Transactional
    public Page<CommentResponseDTO> getComments(String entityType, Long entityId, Long userId, Pageable pageable) {
        Page<Comment> comments = commentRepository
                .findByEntityTypeAndEntityIdAndParentIsNullAndIsDeletedFalseOrderByCreatedAtDesc(entityType, entityId, pageable);

        return comments.map(c -> commentHelperUtill.buildCommentResponse(c, userId));
    }

    @Transactional
    public Page<CommentResponseDTO> getObjectComments(String entityType, Long entityId,
                                                      String objectType, Long objectId, Long userId, Pageable pageable) {
        Page<Comment> comments = commentRepository
                .findByEntityTypeAndEntityIdAndObjectTypeAndObjectIdAndParentIsNullAndIsDeletedFalseOrderByCreatedAtDesc(
                        entityType, entityId, objectType, objectId, pageable);

        return comments.map(c -> commentHelperUtill.buildCommentResponse(c, userId));
    }

    @Transactional
    public List<CommentResponseDTO> getVideoCommentsByTime(Long videoId, Integer startTime, Integer endTime, Long userId) {
        List<Comment> comments = commentRepository.findVideoCommentsByTimeRange(videoId, startTime, endTime);
        return comments.stream()
                .map(c -> commentHelperUtill.buildCommentResponse(c, userId))
                .toList();
    }

    @Transactional
    public List<CommentResponseDTO> getReplayCommentsByTime(Long replayId, Integer startTime, Integer endTime, Long userId) {
        List<Comment> comments = commentRepository.findReplayCommentsByTimeRange(replayId, startTime, endTime);
        return comments.stream()
                .map(c -> commentHelperUtill.buildCommentResponse(c, userId))
                .toList();
    }

    @Transactional
    public Page<CommentResponseDTO> getReplies(Long commentId, Long userId, Pageable pageable) {
        Page<Comment> replies = commentRepository.findByParentIdAndIsDeletedFalseOrderByCreatedAtAsc(commentId, pageable);
        return replies.map(c -> commentHelperUtill.buildCommentResponse(c, userId));
    }

    @Transactional
    public List<CommentResponseDTO> getPinnedComments(String entityType, Long entityId, Long userId) {
        List<Comment> comments = commentRepository
                .findByEntityTypeAndEntityIdAndIsPinnedTrueAndIsDeletedFalseOrderByCreatedAtDesc(entityType, entityId);
        return comments.stream()
                .map(c -> commentHelperUtill.buildCommentResponse(c, userId))
                .toList();
    }

    @Transactional
    public CommentResponseDTO editComment(Long commentId, Long userId, @Valid EditCommentRequestDTO request) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));

        if (!comment.getOwner().getId().equals(userId)) {
            throw new BadRequestException("You can only edit your own comments");
        }

        comment.edit(request.getContent());
        comment = commentRepository.save(comment);

        log.info("Comment {} edited by user {}", commentId, userId);
        return commentHelperUtill.buildCommentResponse(comment, userId);
    }

    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));

        if (!comment.getOwner().getId().equals(userId)) {
            throw new BadRequestException("You can only delete your own comments");
        }

        if (comment.getParent() != null) {
            comment.getParent().decrementReplies();
            commentRepository.save(comment.getParent());
        }

        comment.softDelete();
        commentRepository.save(comment);

        log.info("Comment {} deleted by user {}", commentId, userId);
    }

    @Transactional
    public CommentResponseDTO addEmbed(Long commentId, Long userId, CommentEmbedRequestDTO request) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));

        if (!comment.getOwner().getId().equals(userId)) {
            throw new BadRequestException("You can only modify your own comments");
        }

        comment.setEmbedType(request.getType());
        comment.setEmbedUrl(request.getUrl());
        comment.setEmbedTitle(request.getTitle());
        comment.setEmbedDescription(request.getDescription());
        comment.setEmbedThumbnail(request.getThumbnail());

        comment = commentRepository.save(comment);

        log.info("Embed added to comment {} by user {}", commentId, userId);
        return commentHelperUtill.buildCommentResponse(comment, userId);
    }

    @Transactional
    public CommentResponseDTO addAttachment(Long commentId, Long userId, CommentAttachmentRequestDTO request) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));

        if (!comment.getOwner().getId().equals(userId)) {
            throw new BadRequestException("You can only modify your own comments");
        }

        comment.setAttachmentUrl(request.getUrl());
        comment.setAttachmentType(request.getType());

        comment = commentRepository.save(comment);

        log.info("Attachment added to comment {} by user {}", commentId, userId);
        return commentHelperUtill.buildCommentResponse(comment, userId);
    }

    @Transactional
    public CommentResponseDTO togglePin(Long commentId, Long userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));

        commentHelperUtill.verifyCanPinOrHighlight(comment, userId);

        comment.setIsPinned(!Boolean.TRUE.equals(comment.getIsPinned()));
        comment = commentRepository.save(comment);

        log.info("Comment {} pin toggled by user {}", commentId, userId);
        return commentHelperUtill.buildCommentResponse(comment, userId);
    }

    @Transactional
    public CommentResponseDTO toggleHighlight(Long commentId, Long userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));

        commentHelperUtill.verifyCanPinOrHighlight(comment, userId);

        comment.setIsHighlighted(!Boolean.TRUE.equals(comment.getIsHighlighted()));
        comment = commentRepository.save(comment);

        log.info("Comment {} highlight toggled by user {}", commentId, userId);
        return commentHelperUtill.buildCommentResponse(comment, userId);
    }

    private List<RealTimeCommentsStatusDTO.typingUserDTO> getTypingUsers(String entityType, Long entityId) {

        Set<String> typingUserIds = redisUtill.getCommentTypingUsers(entityType, entityId);

        if (typingUserIds == null || typingUserIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<RealTimeCommentsStatusDTO.typingUserDTO> typingUsers = new ArrayList<>();

        for (String userIdStr : typingUserIds) {
            try {
                Long typingUserId = Long.parseLong(userIdStr);
                userRepository.findById(typingUserId).ifPresent(user -> {
                    String startedAt = redisUtill.getCommentTypingStartTime(entityType, entityId, typingUserId);

                    typingUsers.add(RealTimeCommentsStatusDTO.typingUserDTO.builder()
                            .userId(user.getId())
                            .username(user.getName())
                            .startedTypingAt(startedAt != null ? LocalDateTime.parse(startedAt) : null)
                            .build());
                });
            } catch (NumberFormatException e) {
                log.warn("Invalid user ID in typing set: {}", userIdStr);
            }
        }

        return typingUsers;
    }

    @Transactional
    public RealTimeCommentsStatusDTO getRealtimeStatus(String entityType, Long entityId, Long userId, Long afterCommentId) {

        boolean isEnabled = redisUtill.isCommentRealtimeEnabled(entityType, entityId, userId);

        List<Comment> newComments = new ArrayList<>();
        if (afterCommentId != null && afterCommentId > 0) {
            newComments = commentRepository.findNewComments(entityType, entityId, afterCommentId);
        }

        Long latestCommentId = commentRepository.findLatestCommentId(entityType, entityId);

        List<RealTimeCommentsStatusDTO.typingUserDTO> typingUsers = getTypingUsers(entityType, entityId);

        return RealTimeCommentsStatusDTO.builder()
                .entityType(entityType)
                .entityId(entityId)
                .isEnabled(isEnabled)
                .hasNewComments(!newComments.isEmpty())
                .newCommentsCount(newComments.size())
                .newComments(newComments.stream()
                        .map(c -> commentHelperUtill.buildCommentResponse(c, userId))
                        .toList())
                .latestCommentId(latestCommentId)
                .typingUsers(typingUsers)
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    public void setRealtimeStatus(Long userId, RealTimeStatusRequestDTO request) {
        String entityType = request.getEntityType();
        Long entityId = request.getEntityId();

        if (Boolean.TRUE.equals(request.getEnabled())) {
            redisUtill.enableCommentRealtime(entityType, entityId, userId, REALTIME_TTL_MINUTES);
        } else {
            redisUtill.disableCommentRealtime(entityType, entityId, userId);
        }

        if (request.getIsTyping() != null) {
            redisUtill.setCommentTyping(entityType, entityId, userId, request.getIsTyping());
        }

        log.info("Real-time status set for user {} on {} {}: enabled={}, typing={}",
                userId, entityType, entityId, request.getEnabled(), request.getIsTyping());
    }
}
