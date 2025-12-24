package com.ossn.content.feed.service;

import com.ossn.content.feed.client.UserServiceClient;
import com.ossn.content.feed.dto.request.FeedRequests.*;
import com.ossn.content.feed.dto.response.FeedResponses.*;
import com.ossn.content.feed.entity.Comment;
import com.ossn.content.feed.entity.CommentReaction;
import com.ossn.content.feed.entity.Post;
import com.ossn.content.feed.entity.enums.ReactionType;
import com.ossn.content.feed.event.FeedEventPublisher;
import com.ossn.content.feed.exception.ForbiddenException;
import com.ossn.content.feed.exception.ResourceNotFoundException;
import com.ossn.content.feed.exception.ValidationException;
import com.ossn.content.feed.repository.CommentReactionRepository;
import com.ossn.content.feed.repository.CommentRepository;
import com.ossn.content.feed.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentService {

    private final CommentRepository commentRepository;
    private final CommentReactionRepository reactionRepository;
    private final PostRepository postRepository;
    private final UserServiceClient userServiceClient;
    private final FeedEventPublisher eventPublisher;

    @Transactional
    public CommentResponse createComment(UUID userId, UUID postId, CreateCommentRequest request) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new ResourceNotFoundException("Post", "id", postId));

        if (!post.getAllowComments()) {
            throw new ValidationException("Comments are disabled for this post");
        }

        Comment parent = null;
        if (request.getParentId() != null) {
            parent = commentRepository.findById(request.getParentId())
                .orElseThrow(() -> new ResourceNotFoundException("Comment", "id", request.getParentId()));
            
            if (!parent.getPost().getId().equals(postId)) {
                throw new ValidationException("Parent comment does not belong to this post");
            }
        }

        Comment comment = Comment.builder()
            .post(post)
            .authorId(userId)
            .parent(parent)
            .content(request.getContent())
            .mediaUrl(request.getMediaUrl())
            .mediaType(request.getMediaType())
            .build();

        comment = commentRepository.save(comment);

        // Update counts
        post.incrementCommentCount();
        postRepository.save(post);

        if (parent != null) {
            parent.incrementReplyCount();
            commentRepository.save(parent);
        }

        eventPublisher.publishCommentCreated(comment);

        log.info("Comment {} created on post {} by user {}", comment.getId(), postId, userId);
        return mapToCommentResponse(comment, userId);
    }

    @Transactional
    public CommentResponse updateComment(UUID userId, UUID commentId, UpdateCommentRequest request) {
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new ResourceNotFoundException("Comment", "id", commentId));

        if (!comment.getAuthorId().equals(userId)) {
            throw new ForbiddenException("You can only edit your own comments");
        }

        comment.setContent(request.getContent());
        comment.markEdited();

        comment = commentRepository.save(comment);

        log.info("Comment {} updated by user {}", commentId, userId);
        return mapToCommentResponse(comment, userId);
    }

    @Transactional
    public void deleteComment(UUID userId, UUID commentId) {
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new ResourceNotFoundException("Comment", "id", commentId));

        Post post = comment.getPost();

        boolean canDelete = comment.getAuthorId().equals(userId) ||
            post.getAuthorId().equals(userId);

        if (!canDelete) {
            throw new ForbiddenException("You cannot delete this comment");
        }

        // Update counts
        post.decrementCommentCount();
        postRepository.save(post);

        if (comment.getParent() != null) {
            comment.getParent().decrementReplyCount();
            commentRepository.save(comment.getParent());
        }

        comment.softDelete();
        commentRepository.save(comment);

        eventPublisher.publishCommentDeleted(comment);

        log.info("Comment {} deleted by user {}", commentId, userId);
    }

    @Transactional(readOnly = true)
    public Page<CommentResponse> getPostComments(UUID postId, UUID currentUserId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<Comment> comments = commentRepository.findByPostIdAndParentIsNullOrderByCreatedAtDesc(postId, pageRequest);
        return comments.map(c -> mapToCommentResponse(c, currentUserId));
    }

    @Transactional(readOnly = true)
    public Page<CommentResponse> getCommentReplies(UUID commentId, UUID currentUserId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<Comment> replies = commentRepository.findByParentIdOrderByCreatedAtAsc(commentId, pageRequest);
        return replies.map(c -> mapToCommentResponse(c, currentUserId));
    }

    @Transactional
    public void likeComment(UUID userId, UUID commentId) {
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new ResourceNotFoundException("Comment", "id", commentId));

        if (reactionRepository.existsByCommentIdAndUserId(commentId, userId)) {
            throw new ValidationException("You have already liked this comment");
        }

        CommentReaction reaction = CommentReaction.builder()
            .comment(comment)
            .userId(userId)
            .reactionType(ReactionType.LIKE)
            .build();

        reactionRepository.save(reaction);
        comment.incrementLikeCount();
        commentRepository.save(comment);

        log.info("User {} liked comment {}", userId, commentId);
    }

    @Transactional
    public void unlikeComment(UUID userId, UUID commentId) {
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new ResourceNotFoundException("Comment", "id", commentId));

        if (!reactionRepository.existsByCommentIdAndUserId(commentId, userId)) {
            throw new ValidationException("You have not liked this comment");
        }

        reactionRepository.deleteByCommentIdAndUserId(commentId, userId);
        comment.decrementLikeCount();
        commentRepository.save(comment);

        log.info("User {} unliked comment {}", userId, commentId);
    }

    @Transactional
    public void pinComment(UUID userId, UUID postId, UUID commentId) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new ResourceNotFoundException("Post", "id", postId));

        if (!post.getAuthorId().equals(userId)) {
            throw new ForbiddenException("Only the post author can pin comments");
        }

        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new ResourceNotFoundException("Comment", "id", commentId));

        if (!comment.getPost().getId().equals(postId)) {
            throw new ValidationException("Comment does not belong to this post");
        }

        comment.setIsPinned(true);
        commentRepository.save(comment);

        log.info("Comment {} pinned on post {} by user {}", commentId, postId, userId);
    }

    private CommentResponse mapToCommentResponse(Comment comment, UUID currentUserId) {
        CommentResponse.CommentResponseBuilder builder = CommentResponse.builder()
            .id(comment.getId())
            .postId(comment.getPost().getId())
            .parentId(comment.getParent() != null ? comment.getParent().getId() : null)
            .content(comment.getContent())
            .mediaUrl(comment.getMediaUrl())
            .mediaType(comment.getMediaType())
            .likeCount(comment.getLikeCount())
            .replyCount(comment.getReplyCount())
            .isEdited(comment.getIsEdited())
            .isPinned(comment.getIsPinned())
            .createdAt(comment.getCreatedAt())
            .updatedAt(comment.getUpdatedAt());

        // Get author info
        try {
            UserSummary author = userServiceClient.getUserSummary(comment.getAuthorId());
            builder.author(author);
        } catch (Exception e) {
            log.warn("Failed to fetch comment author info: {}", e.getMessage());
        }

        // Current user context
        if (currentUserId != null) {
            builder.isAuthor(comment.getAuthorId().equals(currentUserId));
            builder.canEdit(comment.getAuthorId().equals(currentUserId));
            builder.canDelete(comment.getAuthorId().equals(currentUserId) || 
                comment.getPost().getAuthorId().equals(currentUserId));
            builder.hasLiked(reactionRepository.existsByCommentIdAndUserId(comment.getId(), currentUserId));
        }

        return builder.build();
    }
}
