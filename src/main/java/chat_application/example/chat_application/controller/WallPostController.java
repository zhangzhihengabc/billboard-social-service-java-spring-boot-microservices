package chat_application.example.chat_application.controller;

import chat_application.example.chat_application.dto.request.*;
import chat_application.example.chat_application.dto.request.post.*;
import chat_application.example.chat_application.dto.response.WallPostResponseDTO;
import chat_application.example.chat_application.service.WallPostService;
import chat_application.example.chat_application.utill.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class WallPostController {

    private final WallPostService wallPostService;

    @PostMapping
    public ResponseEntity<ApiResponse<WallPostResponseDTO>> createPost(
            @RequestParam Long userId,
            @Valid @RequestBody WallPostRequestDTO request) {

        WallPostResponseDTO post = wallPostService.createPost(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Post created successfully", post));
    }

    @PostMapping("/user")
    public ResponseEntity<ApiResponse<WallPostResponseDTO>> postOnUserWall(
            @RequestParam Long userId,
            @Valid @RequestBody UserWallPostRequestDTO request) {

        WallPostResponseDTO post = wallPostService.postOnUserWall(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Post created successfully", post));
    }

    @PostMapping("/group")
    public ResponseEntity<ApiResponse<WallPostResponseDTO>> postInGroup(
            @RequestParam Long userId,
            @Valid @RequestBody GroupWallPostRequestDTO request) {

        WallPostResponseDTO post = wallPostService.postInGroup(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Post created successfully", post));
    }

    @PutMapping("/{postId}")
    public ResponseEntity<ApiResponse<WallPostResponseDTO>> editPost(
            @PathVariable Long postId,
            @RequestParam Long userId,
            @Valid @RequestBody EditPostRequestDTO request) {

        WallPostResponseDTO post = wallPostService.editPost(postId, userId, request);

        return ResponseEntity.ok(ApiResponse.success("Post updated successfully", post));
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<ApiResponse<Void>> deletePost(
            @PathVariable Long postId,
            @RequestParam Long userId) {

        wallPostService.deletePost(postId, userId);

        return ResponseEntity.ok(ApiResponse.success("Post deleted successfully",null));
    }

    @PostMapping("/{postId}/embed")
    public ResponseEntity<ApiResponse<WallPostResponseDTO>> addEmbed(
            @PathVariable Long postId,
            @RequestParam Long userId,
            @Valid @RequestBody EmbedRequestDTO request) {

        WallPostResponseDTO post = wallPostService.addEmbed(postId, userId, request);

        return ResponseEntity.ok(ApiResponse.success("Embed added successfully", post));
    }

    @PostMapping("/share")
    public ResponseEntity<ApiResponse<WallPostResponseDTO>> sharePost(
            @RequestParam Long userId,
            @Valid @RequestBody SharePostRequestDTO request) {

        WallPostResponseDTO post = wallPostService.sharePost(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Post shared successfully", post));
    }

    @GetMapping("/{postId}")
    public ResponseEntity<ApiResponse<WallPostResponseDTO>> getPost(
            @PathVariable Long postId,
            @RequestParam(required = false) Long userId) {

        WallPostResponseDTO post = wallPostService.getPost(postId, userId);

        return ResponseEntity.ok(ApiResponse.success("Post retrieved", post));
    }

    @GetMapping("/user/{targetUserId}/wall")
    public ResponseEntity<ApiResponse<Page<WallPostResponseDTO>>> getUserWallPosts(
            @PathVariable Long targetUserId,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<WallPostResponseDTO> posts = wallPostService.getUserWallPosts(targetUserId, userId, pageable);

        return ResponseEntity.ok(ApiResponse.success("Wall posts retrieved", posts));
    }

    @GetMapping("/group/{groupId}")
    public ResponseEntity<ApiResponse<Page<WallPostResponseDTO>>> getGroupPosts(
            @PathVariable Long groupId,
            @RequestParam Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<WallPostResponseDTO> posts = wallPostService.getGroupPosts(groupId, userId, pageable);

        return ResponseEntity.ok(ApiResponse.success("Group posts retrieved", posts));
    }
}
