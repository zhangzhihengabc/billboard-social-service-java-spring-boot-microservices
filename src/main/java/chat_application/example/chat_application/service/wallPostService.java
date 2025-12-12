package chat_application.example.chat_application.service;

import chat_application.example.chat_application.dto.request.*;
import chat_application.example.chat_application.dto.response.wallPostResponseDTO;
import chat_application.example.chat_application.entities.Post;
import chat_application.example.chat_application.entities.User;
import chat_application.example.chat_application.entities.group.Group;
import chat_application.example.chat_application.exception.ForbiddenException;
import chat_application.example.chat_application.exception.ResourceNotFoundException;
import chat_application.example.chat_application.repository.groupRepository;
import chat_application.example.chat_application.repository.postRepository;
import chat_application.example.chat_application.repository.userRepository;
import chat_application.example.chat_application.utill.wallPostUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class wallPostService {

    private final postRepository postRepository;
    private final userRepository userRepository;
    private final groupRepository groupRepository;
    private final wallPostUtil wallPostUtil;

    @Transactional
    public wallPostResponseDTO createPost(Long userId, wallPostRequestDTO request) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        Post post = Post.builder()
                .owner(owner)
                .content(request.getContent())
                .postType("home")
                .privacy(request.getPrivacy() != null ? request.getPrivacy() : "PUBLIC")
                .location(request.getLocation())
                .build();

        applyEmbed(post, request.getEmbedUrl(), request.getEmbedType(),
                request.getEmbedTitle(), request.getEmbedDescription(), request.getEmbedThumbnail());

        post = postRepository.save(post);
        applyTaggedUsers(post, request.getTaggedUserIds());

        log.info("Post {} created by user {}", post.getId(), userId);
        return wallPostUtil.buildResponse(post, userId);
    }

    @Transactional
    public wallPostResponseDTO postOnUserWall(Long userId, userWallPostRequestDTO request) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        User targetUser = userRepository.findById(request.getTargetUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Target user", request.getTargetUserId()));

        boolean isFriend = wallPostUtil.areFriends(userId, request.getTargetUserId());
        if (!isFriend) {
            throw new ForbiddenException("Must be friends to post on user's wall");
        }

        wallPostUtil.verifyWallPostPrivacy(request.getTargetUserId(), userId, isFriend);

        Post post = Post.builder()
                .owner(owner)
                .targetUser(targetUser)
                .content(request.getContent())
                .postType("user")
                .privacy(request.getPrivacy() != null ? request.getPrivacy() : "FRIENDS")
                .location(request.getLocation())
                .build();

        applyEmbedFromRequest(post, request.getEmbed());
        post = postRepository.save(post);
        applyTaggedUsers(post, request.getTaggedUserIds());

        log.info("Post {} created on user {} wall by user {}", post.getId(), request.getTargetUserId(), userId);
        return wallPostUtil.buildResponse(post, userId);
    }

    @Transactional
    public wallPostResponseDTO postInGroup(Long userId, groupWallPostRequestDTO request) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        Group group = groupRepository.findById(request.getGroupId())
                .orElseThrow(() -> new ResourceNotFoundException("Group", request.getGroupId()));

        if (!groupRepository.existsByGroupIdAndUserId(request.getGroupId(), userId)) {
            throw new ForbiddenException("Must be a group member to post");
        }

        Post post = Post.builder()
                .owner(owner)
                .targetGroup(group)
                .content(request.getContent())
                .postType("group")
                .privacy("GROUP")
                .build();

        applyEmbedFromRequest(post, request.getEmbed());
        post = postRepository.save(post);
        applyTaggedUsers(post, request.getTaggedUserIds());

        log.info("Post {} created in group {} by user {}", post.getId(), request.getGroupId(), userId);
        return wallPostUtil.buildResponse(post, userId);
    }

    // ============================================
    // EDIT & DELETE
    // ============================================

    @Transactional
    public wallPostResponseDTO editPost(Long postId, Long userId, editPostRequestDTO request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post", postId));

        if (!post.getOwner().getId().equals(userId)) {
            throw new ForbiddenException("Can only edit your own posts");
        }

        if (request.getContent() != null) {
            post.setContent(request.getContent());
        }
        post.setIsEdited(true);
        post.setEditedAt(LocalDateTime.now());

        if (request.getPrivacy() != null) {
            post.setPrivacy(request.getPrivacy());
        }

        if (request.getLocation() != null) {
            post.setLocation(request.getLocation());
        }

        applyEmbedFromRequest(post, request.getEmbed());
        post = postRepository.save(post);
        applyTaggedUsers(post, request.getTaggedUserIds());

        log.info("Post {} edited by user {}", postId, userId);
        return wallPostUtil.buildResponse(post, userId);
    }

    @Transactional
    public void deletePost(Long postId, Long userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post", postId));

        if (!post.getOwner().getId().equals(userId)) {
            throw new ForbiddenException("Can only delete your own posts");
        }

        post.softDelete();
        postRepository.save(post);

        log.info("Post {} deleted by user {}", postId, userId);
    }

    @Transactional
    public wallPostResponseDTO addEmbed(Long postId, Long userId, embedRequestDTO request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post", postId));

        if (!post.getOwner().getId().equals(userId)) {
            throw new ForbiddenException("Can only modify your own posts");
        }

        applyEmbedFromRequest(post, request);
        post = postRepository.save(post);

        log.info("Embed added to post {} by user {}", postId, userId);
        return wallPostUtil.buildResponse(post, userId);
    }

    @Transactional
    public wallPostResponseDTO sharePost(Long userId, sharePostRequestDTO request) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        Post originalPost = postRepository.findById(request.getPostId())
                .orElseThrow(() -> new ResourceNotFoundException("Post", request.getPostId()));

        if ("PRIVATE".equals(originalPost.getPrivacy())) {
            throw new ForbiddenException("Cannot share private posts");
        }

        Post sharedPost = Post.builder()
                .owner(owner)
                .content(request.getComment())
                .postType("share")
                .privacy(request.getPrivacy() != null ? request.getPrivacy() : "PUBLIC")
                .originalPost(originalPost)
                .build();

        if (request.getTargetUserId() != null) {
            User targetUser = userRepository.findById(request.getTargetUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Target user", request.getTargetUserId()));
            sharedPost.setTargetUser(targetUser);
            sharedPost.setPostType("user");
        } else if (request.getTargetGroupId() != null) {
            Group targetGroup = groupRepository.findById(request.getTargetGroupId())
                    .orElseThrow(() -> new ResourceNotFoundException("Group", request.getTargetGroupId()));
            sharedPost.setTargetGroup(targetGroup);
            sharedPost.setPostType("group");
        }

        sharedPost = postRepository.save(sharedPost);

        originalPost.incrementShares();
        postRepository.save(originalPost);

        log.info("Post {} shared by user {}", request.getPostId(), userId);
        return wallPostUtil.buildResponse(sharedPost, userId);
    }

    // ============================================
    // QUERY METHODS
    // ============================================

    @Transactional
    public Page<wallPostResponseDTO> getUserWallPosts(Long userId, Long viewerId, Pageable pageable) {
        return postRepository.findUserWallPosts(userId, pageable)
                .map(post -> wallPostUtil.buildResponse(post, viewerId));
    }

    @Transactional
    public Page<wallPostResponseDTO> getGroupPosts(Long groupId, Long userId, Pageable pageable) {
        if (!groupRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new ForbiddenException("Must be a group member to view posts");
        }

        return postRepository.findByTargetGroupIdAndIsDeletedFalseOrderByCreatedAtDesc(groupId, pageable)
                .map(post -> wallPostUtil.buildResponse(post, userId));
    }

    @Transactional
    public wallPostResponseDTO getPost(Long postId, Long userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post", postId));

        if (Boolean.TRUE.equals(post.getIsDeleted())) {
            throw new ResourceNotFoundException("Post", postId);
        }

        return wallPostUtil.buildResponse(post, userId);
    }

    // ============================================
    // PRIVATE HELPER METHODS
    // ============================================

    private void applyEmbed(Post post, String url, String type, String title, String description, String thumbnail) {
        if (url != null) {
            post.setEmbedUrl(url);
            post.setEmbedType(type);
            post.setEmbedTitle(title);
            post.setEmbedDescription(description);
            post.setEmbedThumbnail(thumbnail);
        }
    }

    private void applyEmbedFromRequest(Post post, embedRequestDTO embed) {
        if (embed != null) {
            post.setEmbedUrl(embed.getUrl());
            post.setEmbedType(embed.getType());
            post.setEmbedTitle(embed.getTitle());
            post.setEmbedDescription(embed.getDescription());
            post.setEmbedThumbnail(embed.getThumbnail());
        }
    }

    private void applyTaggedUsers(Post post, List<Long> taggedUserIds) {
        if (taggedUserIds != null && !taggedUserIds.isEmpty()) {
            String taggedIds = taggedUserIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            post.setTaggedUserIds(taggedIds);
            postRepository.save(post);
        }
    }
}
