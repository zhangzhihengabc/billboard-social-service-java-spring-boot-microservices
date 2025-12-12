package chat_application.example.chat_application.controller;

import chat_application.example.chat_application.dto.request.*;
import chat_application.example.chat_application.dto.response.wallPostResponseDTO;
import chat_application.example.chat_application.service.wallPostService;
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
public class wallPostController {

    private final wallPostService wallPostService;

    @PostMapping
    public ResponseEntity<ApiResponse<wallPostResponseDTO>> createPost(
            @RequestParam Long userId,
            @Valid @RequestBody wallPostRequestDTO request) {

        wallPostResponseDTO post = wallPostService.createPost(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Post created successfully", post));
    }

    @PostMapping("/user")
    public ResponseEntity<ApiResponse<wallPostResponseDTO>> postOnUserWall(
            @RequestParam Long userId,
            @Valid @RequestBody userWallPostRequestDTO request) {

        wallPostResponseDTO post = wallPostService.postOnUserWall(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Post created successfully", post));
    }

    @PostMapping("/group")
    public ResponseEntity<ApiResponse<wallPostResponseDTO>> postInGroup(
            @RequestParam Long userId,
            @Valid @RequestBody groupWallPostRequestDTO request) {

        wallPostResponseDTO post = wallPostService.postInGroup(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Post created successfully", post));
    }

    @PutMapping("/{postId}")
    public ResponseEntity<ApiResponse<wallPostResponseDTO>> editPost(
            @PathVariable Long postId,
            @RequestParam Long userId,
            @Valid @RequestBody editPostRequestDTO request) {

        wallPostResponseDTO post = wallPostService.editPost(postId, userId, request);

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
    public ResponseEntity<ApiResponse<wallPostResponseDTO>> addEmbed(
            @PathVariable Long postId,
            @RequestParam Long userId,
            @Valid @RequestBody embedRequestDTO request) {

        wallPostResponseDTO post = wallPostService.addEmbed(postId, userId, request);

        return ResponseEntity.ok(ApiResponse.success("Embed added successfully", post));
    }

    @PostMapping("/share")
    public ResponseEntity<ApiResponse<wallPostResponseDTO>> sharePost(
            @RequestParam Long userId,
            @Valid @RequestBody sharePostRequestDTO request) {

        wallPostResponseDTO post = wallPostService.sharePost(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Post shared successfully", post));
    }

    @GetMapping("/{postId}")
    public ResponseEntity<ApiResponse<wallPostResponseDTO>> getPost(
            @PathVariable Long postId,
            @RequestParam(required = false) Long userId) {

        wallPostResponseDTO post = wallPostService.getPost(postId, userId);

        return ResponseEntity.ok(ApiResponse.success("Post retrieved", post));
    }

    @GetMapping("/user/{targetUserId}/wall")
    public ResponseEntity<ApiResponse<Page<wallPostResponseDTO>>> getUserWallPosts(
            @PathVariable Long targetUserId,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<wallPostResponseDTO> posts = wallPostService.getUserWallPosts(targetUserId, userId, pageable);

        return ResponseEntity.ok(ApiResponse.success("Wall posts retrieved", posts));
    }

    @GetMapping("/group/{groupId}")
    public ResponseEntity<ApiResponse<Page<wallPostResponseDTO>>> getGroupPosts(
            @PathVariable Long groupId,
            @RequestParam Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<wallPostResponseDTO> posts = wallPostService.getGroupPosts(groupId, userId, pageable);

        return ResponseEntity.ok(ApiResponse.success("Group posts retrieved", posts));
    }
}
