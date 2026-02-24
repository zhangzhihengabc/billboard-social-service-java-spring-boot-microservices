package com.billboard.social.event.controller;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.exception.ForbiddenException;
import com.billboard.social.common.exception.GlobalExceptionHandler;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.common.security.JwtAuthenticationFilter;
import com.billboard.social.common.security.UserPrincipal;
import com.billboard.social.event.dto.request.EventRequests.CreateEventRequest;
import com.billboard.social.event.dto.request.EventRequests.UpdateEventRequest;
import com.billboard.social.event.dto.response.EventResponses.EventResponse;
import com.billboard.social.event.dto.response.EventResponses.EventSummaryResponse;
import com.billboard.social.event.entity.enums.EventStatus;
import com.billboard.social.event.entity.enums.EventType;
import com.billboard.social.event.entity.enums.EventVisibility;
import com.billboard.social.event.service.EventService;
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
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EventController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EventService eventService;

    // Test constants
    private static final Long USER_ID = 1L;
    private static final UUID EVENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CATEGORY_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private UserPrincipal userPrincipal;
    private EventResponse testEventResponse;
    private EventSummaryResponse testEventSummary;

    @BeforeEach
    void setUp() {
        userPrincipal = UserPrincipal.builder()
                .id(USER_ID)
                .username("testuser")
                .email("test@example.com")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();

        testEventResponse = EventResponse.builder()
                .id(EVENT_ID)
                .title("Test Event")
                .slug("test-event")
                .description("A test event description")
                .startTime(LocalDateTime.now().plusDays(7))
                .endTime(LocalDateTime.now().plusDays(7).plusHours(2))
                .venueName("Test Venue")
                .city("Test City")
                .eventType(EventType.IN_PERSON)
                .maxAttendees(100)
                .goingCount(10)
                .maybeCount(5)
                .invitedCount(20)
                .status(EventStatus.PUBLISHED)
                .visibility(EventVisibility.PUBLIC)
                .hostId(USER_ID)
                .categoryId(CATEGORY_ID)
                .categoryName("Conference")
                .isTicketed(false)
                .allowGuests(true)
                .showGuestList(true)
                .acceptingRsvps(true)
                .createdAt(LocalDateTime.now())
                .build();

        testEventSummary = EventSummaryResponse.builder()
                .id(EVENT_ID)
                .title("Test Event")
                .slug("test-event")
                .startTime(LocalDateTime.now().plusDays(7))
                .endTime(LocalDateTime.now().plusDays(7).plusHours(2))
                .venueName("Test Venue")
                .city("Test City")
                .eventType(EventType.IN_PERSON)
                .goingCount(10)
                .isTicketed(false)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setAuthentication(UserPrincipal principal) {
        if (principal != null) {
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(auth);
        } else {
            SecurityContextHolder.clearContext();
        }
    }

    // ==================== CREATE EVENT ====================

    @Nested
    @DisplayName("POST /api/v1/events - createEvent")
    class CreateEventTests {

        @Test
        @DisplayName("Success - returns 201")
        void createEvent_Success() throws Exception {
            setAuthentication(userPrincipal);

            CreateEventRequest request = CreateEventRequest.builder()
                    .title("New Event")
                    .description("Event description")
                    .startTime(LocalDateTime.now().plusDays(7))
                    .endTime(LocalDateTime.now().plusDays(7).plusHours(2))
                    .venueName("Event Venue")
                    .city("Event City")
                    .categoryId(CATEGORY_ID)
                    .visibility(EventVisibility.PUBLIC)
                    .maxAttendees(50)
                    .build();

            when(eventService.createEvent(eq(USER_ID), any(CreateEventRequest.class)))
                    .thenReturn(testEventResponse);

            mockMvc.perform(post("/api/v1/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(EVENT_ID.toString()))
                    .andExpect(jsonPath("$.title").value("Test Event"));

            verify(eventService).createEvent(eq(USER_ID), any(CreateEventRequest.class));
        }

        @Test
        @DisplayName("Missing required field - returns 400")
        void createEvent_MissingTitle() throws Exception {
            setAuthentication(userPrincipal);

            CreateEventRequest request = CreateEventRequest.builder()
                    .description("Event description")
                    // Missing title
                    .build();

            mockMvc.perform(post("/api/v1/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(eventService);
        }

        @Test
        @DisplayName("Invalid JSON - returns 400")
        void createEvent_InvalidJson() throws Exception {
            setAuthentication(userPrincipal);

            mockMvc.perform(post("/api/v1/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\": }"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(eventService);
        }

        @Test
        @DisplayName("Service throws ValidationException - returns 400")
        void createEvent_ValidationException() throws Exception {
            setAuthentication(userPrincipal);

            CreateEventRequest request = CreateEventRequest.builder()
                    .title("New Event")
                    .startTime(LocalDateTime.now().plusDays(7))
                    .build();

            when(eventService.createEvent(eq(USER_ID), any(CreateEventRequest.class)))
                    .thenThrow(new ValidationException("Start time must be before end time"));

            mockMvc.perform(post("/api/v1/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Start time must be before end time"));
        }
    }

    // ==================== UPDATE EVENT ====================

    @Nested
    @DisplayName("PUT /api/v1/events/{eventId} - updateEvent")
    class UpdateEventTests {

        @Test
        @DisplayName("Success - returns 200")
        void updateEvent_Success() throws Exception {
            setAuthentication(userPrincipal);

            UpdateEventRequest request = UpdateEventRequest.builder()
                    .title("Updated Event Title")
                    .description("Updated description")
                    .build();

            EventResponse updatedResponse = EventResponse.builder()
                    .id(EVENT_ID)
                    .title("Updated Event Title")
                    .description("Updated description")
                    .build();

            when(eventService.updateEvent(eq(USER_ID), eq(EVENT_ID), any(UpdateEventRequest.class)))
                    .thenReturn(updatedResponse);

            mockMvc.perform(put("/api/v1/events/{eventId}", EVENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("Updated Event Title"));

            verify(eventService).updateEvent(eq(USER_ID), eq(EVENT_ID), any(UpdateEventRequest.class));
        }

        @Test
        @DisplayName("Event not found - returns 400")
        void updateEvent_NotFound() throws Exception {
            setAuthentication(userPrincipal);

            UpdateEventRequest request = UpdateEventRequest.builder()
                    .title("Updated Title")
                    .build();

            when(eventService.updateEvent(eq(USER_ID), eq(EVENT_ID), any(UpdateEventRequest.class)))
                    .thenThrow(new ValidationException("Event not found"));

            mockMvc.perform(put("/api/v1/events/{eventId}", EVENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Event not found"));
        }

        @Test
        @DisplayName("No edit permission - returns 403")
        void updateEvent_Forbidden() throws Exception {
            setAuthentication(userPrincipal);

            UpdateEventRequest request = UpdateEventRequest.builder()
                    .title("Updated Title")
                    .build();

            when(eventService.updateEvent(eq(USER_ID), eq(EVENT_ID), any(UpdateEventRequest.class)))
                    .thenThrow(new ForbiddenException("You don't have permission to edit this event"));

            mockMvc.perform(put("/api/v1/events/{eventId}", EVENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void updateEvent_InvalidUuid() throws Exception {
            setAuthentication(userPrincipal);

            UpdateEventRequest request = UpdateEventRequest.builder()
                    .title("Updated Title")
                    .build();

            mockMvc.perform(put("/api/v1/events/{eventId}", "invalid-uuid")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(eventService);
        }
    }

    // ==================== DELETE EVENT ====================

    @Nested
    @DisplayName("DELETE /api/v1/events/{eventId} - deleteEvent")
    class DeleteEventTests {

        @Test
        @DisplayName("Success - returns 204")
        void deleteEvent_Success() throws Exception {
            setAuthentication(userPrincipal);

            doNothing().when(eventService).deleteEvent(USER_ID, EVENT_ID);

            mockMvc.perform(delete("/api/v1/events/{eventId}", EVENT_ID))
                    .andExpect(status().isNoContent());

            verify(eventService).deleteEvent(USER_ID, EVENT_ID);
        }

        @Test
        @DisplayName("Event not found - returns 400")
        void deleteEvent_NotFound() throws Exception {
            setAuthentication(userPrincipal);

            doThrow(new ValidationException("Event not found"))
                    .when(eventService).deleteEvent(USER_ID, EVENT_ID);

            mockMvc.perform(delete("/api/v1/events/{eventId}", EVENT_ID))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Event not found"));
        }

        @Test
        @DisplayName("Not host - returns 403")
        void deleteEvent_Forbidden() throws Exception {
            setAuthentication(userPrincipal);

            doThrow(new ForbiddenException("Only host can delete this event"))
                    .when(eventService).deleteEvent(USER_ID, EVENT_ID);

            mockMvc.perform(delete("/api/v1/events/{eventId}", EVENT_ID))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void deleteEvent_InvalidUuid() throws Exception {
            setAuthentication(userPrincipal);

            mockMvc.perform(delete("/api/v1/events/{eventId}", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(eventService);
        }
    }

    // ==================== GET EVENT ====================

    @Nested
    @DisplayName("GET /api/v1/events/{eventId} - getEvent")
    class GetEventTests {

        @Test
        @DisplayName("Success - authenticated user")
        void getEvent_Authenticated() throws Exception {
            setAuthentication(userPrincipal);

            when(eventService.getEvent(EVENT_ID, USER_ID)).thenReturn(testEventResponse);

            mockMvc.perform(get("/api/v1/events/{eventId}", EVENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(EVENT_ID.toString()))
                    .andExpect(jsonPath("$.title").value("Test Event"));

            verify(eventService).getEvent(EVENT_ID, USER_ID);
        }

        @Test
        @DisplayName("Success - anonymous user (principal is null)")
        void getEvent_Anonymous() throws Exception {
            setAuthentication(null);

            when(eventService.getEvent(EVENT_ID, null)).thenReturn(testEventResponse);

            mockMvc.perform(get("/api/v1/events/{eventId}", EVENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(EVENT_ID.toString()));

            verify(eventService).getEvent(EVENT_ID, null);
        }

        @Test
        @DisplayName("Event not found - returns 400")
        void getEvent_NotFound() throws Exception {
            setAuthentication(userPrincipal);

            when(eventService.getEvent(EVENT_ID, USER_ID))
                    .thenThrow(new ValidationException("Event not found"));

            mockMvc.perform(get("/api/v1/events/{eventId}", EVENT_ID))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Event not found"));
        }

        @Test
        @DisplayName("No view permission - returns 403")
        void getEvent_Forbidden() throws Exception {
            setAuthentication(userPrincipal);

            when(eventService.getEvent(EVENT_ID, USER_ID))
                    .thenThrow(new ForbiddenException("You don't have permission to view this event"));

            mockMvc.perform(get("/api/v1/events/{eventId}", EVENT_ID))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void getEvent_InvalidUuid() throws Exception {
            setAuthentication(userPrincipal);

            mockMvc.perform(get("/api/v1/events/{eventId}", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(eventService);
        }
    }

    // ==================== GET EVENT BY SLUG ====================

    @Nested
    @DisplayName("GET /api/v1/events/slug/{slug} - getEventBySlug")
    class GetEventBySlugTests {

        @Test
        @DisplayName("Success - authenticated user")
        void getEventBySlug_Authenticated() throws Exception {
            setAuthentication(userPrincipal);

            when(eventService.getEventBySlug("test-event", USER_ID)).thenReturn(testEventResponse);

            mockMvc.perform(get("/api/v1/events/slug/{slug}", "test-event"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.slug").value("test-event"));

            verify(eventService).getEventBySlug("test-event", USER_ID);
        }

        @Test
        @DisplayName("Success - anonymous user (principal is null)")
        void getEventBySlug_Anonymous() throws Exception {
            setAuthentication(null);

            when(eventService.getEventBySlug("test-event", null)).thenReturn(testEventResponse);

            mockMvc.perform(get("/api/v1/events/slug/{slug}", "test-event"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.slug").value("test-event"));

            verify(eventService).getEventBySlug("test-event", null);
        }

        @Test
        @DisplayName("Event not found - returns 400")
        void getEventBySlug_NotFound() throws Exception {
            setAuthentication(userPrincipal);

            when(eventService.getEventBySlug("nonexistent", USER_ID))
                    .thenThrow(new ValidationException("Event not found"));

            mockMvc.perform(get("/api/v1/events/slug/{slug}", "nonexistent"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Event not found"));
        }

        @Test
        @DisplayName("No view permission - returns 403")
        void getEventBySlug_Forbidden() throws Exception {
            setAuthentication(userPrincipal);

            when(eventService.getEventBySlug("private-event", USER_ID))
                    .thenThrow(new ForbiddenException("You don't have permission to view this event"));

            mockMvc.perform(get("/api/v1/events/slug/{slug}", "private-event"))
                    .andExpect(status().isForbidden());
        }
    }

    // ==================== PUBLISH EVENT ====================

    @Nested
    @DisplayName("POST /api/v1/events/{eventId}/publish - publishEvent")
    class PublishEventTests {

        @Test
        @DisplayName("Success - returns 200")
        void publishEvent_Success() throws Exception {
            setAuthentication(userPrincipal);

            EventResponse publishedResponse = EventResponse.builder()
                    .id(EVENT_ID)
                    .title("Test Event")
                    .status(EventStatus.PUBLISHED)
                    .build();

            when(eventService.publishEvent(USER_ID, EVENT_ID)).thenReturn(publishedResponse);

            mockMvc.perform(post("/api/v1/events/{eventId}/publish", EVENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PUBLISHED"));

            verify(eventService).publishEvent(USER_ID, EVENT_ID);
        }

        @Test
        @DisplayName("Not draft event - returns 400")
        void publishEvent_NotDraft() throws Exception {
            setAuthentication(userPrincipal);

            when(eventService.publishEvent(USER_ID, EVENT_ID))
                    .thenThrow(new ValidationException("Only draft events can be published"));

            mockMvc.perform(post("/api/v1/events/{eventId}/publish", EVENT_ID))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Only draft events can be published"));
        }

        @Test
        @DisplayName("No edit permission - returns 403")
        void publishEvent_Forbidden() throws Exception {
            setAuthentication(userPrincipal);

            when(eventService.publishEvent(USER_ID, EVENT_ID))
                    .thenThrow(new ForbiddenException("No permission to publish this event"));

            mockMvc.perform(post("/api/v1/events/{eventId}/publish", EVENT_ID))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Event not found - returns 400")
        void publishEvent_NotFound() throws Exception {
            setAuthentication(userPrincipal);

            when(eventService.publishEvent(USER_ID, EVENT_ID))
                    .thenThrow(new ValidationException("Event not found"));

            mockMvc.perform(post("/api/v1/events/{eventId}/publish", EVENT_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void publishEvent_InvalidUuid() throws Exception {
            setAuthentication(userPrincipal);

            mockMvc.perform(post("/api/v1/events/{eventId}/publish", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(eventService);
        }
    }

    // ==================== CANCEL EVENT ====================

    @Nested
    @DisplayName("POST /api/v1/events/{eventId}/cancel - cancelEvent")
    class CancelEventTests {

        @Test
        @DisplayName("Success - with reason")
        void cancelEvent_WithReason() throws Exception {
            setAuthentication(userPrincipal);

            EventResponse cancelledResponse = EventResponse.builder()
                    .id(EVENT_ID)
                    .title("Test Event")
                    .status(EventStatus.CANCELLED)
                    .build();

            when(eventService.cancelEvent(USER_ID, EVENT_ID, "Weather conditions"))
                    .thenReturn(cancelledResponse);

            mockMvc.perform(post("/api/v1/events/{eventId}/cancel", EVENT_ID)
                            .param("reason", "Weather conditions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));

            verify(eventService).cancelEvent(USER_ID, EVENT_ID, "Weather conditions");
        }

        @Test
        @DisplayName("Success - without reason (reason is optional)")
        void cancelEvent_WithoutReason() throws Exception {
            setAuthentication(userPrincipal);

            EventResponse cancelledResponse = EventResponse.builder()
                    .id(EVENT_ID)
                    .status(EventStatus.CANCELLED)
                    .build();

            when(eventService.cancelEvent(USER_ID, EVENT_ID, null))
                    .thenReturn(cancelledResponse);

            mockMvc.perform(post("/api/v1/events/{eventId}/cancel", EVENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));

            verify(eventService).cancelEvent(USER_ID, EVENT_ID, null);
        }

        @Test
        @DisplayName("Not host - returns 403")
        void cancelEvent_Forbidden() throws Exception {
            setAuthentication(userPrincipal);

            when(eventService.cancelEvent(eq(USER_ID), eq(EVENT_ID), any()))
                    .thenThrow(new ForbiddenException("Only host can cancel this event"));

            mockMvc.perform(post("/api/v1/events/{eventId}/cancel", EVENT_ID))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Event not found - returns 400")
        void cancelEvent_NotFound() throws Exception {
            setAuthentication(userPrincipal);

            when(eventService.cancelEvent(eq(USER_ID), eq(EVENT_ID), any()))
                    .thenThrow(new ValidationException("Event not found"));

            mockMvc.perform(post("/api/v1/events/{eventId}/cancel", EVENT_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void cancelEvent_InvalidUuid() throws Exception {
            setAuthentication(userPrincipal);

            mockMvc.perform(post("/api/v1/events/{eventId}/cancel", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(eventService);
        }
    }

    // ==================== GET UPCOMING EVENTS ====================

    @Nested
    @DisplayName("GET /api/v1/events/upcoming - getUpcomingEvents")
    class GetUpcomingEventsTests {

        @Test
        @DisplayName("Success - returns paginated events")
        void getUpcomingEvents_Success() throws Exception {
            setAuthentication(userPrincipal);

            PageResponse<EventSummaryResponse> pageResponse = PageResponse.<EventSummaryResponse>builder()
                    .content(List.of(testEventSummary))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .first(true)
                    .last(true)
                    .empty(false)
                    .build();

            when(eventService.getUpcomingEvents(0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/events/upcoming"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.totalElements").value(1));

            verify(eventService).getUpcomingEvents(0, 20);
        }

        @Test
        @DisplayName("Success - empty result")
        void getUpcomingEvents_Empty() throws Exception {
            setAuthentication(userPrincipal);

            PageResponse<EventSummaryResponse> emptyResponse = PageResponse.<EventSummaryResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .first(true)
                    .last(true)
                    .empty(true)
                    .build();

            when(eventService.getUpcomingEvents(0, 20)).thenReturn(emptyResponse);

            mockMvc.perform(get("/api/v1/events/upcoming"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)))
                    .andExpect(jsonPath("$.empty").value(true));
        }

        @Test
        @DisplayName("Success - custom pagination")
        void getUpcomingEvents_CustomPagination() throws Exception {
            setAuthentication(userPrincipal);

            PageResponse<EventSummaryResponse> pageResponse = PageResponse.<EventSummaryResponse>builder()
                    .content(Collections.emptyList())
                    .page(5)
                    .size(10)
                    .build();

            when(eventService.getUpcomingEvents(5, 10)).thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/events/upcoming")
                            .param("page", "5")
                            .param("size", "10"))
                    .andExpect(status().isOk());

            verify(eventService).getUpcomingEvents(5, 10);
        }

        @Test
        @DisplayName("Page below minimum - returns 400")
        void getUpcomingEvents_PageBelowMin() throws Exception {
            setAuthentication(userPrincipal);

            mockMvc.perform(get("/api/v1/events/upcoming")
                            .param("page", "-1"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(eventService);
        }

        @Test
        @DisplayName("Page above maximum - returns 400")
        void getUpcomingEvents_PageAboveMax() throws Exception {
            setAuthentication(userPrincipal);

            mockMvc.perform(get("/api/v1/events/upcoming")
                            .param("page", "1001"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(eventService);
        }

        @Test
        @DisplayName("Size below minimum - returns 400")
        void getUpcomingEvents_SizeBelowMin() throws Exception {
            setAuthentication(userPrincipal);

            mockMvc.perform(get("/api/v1/events/upcoming")
                            .param("size", "0"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(eventService);
        }

        @Test
        @DisplayName("Size above maximum - returns 400")
        void getUpcomingEvents_SizeAboveMax() throws Exception {
            setAuthentication(userPrincipal);

            mockMvc.perform(get("/api/v1/events/upcoming")
                            .param("size", "101"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(eventService);
        }
    }

    // ==================== SEARCH EVENTS ====================

    @Nested
    @DisplayName("GET /api/v1/events/search - searchEvents")
    class SearchEventsTests {

        @Test
        @DisplayName("Success - returns search results")
        void searchEvents_Success() throws Exception {
            setAuthentication(userPrincipal);

            PageResponse<EventSummaryResponse> pageResponse = PageResponse.<EventSummaryResponse>builder()
                    .content(List.of(testEventSummary))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .build();

            when(eventService.searchEvents("conference", 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/events/search")
                            .param("query", "conference"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)));

            verify(eventService).searchEvents("conference", 0, 20);
        }

        @Test
        @DisplayName("Success - empty search results")
        void searchEvents_Empty() throws Exception {
            setAuthentication(userPrincipal);

            PageResponse<EventSummaryResponse> emptyResponse = PageResponse.<EventSummaryResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .empty(true)
                    .build();

            when(eventService.searchEvents("nonexistent", 0, 20)).thenReturn(emptyResponse);

            mockMvc.perform(get("/api/v1/events/search")
                            .param("query", "nonexistent"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.empty").value(true));
        }

        @Test
        @DisplayName("Success - with custom pagination")
        void searchEvents_CustomPagination() throws Exception {
            setAuthentication(userPrincipal);

            PageResponse<EventSummaryResponse> pageResponse = PageResponse.<EventSummaryResponse>builder()
                    .content(Collections.emptyList())
                    .build();

            when(eventService.searchEvents("test", 2, 50)).thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/events/search")
                            .param("query", "test")
                            .param("page", "2")
                            .param("size", "50"))
                    .andExpect(status().isOk());

            verify(eventService).searchEvents("test", 2, 50);
        }

        @Test
        @DisplayName("Missing query parameter - returns 400")
        void searchEvents_MissingQuery() throws Exception {
            setAuthentication(userPrincipal);

            mockMvc.perform(get("/api/v1/events/search"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(eventService);
        }

        @Test
        @DisplayName("Page validation - below min")
        void searchEvents_PageBelowMin() throws Exception {
            setAuthentication(userPrincipal);

            mockMvc.perform(get("/api/v1/events/search")
                            .param("query", "test")
                            .param("page", "-1"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(eventService);
        }

        @Test
        @DisplayName("Size validation - above max")
        void searchEvents_SizeAboveMax() throws Exception {
            setAuthentication(userPrincipal);

            mockMvc.perform(get("/api/v1/events/search")
                            .param("query", "test")
                            .param("size", "101"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(eventService);
        }
    }

    // ==================== GET POPULAR EVENTS ====================

    @Nested
    @DisplayName("GET /api/v1/events/popular - getPopularEvents")
    class GetPopularEventsTests {

        @Test
        @DisplayName("Success - returns popular events")
        void getPopularEvents_Success() throws Exception {
            setAuthentication(userPrincipal);

            PageResponse<EventSummaryResponse> pageResponse = PageResponse.<EventSummaryResponse>builder()
                    .content(List.of(testEventSummary))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .build();

            when(eventService.getPopularEvents(0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/events/popular"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)));

            verify(eventService).getPopularEvents(0, 20);
        }

        @Test
        @DisplayName("Success - empty result")
        void getPopularEvents_Empty() throws Exception {
            setAuthentication(userPrincipal);

            PageResponse<EventSummaryResponse> emptyResponse = PageResponse.<EventSummaryResponse>builder()
                    .content(Collections.emptyList())
                    .empty(true)
                    .build();

            when(eventService.getPopularEvents(0, 20)).thenReturn(emptyResponse);

            mockMvc.perform(get("/api/v1/events/popular"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.empty").value(true));
        }

        @Test
        @DisplayName("Success - custom pagination")
        void getPopularEvents_CustomPagination() throws Exception {
            setAuthentication(userPrincipal);

            PageResponse<EventSummaryResponse> pageResponse = PageResponse.<EventSummaryResponse>builder()
                    .content(Collections.emptyList())
                    .build();

            when(eventService.getPopularEvents(3, 15)).thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/events/popular")
                            .param("page", "3")
                            .param("size", "15"))
                    .andExpect(status().isOk());

            verify(eventService).getPopularEvents(3, 15);
        }

        @Test
        @DisplayName("Page above maximum - returns 400")
        void getPopularEvents_PageAboveMax() throws Exception {
            setAuthentication(userPrincipal);

            mockMvc.perform(get("/api/v1/events/popular")
                            .param("page", "1001"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(eventService);
        }

        @Test
        @DisplayName("Size below minimum - returns 400")
        void getPopularEvents_SizeBelowMin() throws Exception {
            setAuthentication(userPrincipal);

            mockMvc.perform(get("/api/v1/events/popular")
                            .param("size", "0"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(eventService);
        }
    }

    // ==================== GET MY UPCOMING EVENTS ====================

    @Nested
    @DisplayName("GET /api/v1/events/my/upcoming - getMyUpcomingEvents")
    class GetMyUpcomingEventsTests {

        @Test
        @DisplayName("Success - returns user's upcoming events")
        void getMyUpcomingEvents_Success() throws Exception {
            setAuthentication(userPrincipal);

            PageResponse<EventSummaryResponse> pageResponse = PageResponse.<EventSummaryResponse>builder()
                    .content(List.of(testEventSummary))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .build();

            when(eventService.getUserUpcomingEvents(USER_ID, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/events/my/upcoming"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)));

            verify(eventService).getUserUpcomingEvents(USER_ID, 0, 20);
        }

        @Test
        @DisplayName("Success - empty result")
        void getMyUpcomingEvents_Empty() throws Exception {
            setAuthentication(userPrincipal);

            PageResponse<EventSummaryResponse> emptyResponse = PageResponse.<EventSummaryResponse>builder()
                    .content(Collections.emptyList())
                    .empty(true)
                    .build();

            when(eventService.getUserUpcomingEvents(USER_ID, 0, 20)).thenReturn(emptyResponse);

            mockMvc.perform(get("/api/v1/events/my/upcoming"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.empty").value(true));
        }

        @Test
        @DisplayName("Success - custom pagination")
        void getMyUpcomingEvents_CustomPagination() throws Exception {
            setAuthentication(userPrincipal);

            PageResponse<EventSummaryResponse> pageResponse = PageResponse.<EventSummaryResponse>builder()
                    .content(Collections.emptyList())
                    .build();

            when(eventService.getUserUpcomingEvents(USER_ID, 1, 30)).thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/events/my/upcoming")
                            .param("page", "1")
                            .param("size", "30"))
                    .andExpect(status().isOk());

            verify(eventService).getUserUpcomingEvents(USER_ID, 1, 30);
        }

        @Test
        @DisplayName("Page below minimum - returns 400")
        void getMyUpcomingEvents_PageBelowMin() throws Exception {
            setAuthentication(userPrincipal);

            mockMvc.perform(get("/api/v1/events/my/upcoming")
                            .param("page", "-1"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(eventService);
        }

        @Test
        @DisplayName("Size above maximum - returns 400")
        void getMyUpcomingEvents_SizeAboveMax() throws Exception {
            setAuthentication(userPrincipal);

            mockMvc.perform(get("/api/v1/events/my/upcoming")
                            .param("size", "101"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(eventService);
        }
    }

    // ==================== GET MY HOSTED EVENTS ====================

    @Nested
    @DisplayName("GET /api/v1/events/my/hosted - getMyHostedEvents")
    class GetMyHostedEventsTests {

        @Test
        @DisplayName("Success - returns user's hosted events")
        void getMyHostedEvents_Success() throws Exception {
            setAuthentication(userPrincipal);

            PageResponse<EventSummaryResponse> pageResponse = PageResponse.<EventSummaryResponse>builder()
                    .content(List.of(testEventSummary))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .build();

            when(eventService.getHostedEvents(USER_ID, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/events/my/hosted"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)));

            verify(eventService).getHostedEvents(USER_ID, 0, 20);
        }

        @Test
        @DisplayName("Success - empty result")
        void getMyHostedEvents_Empty() throws Exception {
            setAuthentication(userPrincipal);

            PageResponse<EventSummaryResponse> emptyResponse = PageResponse.<EventSummaryResponse>builder()
                    .content(Collections.emptyList())
                    .empty(true)
                    .build();

            when(eventService.getHostedEvents(USER_ID, 0, 20)).thenReturn(emptyResponse);

            mockMvc.perform(get("/api/v1/events/my/hosted"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.empty").value(true));
        }

        @Test
        @DisplayName("Success - custom pagination")
        void getMyHostedEvents_CustomPagination() throws Exception {
            setAuthentication(userPrincipal);

            PageResponse<EventSummaryResponse> pageResponse = PageResponse.<EventSummaryResponse>builder()
                    .content(Collections.emptyList())
                    .build();

            when(eventService.getHostedEvents(USER_ID, 10, 5)).thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/events/my/hosted")
                            .param("page", "10")
                            .param("size", "5"))
                    .andExpect(status().isOk());

            verify(eventService).getHostedEvents(USER_ID, 10, 5);
        }

        @Test
        @DisplayName("Page above maximum - returns 400")
        void getMyHostedEvents_PageAboveMax() throws Exception {
            setAuthentication(userPrincipal);

            mockMvc.perform(get("/api/v1/events/my/hosted")
                            .param("page", "1001"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(eventService);
        }

        @Test
        @DisplayName("Size below minimum - returns 400")
        void getMyHostedEvents_SizeBelowMin() throws Exception {
            setAuthentication(userPrincipal);

            mockMvc.perform(get("/api/v1/events/my/hosted")
                            .param("size", "0"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(eventService);
        }

        @Test
        @DisplayName("Pagination at boundaries")
        void getMyHostedEvents_AtBoundaries() throws Exception {
            setAuthentication(userPrincipal);

            PageResponse<EventSummaryResponse> pageResponse = PageResponse.<EventSummaryResponse>builder()
                    .content(Collections.emptyList())
                    .build();

            // Test max page (1000) and max size (100)
            when(eventService.getHostedEvents(USER_ID, 1000, 100)).thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/events/my/hosted")
                            .param("page", "1000")
                            .param("size", "100"))
                    .andExpect(status().isOk());

            verify(eventService).getHostedEvents(USER_ID, 1000, 100);
        }
    }
}