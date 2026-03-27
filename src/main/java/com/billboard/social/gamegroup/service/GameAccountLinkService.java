package com.billboard.social.gamegroup.service;

import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.gamegroup.dto.request.GameGroupRequests.LinkGameAccountRequest;
import com.billboard.social.gamegroup.dto.response.GameGroupResponses.GameAccountLinkResponse;
import com.billboard.social.gamegroup.entity.GameAccountLink;
import com.billboard.social.gamegroup.repository.GameAccountLinkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameAccountLinkService {

    private final GameAccountLinkRepository gameAccountLinkRepository;

    @Transactional
    public GameAccountLinkResponse linkAccount(Long userId, LinkGameAccountRequest request) {

        // If already linked and verified, don't duplicate
        boolean alreadyLinked = gameAccountLinkRepository
                .existsByUserIdAndGameTagAndVerificationStatus(userId, request.getGameTag(), "VERIFIED");
        if (alreadyLinked) {
            throw new ValidationException(
                    "A verified game account for " + request.getGameTag() + " is already linked");
        }

        // Upsert — update existing record or create new one
        GameAccountLink link = gameAccountLinkRepository
                .findByUserIdAndGameTagAndGameAccountId(userId, request.getGameTag(), request.getGameAccountId())
                .orElse(GameAccountLink.builder()
                        .userId(userId)
                        .gameTag(request.getGameTag())
                        .gameAccountId(request.getGameAccountId())
                        .build());

        link.setGameAccountName(request.getGameAccountName());
        link.setVerificationStatus("VERIFIED");
        link.setVerifiedAt(LocalDateTime.now());

        link = gameAccountLinkRepository.save(link);
        log.info("Game account linked: user={} gameTag={} accountId={}", userId, request.getGameTag(), request.getGameAccountId());
        return mapToResponse(link);
    }

    @Transactional(readOnly = true)
    public List<GameAccountLinkResponse> getUserAccounts(Long userId) {
        return gameAccountLinkRepository.findByUserId(userId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public boolean hasVerifiedAccount(Long userId, String gameTag) {
        return gameAccountLinkRepository
                .existsByUserIdAndGameTagAndVerificationStatus(userId, gameTag, "VERIFIED");
    }

    private GameAccountLinkResponse mapToResponse(GameAccountLink link) {
        return GameAccountLinkResponse.builder()
                .id(link.getId())
                .userId(link.getUserId())
                .gameTag(link.getGameTag())
                .gameAccountId(link.getGameAccountId())
                .gameAccountName(link.getGameAccountName())
                .verificationStatus(link.getVerificationStatus())
                .verifiedAt(link.getVerifiedAt())
                .createdAt(link.getCreatedAt())
                .build();
    }
}