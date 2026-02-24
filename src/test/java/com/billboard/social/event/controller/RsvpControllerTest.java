package com.billboard.social.event.controller;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.dto.UserSummary;
import com.billboard.social.common.exception.ForbiddenException;
import com.billboard.social.common.exception.GlobalExceptionHandler;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.common.security.UserPrincipal;
import com.billboard.social.event.dto.request.EventRequests.AddCoHostRequest;
import com.billboard.social.event.dto.request.EventRequests.RsvpRequest;
import com.billboard.social.event.dto.response.EventResponses.CoHostResponse;
import com.billboard.social.event.dto.response.EventResponses.RsvpResponse;
import com.billboard.social.event.entity.enums.RsvpStatus;
import com.billboard.social.event.service.RsvpService;
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

@WebMvcTest(RsvpController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class RsvpControllerTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final UUID EVENT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID RSVP_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID COHOST_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RsvpService rsvpService;

    private RsvpResponse testRsvpResponse;
    private CoHostResponse testCoHostResponse;

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

        UserSummary userSummary = UserSummary.builder()
                .id(USER_ID)
                .username("testuser")
                .email("test@gmail.com")
                .build();

        testRsvpResponse = RsvpResponse.builder()
                .id(RSVP_ID)
                .eventId(EVENT_ID)
                .userId(USER_ID)
                .status(RsvpStatus.GOING)
                .guestCount(0)
                .notificationsEnabled(true)
                .respondedAt(LocalDateTime.now())
                .user(userSummary)
                .build();

        testCoHostResponse = CoHostResponse.builder()
                .id(COHOST_ID)
                .eventId(EVENT_ID)
                .userId(OTHER_USER_ID)
                .username("cohost")
                .displayName("Co Host")
                .addedAt(LocalDateTime.now())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ==================== RSVP ====================

    @Nested
    @DisplayName("POST /api/v1/events/{eventId}/rsvp - rsvp")
    class RsvpTests {

        @Test
        @DisplayName("Success - returns 201")
        void rsvp_Success() throws Exception {
            RsvpRequest request = RsvpRequest.builder()
                    .status(RsvpStatus.GOING)
                    .guestCount(2)
                    .note("Looking forward to it!")
                    .build();

            when(rsvpService.rsvp(eq(USER_ID), eq(EVENT_ID), any(RsvpRequest.class)))
                    .thenReturn(testRsvpResponse);

            mockMvc.perform(post("/api/v1/events/{eventId}/rsvp", EVENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(RSVP_ID.toString()))
                    .andExpect(jsonPath("$.status").value("GOING"));
        }

        @Test
        @DisplayName("Success - RSVP with MAYBE status")
        void rsvp_MaybeStatus() throws Exception {
            RsvpRequest request = RsvpRequest.builder()
                    .status(RsvpStatus.MAYBE)
                    .build();

            testRsvpResponse.setStatus(RsvpStatus.MAYBE);
            when(rsvpService.rsvp(eq(USER_ID), eq(EVENT_ID), any(RsvpRequest.class)))
                    .thenReturn(testRsvpResponse);

            mockMvc.perform(post("/api/v1/events/{eventId}/rsvp", EVENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("MAYBE"));
        }

        @Test
        @DisplayName("Success - RSVP with NOT_GOING status")
        void rsvp_NotGoingStatus() throws Exception {
            RsvpRequest request = RsvpRequest.builder()
                    .status(RsvpStatus.NOT_GOING)
                    .build();

            testRsvpResponse.setStatus(RsvpStatus.NOT_GOING);
            when(rsvpService.rsvp(eq(USER_ID), eq(EVENT_ID), any(RsvpRequest.class)))
                    .thenReturn(testRsvpResponse);

            mockMvc.perform(post("/api/v1/events/{eventId}/rsvp", EVENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("NOT_GOING"));
        }

        @Test
        @DisplayName("Missing status - returns 400")
        void rsvp_MissingStatus() throws Exception {
            RsvpRequest request = RsvpRequest.builder()
                    .guestCount(2)
                    .build();

            mockMvc.perform(post("/api/v1/events/{eventId}/rsvp", EVENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(rsvpService);
        }

        @Test
        @DisplayName("Guest count negative - returns 400")
        void rsvp_NegativeGuestCount() throws Exception {
            RsvpRequest request = RsvpRequest.builder()
                    .status(RsvpStatus.GOING)
                    .guestCount(-1)
                    .build();

            mockMvc.perform(post("/api/v1/events/{eventId}/rsvp", EVENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(rsvpService);
        }

        @Test
        @DisplayName("Guest count exceeds max - returns 400")
        void rsvp_GuestCountExceedsMax() throws Exception {
            RsvpRequest request = RsvpRequest.builder()
                    .status(RsvpStatus.GOING)
                    .guestCount(11)
                    .build();

            mockMvc.perform(post("/api/v1/events/{eventId}/rsvp", EVENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(rsvpService);
        }

        @Test
        @DisplayName("Note exceeds max length - returns 400")
        void rsvp_NoteTooLong() throws Exception {
            RsvpRequest request = RsvpRequest.builder()
                    .status(RsvpStatus.GOING)
                    .note("a".repeat(501))
                    .build();

            mockMvc.perform(post("/api/v1/events/{eventId}/rsvp", EVENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(rsvpService);
        }

        @Test
        @DisplayName("Invalid event ID - returns 400")
        void rsvp_InvalidEventId() throws Exception {
            RsvpRequest request = RsvpRequest.builder()
                    .status(RsvpStatus.GOING)
                    .build();

            mockMvc.perform(post("/api/v1/events/{eventId}/rsvp", "invalid-uuid")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(rsvpService);
        }

        @Test
        @DisplayName("Event not found - returns 400")
        void rsvp_EventNotFound() throws Exception {
            RsvpRequest request = RsvpRequest.builder()
                    .status(RsvpStatus.GOING)
                    .build();

            when(rsvpService.rsvp(eq(USER_ID), eq(EVENT_ID), any(RsvpRequest.class)))
                    .thenThrow(new ValidationException("Event not found"));

            mockMvc.perform(post("/api/v1/events/{eventId}/rsvp", EVENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Event at capacity - returns 400")
        void rsvp_EventAtCapacity() throws Exception {
            RsvpRequest request = RsvpRequest.builder()
                    .status(RsvpStatus.GOING)
                    .build();

            when(rsvpService.rsvp(eq(USER_ID), eq(EVENT_ID), any(RsvpRequest.class)))
                    .thenThrow(new ValidationException("Event is at capacity"));

            mockMvc.perform(post("/api/v1/events/{eventId}/rsvp", EVENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Missing request body - returns 400")
        void rsvp_MissingBody() throws Exception {
            mockMvc.perform(post("/api/v1/events/{eventId}/rsvp", EVENT_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(rsvpService);
        }

        @Test
        @DisplayName("Malformed JSON - returns 400")
        void rsvp_MalformedJson() throws Exception {
            mockMvc.perform(post("/api/v1/events/{eventId}/rsvp", EVENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": }"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(rsvpService);
        }
    }

    // ==================== CANCEL RSVP ====================

    @Nested
    @DisplayName("DELETE /api/v1/events/{eventId}/rsvp - cancelRsvp")
    class CancelRsvpTests {

        @Test
        @DisplayName("Success - returns 204")
        void cancelRsvp_Success() throws Exception {
            doNothing().when(rsvpService).cancelRsvp(USER_ID, EVENT_ID);

            mockMvc.perform(delete("/api/v1/events/{eventId}/rsvp", EVENT_ID))
                    .andExpect(status().isNoContent());

            verify(rsvpService).cancelRsvp(USER_ID, EVENT_ID);
        }

        @Test
        @DisplayName("RSVP not found - returns 400")
        void cancelRsvp_NotFound() throws Exception {
            doThrow(new ValidationException("RSVP not found"))
                    .when(rsvpService).cancelRsvp(USER_ID, EVENT_ID);

            mockMvc.perform(delete("/api/v1/events/{eventId}/rsvp", EVENT_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Host cannot cancel - returns 400")
        void cancelRsvp_HostCannotCancel() throws Exception {
            doThrow(new ValidationException("Host cannot cancel their RSVP"))
                    .when(rsvpService).cancelRsvp(USER_ID, EVENT_ID);

            mockMvc.perform(delete("/api/v1/events/{eventId}/rsvp", EVENT_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Invalid event ID - returns 400")
        void cancelRsvp_InvalidEventId() throws Exception {
            mockMvc.perform(delete("/api/v1/events/{eventId}/rsvp", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(rsvpService);
        }
    }

    // ==================== GET ATTENDEES ====================

    @Nested
    @DisplayName("GET /api/v1/events/{eventId}/rsvp/attendees - getAttendees")
    class GetAttendeesTests {

        @Test
        @DisplayName("Success - returns paginated attendees")
        void getAttendees_Success() throws Exception {
            PageResponse<RsvpResponse> pageResponse = PageResponse.<RsvpResponse>builder()
                    .content(List.of(testRsvpResponse))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .build();

            when(rsvpService.getAttendees(EVENT_ID, null, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/events/{eventId}/rsvp/attendees", EVENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("Success - filter by status GOING")
        void getAttendees_FilterByStatusGoing() throws Exception {
            PageResponse<RsvpResponse> pageResponse = PageResponse.<RsvpResponse>builder()
                    .content(List.of(testRsvpResponse))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .build();

            when(rsvpService.getAttendees(EVENT_ID, RsvpStatus.GOING, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/events/{eventId}/rsvp/attendees", EVENT_ID)
                            .param("status", "GOING"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].status").value("GOING"));
        }

        @Test
        @DisplayName("Success - filter by status MAYBE")
        void getAttendees_FilterByStatusMaybe() throws Exception {
            testRsvpResponse.setStatus(RsvpStatus.MAYBE);
            PageResponse<RsvpResponse> pageResponse = PageResponse.<RsvpResponse>builder()
                    .content(List.of(testRsvpResponse))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .build();

            when(rsvpService.getAttendees(EVENT_ID, RsvpStatus.MAYBE, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/events/{eventId}/rsvp/attendees", EVENT_ID)
                            .param("status", "MAYBE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].status").value("MAYBE"));
        }

        @Test
        @DisplayName("Success - filter by status NOT_GOING")
        void getAttendees_FilterByStatusNotGoing() throws Exception {
            testRsvpResponse.setStatus(RsvpStatus.NOT_GOING);
            PageResponse<RsvpResponse> pageResponse = PageResponse.<RsvpResponse>builder()
                    .content(List.of(testRsvpResponse))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .build();

            when(rsvpService.getAttendees(EVENT_ID, RsvpStatus.NOT_GOING, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/events/{eventId}/rsvp/attendees", EVENT_ID)
                            .param("status", "NOT_GOING"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].status").value("NOT_GOING"));
        }

        @Test
        @DisplayName("Success - custom pagination")
        void getAttendees_CustomPagination() throws Exception {
            PageResponse<RsvpResponse> pageResponse = PageResponse.<RsvpResponse>builder()
                    .content(Collections.emptyList())
                    .page(5)
                    .size(10)
                    .totalElements(100)
                    .totalPages(10)
                    .build();

            when(rsvpService.getAttendees(EVENT_ID, null, 5, 10)).thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/events/{eventId}/rsvp/attendees", EVENT_ID)
                            .param("page", "5")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(5))
                    .andExpect(jsonPath("$.size").value(10));
        }

        @Test
        @DisplayName("Success - empty result")
        void getAttendees_Empty() throws Exception {
            PageResponse<RsvpResponse> pageResponse = PageResponse.<RsvpResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(rsvpService.getAttendees(EVENT_ID, null, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/events/{eventId}/rsvp/attendees", EVENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        @DisplayName("Page below minimum - returns 400")
        void getAttendees_PageBelowMin() throws Exception {
            mockMvc.perform(get("/api/v1/events/{eventId}/rsvp/attendees", EVENT_ID)
                            .param("page", "-1"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(rsvpService);
        }

        @Test
        @DisplayName("Page above maximum - returns 400")
        void getAttendees_PageAboveMax() throws Exception {
            mockMvc.perform(get("/api/v1/events/{eventId}/rsvp/attendees", EVENT_ID)
                            .param("page", "1001"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(rsvpService);
        }

        @Test
        @DisplayName("Size below minimum - returns 400")
        void getAttendees_SizeBelowMin() throws Exception {
            mockMvc.perform(get("/api/v1/events/{eventId}/rsvp/attendees", EVENT_ID)
                            .param("size", "0"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(rsvpService);
        }

        @Test
        @DisplayName("Size above maximum - returns 400")
        void getAttendees_SizeAboveMax() throws Exception {
            mockMvc.perform(get("/api/v1/events/{eventId}/rsvp/attendees", EVENT_ID)
                            .param("size", "101"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(rsvpService);
        }

        @Test
        @DisplayName("Invalid event ID - returns 400")
        void getAttendees_InvalidEventId() throws Exception {
            mockMvc.perform(get("/api/v1/events/{eventId}/rsvp/attendees", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(rsvpService);
        }

        @Test
        @DisplayName("Boundary - page at max (1000)")
        void getAttendees_PageAtMax() throws Exception {
            PageResponse<RsvpResponse> pageResponse = PageResponse.<RsvpResponse>builder()
                    .content(Collections.emptyList())
                    .page(1000)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(rsvpService.getAttendees(EVENT_ID, null, 1000, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/events/{eventId}/rsvp/attendees", EVENT_ID)
                            .param("page", "1000"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Boundary - size at max (100)")
        void getAttendees_SizeAtMax() throws Exception {
            PageResponse<RsvpResponse> pageResponse = PageResponse.<RsvpResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(100)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(rsvpService.getAttendees(EVENT_ID, null, 0, 100)).thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/events/{eventId}/rsvp/attendees", EVENT_ID)
                            .param("size", "100"))
                    .andExpect(status().isOk());
        }
    }

    // ==================== GET GOING ATTENDEES ====================

    @Nested
    @DisplayName("GET /api/v1/events/{eventId}/rsvp/going - getGoingAttendees")
    class GetGoingAttendeesTests {

        @Test
        @DisplayName("Success - returns going attendees")
        void getGoingAttendees_Success() throws Exception {
            PageResponse<RsvpResponse> pageResponse = PageResponse.<RsvpResponse>builder()
                    .content(List.of(testRsvpResponse))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .build();

            when(rsvpService.getGoingAttendees(EVENT_ID, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/events/{eventId}/rsvp/going", EVENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)));
        }

        @Test
        @DisplayName("Success - empty result")
        void getGoingAttendees_Empty() throws Exception {
            PageResponse<RsvpResponse> pageResponse = PageResponse.<RsvpResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(rsvpService.getGoingAttendees(EVENT_ID, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/events/{eventId}/rsvp/going", EVENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        @DisplayName("Custom pagination")
        void getGoingAttendees_CustomPagination() throws Exception {
            PageResponse<RsvpResponse> pageResponse = PageResponse.<RsvpResponse>builder()
                    .content(Collections.emptyList())
                    .page(2)
                    .size(50)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(rsvpService.getGoingAttendees(EVENT_ID, 2, 50)).thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/events/{eventId}/rsvp/going", EVENT_ID)
                            .param("page", "2")
                            .param("size", "50"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Page above maximum - returns 400")
        void getGoingAttendees_PageAboveMax() throws Exception {
            mockMvc.perform(get("/api/v1/events/{eventId}/rsvp/going", EVENT_ID)
                            .param("page", "1001"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(rsvpService);
        }

        @Test
        @DisplayName("Size below minimum - returns 400")
        void getGoingAttendees_SizeBelowMin() throws Exception {
            mockMvc.perform(get("/api/v1/events/{eventId}/rsvp/going", EVENT_ID)
                            .param("size", "0"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(rsvpService);
        }

        @Test
        @DisplayName("Invalid event ID - returns 400")
        void getGoingAttendees_InvalidEventId() throws Exception {
            mockMvc.perform(get("/api/v1/events/{eventId}/rsvp/going", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(rsvpService);
        }
    }

    // ==================== GET CHECKED-IN ATTENDEES ====================

    @Nested
    @DisplayName("GET /api/v1/events/{eventId}/rsvp/checked-in - getCheckedInAttendees")
    class GetCheckedInAttendeesTests {

        @Test
        @DisplayName("Success - returns checked-in attendees")
        void getCheckedInAttendees_Success() throws Exception {
            testRsvpResponse.setCheckedInAt(LocalDateTime.now());
            PageResponse<RsvpResponse> pageResponse = PageResponse.<RsvpResponse>builder()
                    .content(List.of(testRsvpResponse))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .build();

            when(rsvpService.getCheckedInAttendees(EVENT_ID, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/events/{eventId}/rsvp/checked-in", EVENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)));
        }

        @Test
        @DisplayName("Success - empty result")
        void getCheckedInAttendees_Empty() throws Exception {
            PageResponse<RsvpResponse> pageResponse = PageResponse.<RsvpResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(rsvpService.getCheckedInAttendees(EVENT_ID, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/events/{eventId}/rsvp/checked-in", EVENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        @DisplayName("Custom pagination")
        void getCheckedInAttendees_CustomPagination() throws Exception {
            PageResponse<RsvpResponse> pageResponse = PageResponse.<RsvpResponse>builder()
                    .content(Collections.emptyList())
                    .page(3)
                    .size(25)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(rsvpService.getCheckedInAttendees(EVENT_ID, 3, 25)).thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/events/{eventId}/rsvp/checked-in", EVENT_ID)
                            .param("page", "3")
                            .param("size", "25"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Size above maximum - returns 400")
        void getCheckedInAttendees_SizeAboveMax() throws Exception {
            mockMvc.perform(get("/api/v1/events/{eventId}/rsvp/checked-in", EVENT_ID)
                            .param("size", "101"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(rsvpService);
        }

        @Test
        @DisplayName("Invalid event ID - returns 400")
        void getCheckedInAttendees_InvalidEventId() throws Exception {
            mockMvc.perform(get("/api/v1/events/{eventId}/rsvp/checked-in", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(rsvpService);
        }
    }

    // ==================== GET GOING USER IDS ====================

    @Nested
    @DisplayName("GET /api/v1/events/{eventId}/rsvp/going/ids - getGoingUserIds")
    class GetGoingUserIdsTests {

        @Test
        @DisplayName("Success - returns user IDs")
        void getGoingUserIds_Success() throws Exception {
            List<Long> userIds = List.of(USER_ID, OTHER_USER_ID);

            when(rsvpService.getGoingUserIds(EVENT_ID)).thenReturn(userIds);

            mockMvc.perform(get("/api/v1/events/{eventId}/rsvp/going/ids", EVENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0]").value(USER_ID.toString()))
                    .andExpect(jsonPath("$[1]").value(OTHER_USER_ID.toString()));
        }

        @Test
        @DisplayName("Success - empty list")
        void getGoingUserIds_Empty() throws Exception {
            when(rsvpService.getGoingUserIds(EVENT_ID)).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/v1/events/{eventId}/rsvp/going/ids", EVENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("Invalid event ID - returns 400")
        void getGoingUserIds_InvalidEventId() throws Exception {
            mockMvc.perform(get("/api/v1/events/{eventId}/rsvp/going/ids", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(rsvpService);
        }

        @Test
        @DisplayName("Event not found - returns 400")
        void getGoingUserIds_EventNotFound() throws Exception {
            when(rsvpService.getGoingUserIds(EVENT_ID))
                    .thenThrow(new ValidationException("Event not found"));

            mockMvc.perform(get("/api/v1/events/{eventId}/rsvp/going/ids", EVENT_ID))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== CHECK-IN ====================

    @Nested
    @DisplayName("POST /api/v1/events/{eventId}/rsvp/{userId}/check-in - checkIn")
    class CheckInTests {

        @Test
        @DisplayName("Success - returns 200")
        void checkIn_Success() throws Exception {
            testRsvpResponse.setCheckedInAt(LocalDateTime.now());

            when(rsvpService.checkIn(USER_ID, EVENT_ID, OTHER_USER_ID)).thenReturn(testRsvpResponse);

            mockMvc.perform(post("/api/v1/events/{eventId}/rsvp/{userId}/check-in", EVENT_ID, OTHER_USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.checkedInAt").isNotEmpty());

            verify(rsvpService).checkIn(USER_ID, EVENT_ID, OTHER_USER_ID);
        }

        @Test
        @DisplayName("RSVP not found - returns 400")
        void checkIn_RsvpNotFound() throws Exception {
            when(rsvpService.checkIn(USER_ID, EVENT_ID, OTHER_USER_ID))
                    .thenThrow(new ValidationException("RSVP not found"));

            mockMvc.perform(post("/api/v1/events/{eventId}/rsvp/{userId}/check-in", EVENT_ID, OTHER_USER_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("User not going - returns 400")
        void checkIn_UserNotGoing() throws Exception {
            when(rsvpService.checkIn(USER_ID, EVENT_ID, OTHER_USER_ID))
                    .thenThrow(new ValidationException("User is not marked as GOING"));

            mockMvc.perform(post("/api/v1/events/{eventId}/rsvp/{userId}/check-in", EVENT_ID, OTHER_USER_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("No permission - returns 403")
        void checkIn_NoPermission() throws Exception {
            when(rsvpService.checkIn(USER_ID, EVENT_ID, OTHER_USER_ID))
                    .thenThrow(new ForbiddenException("No permission to manage RSVPs"));

            mockMvc.perform(post("/api/v1/events/{eventId}/rsvp/{userId}/check-in", EVENT_ID, OTHER_USER_ID))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Invalid event ID - returns 400")
        void checkIn_InvalidEventId() throws Exception {
            mockMvc.perform(post("/api/v1/events/{eventId}/rsvp/{userId}/check-in", "invalid-uuid", OTHER_USER_ID))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(rsvpService);
        }

        @Test
        @DisplayName("Invalid user ID - returns 400")
        void checkIn_InvalidUserId() throws Exception {
            mockMvc.perform(post("/api/v1/events/{eventId}/rsvp/{userId}/check-in", EVENT_ID, "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(rsvpService);
        }
    }

    // ==================== UNDO CHECK-IN ====================

    @Nested
    @DisplayName("DELETE /api/v1/events/{eventId}/rsvp/{userId}/check-in - undoCheckIn")
    class UndoCheckInTests {

        @Test
        @DisplayName("Success - returns 200")
        void undoCheckIn_Success() throws Exception {
            testRsvpResponse.setCheckedInAt(null);

            when(rsvpService.undoCheckIn(USER_ID, EVENT_ID, OTHER_USER_ID)).thenReturn(testRsvpResponse);

            mockMvc.perform(delete("/api/v1/events/{eventId}/rsvp/{userId}/check-in", EVENT_ID, OTHER_USER_ID))
                    .andExpect(status().isOk());

            verify(rsvpService).undoCheckIn(USER_ID, EVENT_ID, OTHER_USER_ID);
        }

        @Test
        @DisplayName("RSVP not found - returns 400")
        void undoCheckIn_RsvpNotFound() throws Exception {
            when(rsvpService.undoCheckIn(USER_ID, EVENT_ID, OTHER_USER_ID))
                    .thenThrow(new ValidationException("RSVP not found"));

            mockMvc.perform(delete("/api/v1/events/{eventId}/rsvp/{userId}/check-in", EVENT_ID, OTHER_USER_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("User not checked in - returns 400")
        void undoCheckIn_UserNotCheckedIn() throws Exception {
            when(rsvpService.undoCheckIn(USER_ID, EVENT_ID, OTHER_USER_ID))
                    .thenThrow(new ValidationException("User is not checked in"));

            mockMvc.perform(delete("/api/v1/events/{eventId}/rsvp/{userId}/check-in", EVENT_ID, OTHER_USER_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("No permission - returns 403")
        void undoCheckIn_NoPermission() throws Exception {
            when(rsvpService.undoCheckIn(USER_ID, EVENT_ID, OTHER_USER_ID))
                    .thenThrow(new ForbiddenException("No permission to manage RSVPs"));

            mockMvc.perform(delete("/api/v1/events/{eventId}/rsvp/{userId}/check-in", EVENT_ID, OTHER_USER_ID))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Invalid event ID - returns 400")
        void undoCheckIn_InvalidEventId() throws Exception {
            mockMvc.perform(delete("/api/v1/events/{eventId}/rsvp/{userId}/check-in", "invalid-uuid", OTHER_USER_ID))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(rsvpService);
        }

        @Test
        @DisplayName("Invalid user ID - returns 400")
        void undoCheckIn_InvalidUserId() throws Exception {
            mockMvc.perform(delete("/api/v1/events/{eventId}/rsvp/{userId}/check-in", EVENT_ID, "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(rsvpService);
        }
    }

    // ==================== MY RSVP STATUS ====================

    @Nested
    @DisplayName("GET /api/v1/events/{eventId}/rsvp/my-status - getMyRsvpStatus")
    class GetMyRsvpStatusTests {

        @Test
        @DisplayName("Success - returns RSVP status")
        void getMyRsvpStatus_Success() throws Exception {
            when(rsvpService.getMyRsvpStatus(USER_ID, EVENT_ID)).thenReturn(testRsvpResponse);

            mockMvc.perform(get("/api/v1/events/{eventId}/rsvp/my-status", EVENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                    .andExpect(jsonPath("$.status").value("GOING"));
        }

        @Test
        @DisplayName("RSVP not found - returns 400")
        void getMyRsvpStatus_NotFound() throws Exception {
            when(rsvpService.getMyRsvpStatus(USER_ID, EVENT_ID))
                    .thenThrow(new ValidationException("RSVP not found"));

            mockMvc.perform(get("/api/v1/events/{eventId}/rsvp/my-status", EVENT_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Invalid event ID - returns 400")
        void getMyRsvpStatus_InvalidEventId() throws Exception {
            mockMvc.perform(get("/api/v1/events/{eventId}/rsvp/my-status", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(rsvpService);
        }
    }

    // ==================== ADD CO-HOST ====================

    @Nested
    @DisplayName("POST /api/v1/events/{eventId}/rsvp/co-hosts - addCoHost")
    class AddCoHostTests {

        @Test
        @DisplayName("Success - returns 201")
        void addCoHost_Success() throws Exception {
            AddCoHostRequest request = AddCoHostRequest.builder()
                    .userId(OTHER_USER_ID)
                    .build();

            when(rsvpService.addCoHost(eq(USER_ID), eq(EVENT_ID), any(AddCoHostRequest.class)))
                    .thenReturn(testCoHostResponse);

            mockMvc.perform(post("/api/v1/events/{eventId}/rsvp/co-hosts", EVENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(COHOST_ID.toString()))
                    .andExpect(jsonPath("$.userId").value(OTHER_USER_ID.toString()));
        }

        @Test
        @DisplayName("Missing userId - returns 400")
        void addCoHost_MissingUserId() throws Exception {
            AddCoHostRequest request = AddCoHostRequest.builder().build();

            mockMvc.perform(post("/api/v1/events/{eventId}/rsvp/co-hosts", EVENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(rsvpService);
        }

        @Test
        @DisplayName("User already co-host - returns 400")
        void addCoHost_AlreadyCoHost() throws Exception {
            AddCoHostRequest request = AddCoHostRequest.builder()
                    .userId(OTHER_USER_ID)
                    .build();

            when(rsvpService.addCoHost(eq(USER_ID), eq(EVENT_ID), any(AddCoHostRequest.class)))
                    .thenThrow(new ValidationException("User is already a co-host"));

            mockMvc.perform(post("/api/v1/events/{eventId}/rsvp/co-hosts", EVENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Not host - returns 403")
        void addCoHost_NotHost() throws Exception {
            AddCoHostRequest request = AddCoHostRequest.builder()
                    .userId(OTHER_USER_ID)
                    .build();

            when(rsvpService.addCoHost(eq(USER_ID), eq(EVENT_ID), any(AddCoHostRequest.class)))
                    .thenThrow(new ForbiddenException("Only host can manage co-hosts"));

            mockMvc.perform(post("/api/v1/events/{eventId}/rsvp/co-hosts", EVENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Invalid event ID - returns 400")
        void addCoHost_InvalidEventId() throws Exception {
            AddCoHostRequest request = AddCoHostRequest.builder()
                    .userId(OTHER_USER_ID)
                    .build();

            mockMvc.perform(post("/api/v1/events/{eventId}/rsvp/co-hosts", "invalid-uuid")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(rsvpService);
        }

        @Test
        @DisplayName("Missing request body - returns 400")
        void addCoHost_MissingBody() throws Exception {
            mockMvc.perform(post("/api/v1/events/{eventId}/rsvp/co-hosts", EVENT_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(rsvpService);
        }

        @Test
        @DisplayName("Malformed JSON - returns 400")
        void addCoHost_MalformedJson() throws Exception {
            mockMvc.perform(post("/api/v1/events/{eventId}/rsvp/co-hosts", EVENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"userId\": }"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(rsvpService);
        }
    }

    // ==================== REMOVE CO-HOST ====================

    @Nested
    @DisplayName("DELETE /api/v1/events/{eventId}/rsvp/co-hosts/{userId} - removeCoHost")
    class RemoveCoHostTests {

        @Test
        @DisplayName("Success - returns 204")
        void removeCoHost_Success() throws Exception {
            doNothing().when(rsvpService).removeCoHost(USER_ID, EVENT_ID, OTHER_USER_ID);

            mockMvc.perform(delete("/api/v1/events/{eventId}/rsvp/co-hosts/{userId}", EVENT_ID, OTHER_USER_ID))
                    .andExpect(status().isNoContent());

            verify(rsvpService).removeCoHost(USER_ID, EVENT_ID, OTHER_USER_ID);
        }

        @Test
        @DisplayName("Co-host not found - returns 400")
        void removeCoHost_NotFound() throws Exception {
            doThrow(new ValidationException("Co-host not found"))
                    .when(rsvpService).removeCoHost(USER_ID, EVENT_ID, OTHER_USER_ID);

            mockMvc.perform(delete("/api/v1/events/{eventId}/rsvp/co-hosts/{userId}", EVENT_ID, OTHER_USER_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Not host - returns 403")
        void removeCoHost_NotHost() throws Exception {
            doThrow(new ForbiddenException("Only host can manage co-hosts"))
                    .when(rsvpService).removeCoHost(USER_ID, EVENT_ID, OTHER_USER_ID);

            mockMvc.perform(delete("/api/v1/events/{eventId}/rsvp/co-hosts/{userId}", EVENT_ID, OTHER_USER_ID))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Invalid event ID - returns 400")
        void removeCoHost_InvalidEventId() throws Exception {
            mockMvc.perform(delete("/api/v1/events/{eventId}/rsvp/co-hosts/{userId}", "invalid-uuid", OTHER_USER_ID))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(rsvpService);
        }

        @Test
        @DisplayName("Invalid user ID - returns 400")
        void removeCoHost_InvalidUserId() throws Exception {
            mockMvc.perform(delete("/api/v1/events/{eventId}/rsvp/co-hosts/{userId}", EVENT_ID, "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(rsvpService);
        }
    }

    // ==================== GET CO-HOSTS ====================

    @Nested
    @DisplayName("GET /api/v1/events/{eventId}/rsvp/co-hosts - getCoHosts")
    class GetCoHostsTests {

        @Test
        @DisplayName("Success - returns co-hosts")
        void getCoHosts_Success() throws Exception {
            when(rsvpService.getCoHosts(EVENT_ID)).thenReturn(List.of(testCoHostResponse));

            mockMvc.perform(get("/api/v1/events/{eventId}/rsvp/co-hosts", EVENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id").value(COHOST_ID.toString()));
        }

        @Test
        @DisplayName("Success - empty list")
        void getCoHosts_Empty() throws Exception {
            when(rsvpService.getCoHosts(EVENT_ID)).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/v1/events/{eventId}/rsvp/co-hosts", EVENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("Success - multiple co-hosts")
        void getCoHosts_Multiple() throws Exception {
            CoHostResponse secondCoHost = CoHostResponse.builder()
                    .id(UUID.randomUUID())
                    .eventId(EVENT_ID)
                    .userId(USER_ID)
                    .username("cohost2")
                    .build();

            when(rsvpService.getCoHosts(EVENT_ID)).thenReturn(List.of(testCoHostResponse, secondCoHost));

            mockMvc.perform(get("/api/v1/events/{eventId}/rsvp/co-hosts", EVENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)));
        }

        @Test
        @DisplayName("Event not found - returns 400")
        void getCoHosts_EventNotFound() throws Exception {
            when(rsvpService.getCoHosts(EVENT_ID))
                    .thenThrow(new ValidationException("Event not found"));

            mockMvc.perform(get("/api/v1/events/{eventId}/rsvp/co-hosts", EVENT_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Invalid event ID - returns 400")
        void getCoHosts_InvalidEventId() throws Exception {
            mockMvc.perform(get("/api/v1/events/{eventId}/rsvp/co-hosts", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(rsvpService);
        }
    }
}