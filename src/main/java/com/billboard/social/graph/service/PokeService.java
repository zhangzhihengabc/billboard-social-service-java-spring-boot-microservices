package com.billboard.social.graph.service;
import com.billboard.social.common.dto.UserSummary;

import com.billboard.social.common.client.UserServiceClient;
import com.billboard.social.graph.dto.request.SocialRequests.*;
import com.billboard.social.graph.dto.response.SocialResponses.*;
import com.billboard.social.graph.entity.Poke;
import com.billboard.social.graph.event.SocialEventPublisher;
import com.billboard.social.common.exception.ResourceNotFoundException;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.graph.repository.BlockRepository;
import com.billboard.social.graph.repository.PokeRepository;
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
public class PokeService {

    private final PokeRepository pokeRepository;
    private final BlockRepository blockRepository;
    private final UserServiceClient userServiceClient;
    private final SocialEventPublisher eventPublisher;

    @Transactional
    public PokeResponse poke(UUID pokerId, PokeRequest request) {
        UUID pokedId = request.getUserId();

        // Validate not self
        if (pokerId.equals(pokedId)) {
            throw new ValidationException("Cannot poke yourself");
        }

        // Check if blocked
        if (blockRepository.isBlockedEitherWay(pokerId, pokedId)) {
            throw new ValidationException("Cannot poke this user");
        }

        // Check if there's already an active poke
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
            poke = pokeRepository.save(poke);

            // Publish event
            eventPublisher.publishUserPoked(poke);

            log.info("User {} poked {}", pokerId, pokedId);
        }

        return mapToPokeResponse(poke);
    }

    @Transactional
    public PokeResponse pokeBack(UUID userId, UUID pokeId) {
        Poke poke = pokeRepository.findById(pokeId)
            .orElseThrow(() -> new ResourceNotFoundException("Poke", "id", pokeId));

        if (!poke.getPokedId().equals(userId)) {
            throw new ValidationException("Cannot poke back on this poke");
        }

        if (!poke.getIsActive()) {
            throw new ValidationException("This poke is no longer active");
        }

        poke.pokeBack();
        poke = pokeRepository.save(poke);

        // Create a new poke in the opposite direction
        Poke newPoke = Poke.builder()
            .pokerId(userId)
            .pokedId(poke.getPokerId())
            .build();
        newPoke = pokeRepository.save(newPoke);

        // Publish event
        eventPublisher.publishUserPoked(newPoke);

        log.info("User {} poked back {}", userId, poke.getPokerId());
        return mapToPokeResponse(newPoke);
    }

    @Transactional(readOnly = true)
    public Page<PokeResponse> getReceivedPokes(UUID userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Poke> pokes = pokeRepository.findActivePokesForUser(userId, pageRequest);
        return pokes.map(this::mapToPokeResponse);
    }

    @Transactional(readOnly = true)
    public Page<PokeResponse> getSentPokes(UUID userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Poke> pokes = pokeRepository.findPokesSentByUser(userId, pageRequest);
        return pokes.map(this::mapToPokeResponse);
    }

    @Transactional(readOnly = true)
    public long getActivePokesCount(UUID userId) {
        return pokeRepository.countByPokedIdAndIsActiveTrue(userId);
    }

    @Transactional
    public void dismissPoke(UUID userId, UUID pokeId) {
        Poke poke = pokeRepository.findById(pokeId)
            .orElseThrow(() -> new ResourceNotFoundException("Poke", "id", pokeId));

        if (!poke.getPokedId().equals(userId)) {
            throw new ValidationException("Cannot dismiss this poke");
        }

        poke.deactivate();
        pokeRepository.save(poke);

        log.info("User {} dismissed poke {}", userId, pokeId);
    }

    private PokeResponse mapToPokeResponse(Poke poke) {
        UserSummary userSummary = userServiceClient.getUserSummary(poke.getPokerId());

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
}
