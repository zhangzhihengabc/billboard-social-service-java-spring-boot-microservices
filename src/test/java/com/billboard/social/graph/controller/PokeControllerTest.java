package com.billboard.social.graph.controller;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.dto.UserSummary;
import com.billboard.social.common.exception.GlobalExceptionHandler;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.common.security.UserPrincipal;
import com.billboard.social.graph.dto.request.SocialRequests.PokeRequest;
import com.billboard.social.graph.dto.response.SocialResponses.PokeResponse;
import com.billboard.social.graph.service.PokeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.*;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PokeController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PokeControllerTest {

    private static final Long USER_ID = 1L;
    private static final Long TARGET_USER_ID = 2L;
    private static final UUID POKE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PokeService pokeService;

    private PokeResponse testPokeResponse;

    @BeforeEach
    void setUp() {
        UserPrincipal userPrincipal = UserPrincipal.builder()
                .id(USER_ID)
                .username("testuser")
                .email("test@example.com")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userPrincipal, null, userPrincipal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        UserSummary pokerSummary = UserSummary.builder()
                .id(USER_ID)
                .username("testuser")
                .email("test@gmail.com")
                .build();

        UserSummary pokedSummary = UserSummary.builder()
                .id(TARGET_USER_ID)
                .username("targetuser")
                .email("test@gmail.com")
                .build();

        testPokeResponse = PokeResponse.builder()
                .id(POKE_ID)
                .pokerId(USER_ID)
                .pokedId(TARGET_USER_ID)
                .isActive(true)
                .pokeCount(1)
                .createdAt(LocalDateTime.now())
                .poker(pokerSummary)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ==================== POKE ====================

    @Nested
    @DisplayName("POST /pokes - poke")
    class PokeTests {

        @Test
        @DisplayName("Success - returns 201")
        void poke_Success() throws Exception {
            PokeRequest request = PokeRequest.builder()
                    .userId(TARGET_USER_ID)
                    .build();

            when(pokeService.poke(eq(USER_ID), any(PokeRequest.class)))
                    .thenReturn(testPokeResponse);

            mockMvc.perform(post("/api/v1/pokes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(POKE_ID.toString()))
                    .andExpect(jsonPath("$.pokerId").value(USER_ID.toString()))
                    .andExpect(jsonPath("$.pokedId").value(TARGET_USER_ID.toString()))
                    .andExpect(jsonPath("$.isActive").value(true))
                    .andExpect(jsonPath("$.pokeCount").value(1));
        }

        @Test
        @DisplayName("Success - without message")
        void poke_WithoutMessage() throws Exception {
            PokeRequest request = PokeRequest.builder()
                    .userId(TARGET_USER_ID)
                    .build();

            when(pokeService.poke(eq(USER_ID), any(PokeRequest.class)))
                    .thenReturn(testPokeResponse);

            mockMvc.perform(post("/api/v1/pokes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.message").doesNotExist());
        }

        @Test
        @DisplayName("Missing userId - returns 400")
        void poke_MissingUserId() throws Exception {
            PokeRequest request = PokeRequest.builder()
                    .build();

            mockMvc.perform(post("/api/v1/pokes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(pokeService);
        }

        @Test
        @DisplayName("Cannot poke yourself - returns 400")
        void poke_CannotPokeYourself() throws Exception {
            PokeRequest request = PokeRequest.builder()
                    .userId(TARGET_USER_ID)
                    .build();

            when(pokeService.poke(eq(USER_ID), any(PokeRequest.class)))
                    .thenThrow(new ValidationException("Cannot poke yourself"));

            mockMvc.perform(post("/api/v1/pokes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Cannot poke yourself"));
        }

        @Test
        @DisplayName("User blocked - returns 400")
        void poke_UserBlocked() throws Exception {
            PokeRequest request = PokeRequest.builder()
                    .userId(TARGET_USER_ID)
                    .build();

            when(pokeService.poke(eq(USER_ID), any(PokeRequest.class)))
                    .thenThrow(new ValidationException("Cannot poke a blocked user"));

            mockMvc.perform(post("/api/v1/pokes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Poke already exists - returns 400")
        void poke_AlreadyExists() throws Exception {
            PokeRequest request = PokeRequest.builder()
                    .userId(TARGET_USER_ID)
                    .build();

            when(pokeService.poke(eq(USER_ID), any(PokeRequest.class)))
                    .thenThrow(new ValidationException("Active poke already exists"));

            mockMvc.perform(post("/api/v1/pokes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("User not found - returns 400")
        void poke_UserNotFound() throws Exception {
            PokeRequest request = PokeRequest.builder()
                    .userId(TARGET_USER_ID)
                    .build();

            when(pokeService.poke(eq(USER_ID), any(PokeRequest.class)))
                    .thenThrow(new ValidationException("User not found"));

            mockMvc.perform(post("/api/v1/pokes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Missing request body - returns 400")
        void poke_MissingBody() throws Exception {
            mockMvc.perform(post("/api/v1/pokes")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(pokeService);
        }

        @Test
        @DisplayName("Malformed JSON - returns 400")
        void poke_MalformedJson() throws Exception {
            mockMvc.perform(post("/api/v1/pokes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"userId\": }"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(pokeService);
        }
    }

    // ==================== POKE BACK ====================

    @Nested
    @DisplayName("POST /pokes/{pokeId}/poke-back - pokeBack")
    class PokeBackTests {

        @Test
        @DisplayName("Success - returns 200")
        void pokeBack_Success() throws Exception {
            testPokeResponse.setIsActive(true);
            testPokeResponse.setPokeCount(2);
            testPokeResponse.setPokedBackAt(LocalDateTime.now());

            when(pokeService.pokeBack(USER_ID, POKE_ID)).thenReturn(testPokeResponse);

            mockMvc.perform(post("/api/v1/pokes/{pokeId}/poke-back", POKE_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(POKE_ID.toString()))
                    .andExpect(jsonPath("$.isActive").value(true))
                    .andExpect(jsonPath("$.pokeCount").value(2))
                    .andExpect(jsonPath("$.pokedBackAt").exists());
        }

        @Test
        @DisplayName("Poke not found - returns 400")
        void pokeBack_PokeNotFound() throws Exception {
            when(pokeService.pokeBack(USER_ID, POKE_ID))
                    .thenThrow(new ValidationException("Poke not found"));

            mockMvc.perform(post("/api/v1/pokes/{pokeId}/poke-back", POKE_ID))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Poke not found"));
        }

        @Test
        @DisplayName("Not the poked user - returns 400")
        void pokeBack_NotThePokedUser() throws Exception {
            when(pokeService.pokeBack(USER_ID, POKE_ID))
                    .thenThrow(new ValidationException("Only the poked user can poke back"));

            mockMvc.perform(post("/api/v1/pokes/{pokeId}/poke-back", POKE_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Poke inactive - returns 400")
        void pokeBack_PokeInactive() throws Exception {
            when(pokeService.pokeBack(USER_ID, POKE_ID))
                    .thenThrow(new ValidationException("Poke is not active"));

            mockMvc.perform(post("/api/v1/pokes/{pokeId}/poke-back", POKE_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void pokeBack_InvalidUuid() throws Exception {
            mockMvc.perform(post("/api/v1/pokes/{pokeId}/poke-back", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(pokeService);
        }
    }

    // ==================== GET RECEIVED POKES ====================

    @Nested
    @DisplayName("GET /pokes/received - getReceivedPokes")
    class GetReceivedPokesTests {

        @Test
        @DisplayName("Success - returns paginated received pokes")
        void getReceivedPokes_Success() throws Exception {
            PageResponse<PokeResponse> pageResponse = PageResponse.<PokeResponse>builder()
                    .content(List.of(testPokeResponse))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .build();

            when(pokeService.getReceivedPokes(USER_ID, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/pokes/received"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].id").value(POKE_ID.toString()))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("Success - empty list")
        void getReceivedPokes_Empty() throws Exception {
            PageResponse<PokeResponse> pageResponse = PageResponse.<PokeResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(pokeService.getReceivedPokes(USER_ID, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/pokes/received"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        @DisplayName("Custom pagination")
        void getReceivedPokes_CustomPagination() throws Exception {
            PageResponse<PokeResponse> pageResponse = PageResponse.<PokeResponse>builder()
                    .content(Collections.emptyList())
                    .page(5)
                    .size(50)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(pokeService.getReceivedPokes(USER_ID, 5, 50)).thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/pokes/received")
                            .param("page", "5")
                            .param("size", "50"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(5))
                    .andExpect(jsonPath("$.size").value(50));
        }

        @Test
        @DisplayName("Page below minimum - returns 400")
        void getReceivedPokes_PageBelowMin() throws Exception {
            mockMvc.perform(get("/api/v1/pokes/received")
                            .param("page", "-1"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(pokeService);
        }

        @Test
        @DisplayName("Page above maximum - returns 400")
        void getReceivedPokes_PageAboveMax() throws Exception {
            mockMvc.perform(get("/api/v1/pokes/received")
                            .param("page", "1001"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(pokeService);
        }

        @Test
        @DisplayName("Size below minimum - returns 400")
        void getReceivedPokes_SizeBelowMin() throws Exception {
            mockMvc.perform(get("/api/v1/pokes/received")
                            .param("size", "0"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(pokeService);
        }

        @Test
        @DisplayName("Size above maximum - returns 400")
        void getReceivedPokes_SizeAboveMax() throws Exception {
            mockMvc.perform(get("/api/v1/pokes/received")
                            .param("size", "101"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(pokeService);
        }

        @Test
        @DisplayName("Boundary - page at max (1000)")
        void getReceivedPokes_PageAtMax() throws Exception {
            PageResponse<PokeResponse> pageResponse = PageResponse.<PokeResponse>builder()
                    .content(Collections.emptyList())
                    .page(1000)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(pokeService.getReceivedPokes(USER_ID, 1000, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/pokes/received")
                            .param("page", "1000"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Boundary - size at min (1)")
        void getReceivedPokes_SizeAtMin() throws Exception {
            PageResponse<PokeResponse> pageResponse = PageResponse.<PokeResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(1)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(pokeService.getReceivedPokes(USER_ID, 0, 1)).thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/pokes/received")
                            .param("size", "1"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Boundary - size at max (100)")
        void getReceivedPokes_SizeAtMax() throws Exception {
            PageResponse<PokeResponse> pageResponse = PageResponse.<PokeResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(100)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(pokeService.getReceivedPokes(USER_ID, 0, 100)).thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/pokes/received")
                            .param("size", "100"))
                    .andExpect(status().isOk());
        }
    }

    // ==================== GET SENT POKES ====================

    @Nested
    @DisplayName("GET /pokes/sent - getSentPokes")
    class GetSentPokesTests {

        @Test
        @DisplayName("Success - returns paginated sent pokes")
        void getSentPokes_Success() throws Exception {
            PageResponse<PokeResponse> pageResponse = PageResponse.<PokeResponse>builder()
                    .content(List.of(testPokeResponse))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .build();

            when(pokeService.getSentPokes(USER_ID, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/pokes/sent"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].pokerId").value(USER_ID.toString()));
        }

        @Test
        @DisplayName("Success - empty list")
        void getSentPokes_Empty() throws Exception {
            PageResponse<PokeResponse> pageResponse = PageResponse.<PokeResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(pokeService.getSentPokes(USER_ID, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/pokes/sent"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        @DisplayName("Custom pagination")
        void getSentPokes_CustomPagination() throws Exception {
            PageResponse<PokeResponse> pageResponse = PageResponse.<PokeResponse>builder()
                    .content(Collections.emptyList())
                    .page(3)
                    .size(25)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(pokeService.getSentPokes(USER_ID, 3, 25)).thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/pokes/sent")
                            .param("page", "3")
                            .param("size", "25"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Page above maximum - returns 400")
        void getSentPokes_PageAboveMax() throws Exception {
            mockMvc.perform(get("/api/v1/pokes/sent")
                            .param("page", "1001"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(pokeService);
        }

        @Test
        @DisplayName("Size below minimum - returns 400")
        void getSentPokes_SizeBelowMin() throws Exception {
            mockMvc.perform(get("/api/v1/pokes/sent")
                            .param("size", "0"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(pokeService);
        }
    }

    // ==================== GET ACTIVE POKES COUNT ====================

    @Nested
    @DisplayName("GET /pokes/count - getActivePokesCount")
    class GetActivePokesCountTests {

        @Test
        @DisplayName("Success - returns count")
        void getActivePokesCount_Success() throws Exception {
            when(pokeService.getActivePokesCount(USER_ID)).thenReturn(5L);

            mockMvc.perform(get("/api/v1/pokes/count"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("5"));
        }

        @Test
        @DisplayName("Success - returns zero")
        void getActivePokesCount_Zero() throws Exception {
            when(pokeService.getActivePokesCount(USER_ID)).thenReturn(0L);

            mockMvc.perform(get("/api/v1/pokes/count"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("0"));
        }

        @Test
        @DisplayName("Success - returns large count")
        void getActivePokesCount_LargeCount() throws Exception {
            when(pokeService.getActivePokesCount(USER_ID)).thenReturn(100L);

            mockMvc.perform(get("/api/v1/pokes/count"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("100"));
        }
    }

    // ==================== DISMISS POKE ====================

    @Nested
    @DisplayName("DELETE /pokes/{pokeId}/dismiss - dismissPoke")
    class DismissPokeTests {

        @Test
        @DisplayName("Success - returns 204")
        void dismissPoke_Success() throws Exception {
            doNothing().when(pokeService).dismissPoke(USER_ID, POKE_ID);

            mockMvc.perform(delete("/api/v1/pokes/{pokeId}/dismiss", POKE_ID))
                    .andExpect(status().isNoContent());

            verify(pokeService).dismissPoke(USER_ID, POKE_ID);
        }

        @Test
        @DisplayName("Poke not found - returns 400")
        void dismissPoke_PokeNotFound() throws Exception {
            doThrow(new ValidationException("Poke not found"))
                    .when(pokeService).dismissPoke(USER_ID, POKE_ID);

            mockMvc.perform(delete("/api/v1/pokes/{pokeId}/dismiss", POKE_ID))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Poke not found"));
        }

        @Test
        @DisplayName("Not the poked user - returns 400")
        void dismissPoke_NotThePokedUser() throws Exception {
            doThrow(new ValidationException("Only the poked user can dismiss"))
                    .when(pokeService).dismissPoke(USER_ID, POKE_ID);

            mockMvc.perform(delete("/api/v1/pokes/{pokeId}/dismiss", POKE_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Already dismissed - returns 400")
        void dismissPoke_AlreadyDismissed() throws Exception {
            doThrow(new ValidationException("Poke is already dismissed"))
                    .when(pokeService).dismissPoke(USER_ID, POKE_ID);

            mockMvc.perform(delete("/api/v1/pokes/{pokeId}/dismiss", POKE_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void dismissPoke_InvalidUuid() throws Exception {
            mockMvc.perform(delete("/api/v1/pokes/{pokeId}/dismiss", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(pokeService);
        }
    }
}