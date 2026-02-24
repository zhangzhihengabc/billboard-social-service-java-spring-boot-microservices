package com.billboard.social.graph.service;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.dto.UserSummary;
import com.billboard.social.common.client.UserServiceClient;
import com.billboard.social.graph.dto.request.SocialRequests.*;
import com.billboard.social.graph.dto.response.SocialResponses.*;
import com.billboard.social.graph.entity.Poke;
import com.billboard.social.graph.event.SocialEventPublisher;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.graph.repository.BlockRepository;
import com.billboard.social.graph.repository.PokeRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PokeService {

    private final PokeRepository pokeRepository;
    private final BlockRepository blockRepository;
    private final UserServiceClient userServiceClient;
    private final SocialEventPublisher eventPublisher;

    @Transactional
    public PokeResponse poke(Long pokerId, PokeRequest request) {
        if (request.getUserId() == null) {
            throw new ValidationException("User ID is required");
        }

        Long pokedId = request.getUserId();

        if (pokerId.equals(pokedId)) {
            throw new ValidationException("Cannot poke yourself");
        }

        if (blockRepository.isBlockedEitherWay(pokerId, pokedId)) {
            throw new ValidationException("Cannot poke this user");
        }

        var existingPoke = pokeRepository.findByPokerIdAndPokedId(pokerId, pokedId);

        Poke poke;
        if (existingPoke.isPresent()) {
            poke = existingPoke.get();
            poke.incrementPokeCount();
            poke = pokeRepository.save(poke);
            log.info("User {} poked {} again (count: {})", pokerId, pokedId, poke.getPokeCount());
        } else {
            poke = Poke.builder()
                    .pokerId(pokerId)
                    .pokedId(pokedId)
                    .build();

            try {
                poke = pokeRepository.save(poke);
            } catch (DataIntegrityViolationException e) {
                log.warn("Race condition detected for poke from {} to {}: {}", pokerId, pokedId, e.getMessage());
                throw new ValidationException("Poke already exists");
            }

            eventPublisher.publishUserPoked(poke);

            log.info("User {} poked {}", pokerId, pokedId);
        }

        return mapToPokeResponse(poke);
    }

    @Transactional
    public PokeResponse pokeBack(Long userId, UUID pokeId) {
        Poke poke = pokeRepository.findById(pokeId)
                .orElseThrow(() -> new ValidationException("Poke not found with id: " + pokeId));

        if (!poke.getPokedId().equals(userId)) {
            throw new ValidationException("Cannot poke back on this poke");
        }

        if (!poke.getIsActive()) {
            throw new ValidationException("This poke is no longer active");
        }

        poke.pokeBack();
        pokeRepository.save(poke);

        Poke newPoke = Poke.builder()
                .pokerId(userId)
                .pokedId(poke.getPokerId())
                .build();

        try {
            newPoke = pokeRepository.save(newPoke);
        } catch (DataIntegrityViolationException e) {
            var existing = pokeRepository.findByPokerIdAndPokedId(userId, poke.getPokerId());
            if (existing.isPresent()) {
                newPoke = existing.get();
                newPoke.incrementPokeCount();
                newPoke = pokeRepository.save(newPoke);
            } else {
                throw new ValidationException("Failed to create poke back");
            }
        }

        eventPublisher.publishUserPoked(newPoke);

        log.info("User {} poked back {}", userId, poke.getPokerId());
        return mapToPokeResponse(poke);
    }

    @Transactional(readOnly = true)
    public PageResponse<PokeResponse> getReceivedPokes(Long userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Poke> pokes = pokeRepository.findActivePokesForUser(userId, pageRequest);
        return PageResponse.from(pokes, this::mapToPokeResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<PokeResponse> getSentPokes(Long userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Poke> pokes = pokeRepository.findPokesSentByUser(userId, pageRequest);
        return PageResponse.from(pokes, this::mapToPokeResponse);
    }

    @Transactional(readOnly = true)
    public long getActivePokesCount(Long userId) {
        return pokeRepository.countByPokedIdAndIsActiveTrue(userId);
    }

    @Transactional
    public void dismissPoke(Long userId, UUID pokeId) {
        Poke poke = pokeRepository.findById(pokeId)
                .orElseThrow(() -> new ValidationException("Poke not found with id: " + pokeId));

        if (!poke.getPokedId().equals(userId)) {
            throw new ValidationException("Cannot dismiss this poke");
        }

        if (!poke.getIsActive()) {
            throw new ValidationException("Poke is already dismissed");
        }

        poke.deactivate();
        pokeRepository.save(poke);

        log.info("User {} dismissed poke {}", userId, pokeId);
    }

    private PokeResponse mapToPokeResponse(Poke poke) {
        UserSummary userSummary = fetchUserSummaryWithFallback(poke.getPokerId());

        return PokeResponse.builder()
                .id(poke.getId())
                .pokerId(poke.getPokerId())
                .pokedId(poke.getPokedId())
                .isActive(poke.getIsActive())
                .pokeCount(poke.getPokeCount())
                .pokedBackAt(poke.getPokedBackAt())
                .createdAt(poke.getCreatedAt())
                .poker(userSummary)
                .build();
    }

    private UserSummary fetchUserSummaryWithFallback(Long userId) {
        try {
            UserSummary summary = userServiceClient.getUserSummary(userId);
            if (summary != null) {
                return summary;
            }
            log.warn("User summary returned null for userId: {}", userId);
        } catch (FeignException.NotFound e) {
            log.warn("User not found in identity-service: {}", userId);
        } catch (FeignException e) {
            log.warn("Identity service unavailable for userId {}: Status {}", userId, e.status());
        } catch (Exception e) {
            log.warn("Failed to fetch user summary for userId {}: {} - {}",
                    userId, e.getClass().getSimpleName(), e.getMessage());
        }

        return UserSummary.builder()
                .id(userId)
                .username("Unknown")
                .email("unknown@gmail.com")
                .build();
    }
}