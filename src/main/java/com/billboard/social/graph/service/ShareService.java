package com.billboard.social.graph.service;
import com.billboard.social.common.dto.UserSummary;

import com.billboard.social.common.client.UserServiceClient;
import com.billboard.social.graph.dto.request.SocialRequests.*;
import com.billboard.social.graph.dto.response.SocialResponses.*;
import com.billboard.social.graph.entity.Share;
import com.billboard.social.graph.entity.enums.ContentType;
import com.billboard.social.graph.event.SocialEventPublisher;
import com.billboard.social.graph.repository.BlockRepository;
import com.billboard.social.graph.repository.ShareRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShareService {

    private final ShareRepository shareRepository;
    private final BlockRepository blockRepository;
    private final UserServiceClient userServiceClient;
    private final SocialEventPublisher eventPublisher;

    @Transactional
    public ShareResponse share(UUID userId, ShareRequest request) {
        // Check if private share to blocked user
        if (request.getTargetUserId() != null && 
            blockRepository.isBlockedEitherWay(userId, request.getTargetUserId())) {
            throw new IllegalArgumentException("Cannot share to this user");
        }

        Share share = Share.builder()
            .userId(userId)
            .contentType(request.getContentType())
            .contentId(request.getContentId())
            .contentOwnerId(request.getContentOwnerId())
            .targetUserId(request.getTargetUserId())
            .message(request.getMessage())
            .shareToFeed(request.getShareToFeed())
            .shareToStory(request.getShareToStory())
            .isPrivateShare(request.getIsPrivateShare())
            .build();

        share = shareRepository.save(share);

        // Publish event
        eventPublisher.publishContentShared(share);

        log.info("User {} shared {} {}", userId, request.getContentType(), request.getContentId());
        return mapToShareResponse(share);
    }

    @Transactional(readOnly = true)
    public Page<ShareResponse> getSharesByContent(ContentType contentType, UUID contentId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Share> shares = shareRepository.findByContentTypeAndContentId(contentType, contentId, pageRequest);
        return shares.map(this::mapToShareResponse);
    }

    @Transactional(readOnly = true)
    public Page<ShareResponse> getSharesByUser(UUID userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Share> shares = shareRepository.findByUserId(userId, pageRequest);
        return shares.map(this::mapToShareResponse);
    }

    @Transactional(readOnly = true)
    public long getShareCount(ContentType contentType, UUID contentId) {
        return shareRepository.countByContentTypeAndContentId(contentType, contentId);
    }

    private ShareResponse mapToShareResponse(Share share) {
        UserSummary userSummary = userServiceClient.getUserSummary(share.getUserId());

        return ShareResponse.builder()
            .id(share.getId())
            .userId(share.getUserId())
            .contentType(share.getContentType())
            .contentId(share.getContentId())
            .targetUserId(share.getTargetUserId())
            .message(share.getMessage())
            .shareToFeed(share.getShareToFeed())
            .shareToStory(share.getShareToStory())
            .isPrivateShare(share.getIsPrivateShare())
            .createdAt(share.getCreatedAt())
            .user(userSummary)
            .build();
    }
}
