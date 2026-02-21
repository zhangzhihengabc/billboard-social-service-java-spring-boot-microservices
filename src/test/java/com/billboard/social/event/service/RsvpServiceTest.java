package com.billboard.social.event.service;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.dto.UserSummary;
import com.billboard.social.common.exception.ForbiddenException;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.common.client.UserServiceClient;
import com.billboard.social.common.security.InputValidator;
import com.billboard.social.event.dto.request.EventRequests.AddCoHostRequest;
import com.billboard.social.event.dto.request.EventRequests.RsvpRequest;
import com.billboard.social.event.dto.response.EventResponses.CoHostResponse;
import com.billboard.social.event.dto.response.EventResponses.RsvpResponse;
import com.billboard.social.event.entity.*;
import com.billboard.social.event.entity.enums.*;
import com.billboard.social.event.event.EventEventPublisher;
import com.billboard.social.event.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RsvpServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventRsvpRepository rsvpRepository;

    @Mock
    private EventCoHostRepository coHostRepository;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private EventEventPublisher eventPublisher;

    @InjectMocks
    private RsvpService rsvpService;

    // Test constants
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID HOST_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID EVENT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID RSVP_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID COHOST_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");

    private Event testEvent;
    private EventRsvp testRsvp;
    private EventCoHost testCoHost;
    private UserSummary testUserSummary;

    @BeforeEach
    void setUp() {
        testEvent = Event.builder()
                .id(EVENT_ID)
                .title("Test Event")
                .slug("test-event")
                .hostId(HOST_ID)
                .status(EventStatus.PUBLISHED)
                .acceptingRsvps(true)
                .allowGuests(true)
                .guestsPerRsvp(5)
                .maxAttendees(100)
                .goingCount(10)
                .maybeCount(5)
                .invitedCount(20)
                .build();

        testRsvp = EventRsvp.builder()
                .id(RSVP_ID)
                .event(testEvent)
                .userId(USER_ID)
                .status(RsvpStatus.GOING)
                .guestCount(0)
                .notificationsEnabled(true)
                .build();
        testRsvp.setRespondedAt(LocalDateTime.now());

        testCoHost = EventCoHost.builder()
                .id(COHOST_ID)
                .event(testEvent)
                .userId(OTHER_USER_ID)
                .canEdit(true)
                .canInvite(true)
                .canManageRsvps(true)
                .build();

        testUserSummary = UserSummary.builder()
                .id(USER_ID)
                .username("testuser")
                .email("test@gmail.com")
                .build();
    }

    // ==================== RSVP ====================

    @Nested
    @DisplayName("rsvp")
    class RsvpTests {

        @Test
        @DisplayName("Success - RSVP GOING to event")
        void rsvp_GoingSuccess() {
            RsvpRequest request = RsvpRequest.builder()
                    .status(RsvpStatus.GOING)
                    .guestCount(2)
                    .note("Excited!")
                    .build();

            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                mockedValidator.when(() -> InputValidator.validateText("Excited!", "Note", 500))
                        .thenReturn("Excited!");

                when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
                when(rsvpRepository.findByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
                when(rsvpRepository.save(any(EventRsvp.class))).thenAnswer(invocation -> {
                    EventRsvp saved = invocation.getArgument(0);
                    saved.setId(RSVP_ID);
                    return saved;
                });
                when(eventRepository.save(any(Event.class))).thenReturn(testEvent);
                when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

                RsvpResponse response = rsvpService.rsvp(USER_ID, EVENT_ID, request);

                assertThat(response).isNotNull();
                assertThat(response.getStatus()).isEqualTo(RsvpStatus.GOING);
                verify(eventPublisher).publishRsvpChanged(any(EventRsvp.class), eq(RsvpStatus.INVITED));
            }
        }

        @Test
        @DisplayName("Success - RSVP MAYBE to event")
        void rsvp_MaybeSuccess() {
            RsvpRequest request = RsvpRequest.builder()
                    .status(RsvpStatus.MAYBE)
                    .build();

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(rsvpRepository.findByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
            when(rsvpRepository.save(any(EventRsvp.class))).thenAnswer(invocation -> {
                EventRsvp saved = invocation.getArgument(0);
                saved.setId(RSVP_ID);
                return saved;
            });
            when(eventRepository.save(any(Event.class))).thenReturn(testEvent);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            RsvpResponse response = rsvpService.rsvp(USER_ID, EVENT_ID, request);

            assertThat(response.getStatus()).isEqualTo(RsvpStatus.MAYBE);
        }

        @Test
        @DisplayName("Success - RSVP NOT_GOING to event")
        void rsvp_NotGoingSuccess() {
            RsvpRequest request = RsvpRequest.builder()
                    .status(RsvpStatus.NOT_GOING)
                    .build();

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(rsvpRepository.findByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
            when(rsvpRepository.save(any(EventRsvp.class))).thenAnswer(invocation -> {
                EventRsvp saved = invocation.getArgument(0);
                saved.setId(RSVP_ID);
                return saved;
            });
            when(eventRepository.save(any(Event.class))).thenReturn(testEvent);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            RsvpResponse response = rsvpService.rsvp(USER_ID, EVENT_ID, request);

            assertThat(response.getStatus()).isEqualTo(RsvpStatus.NOT_GOING);
        }

        @Test
        @DisplayName("Success - Update existing RSVP from MAYBE to GOING")
        void rsvp_UpdateExistingRsvp() {
            testRsvp.setStatus(RsvpStatus.MAYBE);
            RsvpRequest request = RsvpRequest.builder()
                    .status(RsvpStatus.GOING)
                    .build();

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(rsvpRepository.findByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.of(testRsvp));
            when(rsvpRepository.save(any(EventRsvp.class))).thenReturn(testRsvp);
            when(eventRepository.save(any(Event.class))).thenReturn(testEvent);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            RsvpResponse response = rsvpService.rsvp(USER_ID, EVENT_ID, request);

            assertThat(response).isNotNull();
            verify(eventPublisher).publishRsvpChanged(any(EventRsvp.class), eq(RsvpStatus.MAYBE));
        }

        @Test
        @DisplayName("Success - Update existing RSVP from GOING to MAYBE")
        void rsvp_UpdateGoingToMaybe() {
            testRsvp.setStatus(RsvpStatus.GOING);
            RsvpRequest request = RsvpRequest.builder()
                    .status(RsvpStatus.MAYBE)
                    .build();

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(rsvpRepository.findByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.of(testRsvp));
            when(rsvpRepository.save(any(EventRsvp.class))).thenReturn(testRsvp);
            when(eventRepository.save(any(Event.class))).thenReturn(testEvent);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            rsvpService.rsvp(USER_ID, EVENT_ID, request);

            // Verify counts updated: decrement GOING, increment MAYBE
            verify(eventRepository).save(any(Event.class));
        }

        @Test
        @DisplayName("Null request - throws ValidationException")
        void rsvp_NullRequest() {
            assertThatThrownBy(() -> rsvpService.rsvp(USER_ID, EVENT_ID, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Request body is required");

            verifyNoInteractions(eventRepository);
        }

        @Test
        @DisplayName("Null status - throws ValidationException")
        void rsvp_NullStatus() {
            RsvpRequest request = RsvpRequest.builder().build();

            assertThatThrownBy(() -> rsvpService.rsvp(USER_ID, EVENT_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("RSVP status is required");

            verifyNoInteractions(eventRepository);
        }

        @Test
        @DisplayName("CHECKED_IN status - throws ValidationException")
        void rsvp_CheckedInStatus() {
            RsvpRequest request = RsvpRequest.builder()
                    .status(RsvpStatus.CHECKED_IN)
                    .build();

            assertThatThrownBy(() -> rsvpService.rsvp(USER_ID, EVENT_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Cannot set CHECKED_IN status via RSVP. Use check-in endpoint.");

            verifyNoInteractions(eventRepository);
        }

        @Test
        @DisplayName("Event not found - throws ValidationException")
        void rsvp_EventNotFound() {
            RsvpRequest request = RsvpRequest.builder()
                    .status(RsvpStatus.GOING)
                    .build();

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> rsvpService.rsvp(USER_ID, EVENT_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Event not found with id: " + EVENT_ID);
        }

        @Test
        @DisplayName("Event not published - throws ValidationException")
        void rsvp_EventNotPublished() {
            testEvent.setStatus(EventStatus.DRAFT);
            RsvpRequest request = RsvpRequest.builder()
                    .status(RsvpStatus.GOING)
                    .build();

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));

            assertThatThrownBy(() -> rsvpService.rsvp(USER_ID, EVENT_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Cannot RSVP to unpublished event");
        }

        @Test
        @DisplayName("Event not accepting RSVPs - throws ValidationException")
        void rsvp_NotAcceptingRsvps() {
            testEvent.setAcceptingRsvps(false);
            RsvpRequest request = RsvpRequest.builder()
                    .status(RsvpStatus.GOING)
                    .build();

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));

            assertThatThrownBy(() -> rsvpService.rsvp(USER_ID, EVENT_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("This event is not accepting RSVPs at this time");
        }

        @Test
        @DisplayName("Event at capacity - throws ValidationException")
        void rsvp_EventAtCapacity() {
            testEvent.setMaxAttendees(10);
            testEvent.setGoingCount(10); // At capacity
            RsvpRequest request = RsvpRequest.builder()
                    .status(RsvpStatus.GOING)
                    .build();

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));

            assertThatThrownBy(() -> rsvpService.rsvp(USER_ID, EVENT_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Event is at full capacity");
        }

        @Test
        @DisplayName("MAYBE at capacity - succeeds (only GOING checks capacity)")
        void rsvp_MaybeAtCapacity() {
            testEvent.setMaxAttendees(10);
            testEvent.setGoingCount(10);
            RsvpRequest request = RsvpRequest.builder()
                    .status(RsvpStatus.MAYBE)
                    .build();

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(rsvpRepository.findByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
            when(rsvpRepository.save(any(EventRsvp.class))).thenAnswer(invocation -> {
                EventRsvp saved = invocation.getArgument(0);
                saved.setId(RSVP_ID);
                return saved;
            });
            when(eventRepository.save(any(Event.class))).thenReturn(testEvent);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            RsvpResponse response = rsvpService.rsvp(USER_ID, EVENT_ID, request);

            assertThat(response.getStatus()).isEqualTo(RsvpStatus.MAYBE);
        }

        @Test
        @DisplayName("Guests not allowed - throws ValidationException")
        void rsvp_GuestsNotAllowed() {
            testEvent.setAllowGuests(false);
            RsvpRequest request = RsvpRequest.builder()
                    .status(RsvpStatus.GOING)
                    .guestCount(2)
                    .build();

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));

            assertThatThrownBy(() -> rsvpService.rsvp(USER_ID, EVENT_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("This event does not allow guests");
        }

        @Test
        @DisplayName("Guest count exceeds limit - throws ValidationException")
        void rsvp_GuestCountExceedsLimit() {
            testEvent.setGuestsPerRsvp(3);
            RsvpRequest request = RsvpRequest.builder()
                    .status(RsvpStatus.GOING)
                    .guestCount(5)
                    .build();

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));

            assertThatThrownBy(() -> rsvpService.rsvp(USER_ID, EVENT_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Maximum 3 guests allowed");
        }

        @Test
        @DisplayName("Zero guest count - succeeds")
        void rsvp_ZeroGuestCount() {
            RsvpRequest request = RsvpRequest.builder()
                    .status(RsvpStatus.GOING)
                    .guestCount(0)
                    .build();

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(rsvpRepository.findByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
            when(rsvpRepository.save(any(EventRsvp.class))).thenAnswer(invocation -> {
                EventRsvp saved = invocation.getArgument(0);
                saved.setId(RSVP_ID);
                return saved;
            });
            when(eventRepository.save(any(Event.class))).thenReturn(testEvent);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            RsvpResponse response = rsvpService.rsvp(USER_ID, EVENT_ID, request);

            assertThat(response.getGuestCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("Null guest count - defaults to 0")
        void rsvp_NullGuestCount() {
            RsvpRequest request = RsvpRequest.builder()
                    .status(RsvpStatus.GOING)
                    .build();

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(rsvpRepository.findByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
            when(rsvpRepository.save(any(EventRsvp.class))).thenAnswer(invocation -> {
                EventRsvp saved = invocation.getArgument(0);
                assertThat(saved.getGuestCount()).isEqualTo(0);
                saved.setId(RSVP_ID);
                return saved;
            });
            when(eventRepository.save(any(Event.class))).thenReturn(testEvent);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            rsvpService.rsvp(USER_ID, EVENT_ID, request);
        }

        @Test
        @DisplayName("Blank note - not validated")
        void rsvp_BlankNote() {
            RsvpRequest request = RsvpRequest.builder()
                    .status(RsvpStatus.GOING)
                    .note("   ")
                    .build();

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(rsvpRepository.findByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
            when(rsvpRepository.save(any(EventRsvp.class))).thenAnswer(invocation -> {
                EventRsvp saved = invocation.getArgument(0);
                assertThat(saved.getNote()).isNull();
                saved.setId(RSVP_ID);
                return saved;
            });
            when(eventRepository.save(any(Event.class))).thenReturn(testEvent);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            rsvpService.rsvp(USER_ID, EVENT_ID, request);
        }

        @Test
        @DisplayName("Cannot change from CHECKED_IN status")
        void rsvp_CannotChangeFromCheckedIn() {
            testRsvp.setStatus(RsvpStatus.CHECKED_IN);
            RsvpRequest request = RsvpRequest.builder()
                    .status(RsvpStatus.GOING)
                    .build();

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(rsvpRepository.findByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.of(testRsvp));

            assertThatThrownBy(() -> rsvpService.rsvp(USER_ID, EVENT_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Cannot change RSVP status after check-in");
        }

        @Test
        @DisplayName("Null eventId - throws ValidationException")
        void rsvp_NullEventId() {
            RsvpRequest request = RsvpRequest.builder()
                    .status(RsvpStatus.GOING)
                    .build();

            assertThatThrownBy(() -> rsvpService.rsvp(USER_ID, null, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Event ID is required");
        }
    }

    // ==================== CANCEL RSVP ====================

    @Nested
    @DisplayName("cancelRsvp")
    class CancelRsvpTests {

        @Test
        @DisplayName("Success - cancel GOING RSVP")
        void cancelRsvp_GoingSuccess() {
            testRsvp.setStatus(RsvpStatus.GOING);

            when(rsvpRepository.findByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.of(testRsvp));
            doNothing().when(rsvpRepository).delete(testRsvp);
            when(eventRepository.save(any(Event.class))).thenReturn(testEvent);

            rsvpService.cancelRsvp(USER_ID, EVENT_ID);

            verify(rsvpRepository).delete(testRsvp);
            verify(eventRepository).save(argThat(event -> event.getGoingCount() == 9)); // Decremented
        }

        @Test
        @DisplayName("Success - cancel MAYBE RSVP")
        void cancelRsvp_MaybeSuccess() {
            testRsvp.setStatus(RsvpStatus.MAYBE);

            when(rsvpRepository.findByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.of(testRsvp));
            doNothing().when(rsvpRepository).delete(testRsvp);
            when(eventRepository.save(any(Event.class))).thenReturn(testEvent);

            rsvpService.cancelRsvp(USER_ID, EVENT_ID);

            verify(rsvpRepository).delete(testRsvp);
            verify(eventRepository).save(argThat(event -> event.getMaybeCount() == 4)); // Decremented
        }

        @Test
        @DisplayName("Success - cancel NOT_GOING RSVP (no count change)")
        void cancelRsvp_NotGoingSuccess() {
            testRsvp.setStatus(RsvpStatus.NOT_GOING);

            when(rsvpRepository.findByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.of(testRsvp));
            doNothing().when(rsvpRepository).delete(testRsvp);
            when(eventRepository.save(any(Event.class))).thenReturn(testEvent);

            rsvpService.cancelRsvp(USER_ID, EVENT_ID);

            verify(rsvpRepository).delete(testRsvp);
        }

        @Test
        @DisplayName("Null eventId - throws ValidationException")
        void cancelRsvp_NullEventId() {
            assertThatThrownBy(() -> rsvpService.cancelRsvp(USER_ID, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Event ID is required");

            verifyNoInteractions(rsvpRepository);
        }

        @Test
        @DisplayName("RSVP not found - throws ValidationException")
        void cancelRsvp_NotFound() {
            when(rsvpRepository.findByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> rsvpService.cancelRsvp(USER_ID, EVENT_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("RSVP not found for this event");
        }

        @Test
        @DisplayName("Host cannot cancel - throws ValidationException")
        void cancelRsvp_HostCannotCancel() {
            testRsvp.setUserId(HOST_ID);

            when(rsvpRepository.findByEventIdAndUserId(EVENT_ID, HOST_ID)).thenReturn(Optional.of(testRsvp));

            assertThatThrownBy(() -> rsvpService.cancelRsvp(HOST_ID, EVENT_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Host cannot cancel their RSVP");

            verify(rsvpRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Cannot cancel after check-in - throws ValidationException")
        void cancelRsvp_CannotCancelAfterCheckIn() {
            testRsvp.setStatus(RsvpStatus.CHECKED_IN);

            when(rsvpRepository.findByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.of(testRsvp));

            assertThatThrownBy(() -> rsvpService.cancelRsvp(USER_ID, EVENT_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Cannot cancel RSVP after check-in");

            verify(rsvpRepository, never()).delete(any());
        }
    }

    // ==================== GET ATTENDEES ====================

    @Nested
    @DisplayName("getAttendees")
    class GetAttendeesTests {

        @Test
        @DisplayName("Success - without status filter")
        void getAttendees_WithoutFilter() {
            Page<EventRsvp> page = new PageImpl<>(List.of(testRsvp), PageRequest.of(0, 20), 1);

            when(rsvpRepository.findByEventIdPageable(eq(EVENT_ID), any(Pageable.class))).thenReturn(page);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            PageResponse<RsvpResponse> response = rsvpService.getAttendees(EVENT_ID, null, 0, 20);

            assertThat(response.getContent()).hasSize(1);
            verify(rsvpRepository).findByEventIdPageable(eq(EVENT_ID), any(Pageable.class));
        }

        @Test
        @DisplayName("Success - with status filter GOING")
        void getAttendees_WithGoingFilter() {
            Page<EventRsvp> page = new PageImpl<>(List.of(testRsvp), PageRequest.of(0, 20), 1);

            when(rsvpRepository.findByEventIdAndStatus(eq(EVENT_ID), eq(RsvpStatus.GOING), any(Pageable.class)))
                    .thenReturn(page);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            PageResponse<RsvpResponse> response = rsvpService.getAttendees(EVENT_ID, RsvpStatus.GOING, 0, 20);

            assertThat(response.getContent()).hasSize(1);
            verify(rsvpRepository).findByEventIdAndStatus(eq(EVENT_ID), eq(RsvpStatus.GOING), any(Pageable.class));
        }

        @Test
        @DisplayName("Success - with status filter MAYBE")
        void getAttendees_WithMaybeFilter() {
            testRsvp.setStatus(RsvpStatus.MAYBE);
            Page<EventRsvp> page = new PageImpl<>(List.of(testRsvp), PageRequest.of(0, 20), 1);

            when(rsvpRepository.findByEventIdAndStatus(eq(EVENT_ID), eq(RsvpStatus.MAYBE), any(Pageable.class)))
                    .thenReturn(page);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            PageResponse<RsvpResponse> response = rsvpService.getAttendees(EVENT_ID, RsvpStatus.MAYBE, 0, 20);

            assertThat(response.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Success - empty result")
        void getAttendees_Empty() {
            Page<EventRsvp> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);

            when(rsvpRepository.findByEventIdPageable(eq(EVENT_ID), any(Pageable.class))).thenReturn(emptyPage);

            PageResponse<RsvpResponse> response = rsvpService.getAttendees(EVENT_ID, null, 0, 20);

            assertThat(response.getContent()).isEmpty();
        }

        @Test
        @DisplayName("Null eventId - throws ValidationException")
        void getAttendees_NullEventId() {
            assertThatThrownBy(() -> rsvpService.getAttendees(null, null, 0, 20))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Event ID is required");

            verifyNoInteractions(rsvpRepository);
        }
    }

    // ==================== GET GOING ATTENDEES ====================

    @Nested
    @DisplayName("getGoingAttendees")
    class GetGoingAttendeesTests {

        @Test
        @DisplayName("Success - returns going attendees")
        void getGoingAttendees_Success() {
            Page<EventRsvp> page = new PageImpl<>(List.of(testRsvp), PageRequest.of(0, 20), 1);

            when(rsvpRepository.findByEventIdAndStatus(eq(EVENT_ID), eq(RsvpStatus.GOING), any(Pageable.class)))
                    .thenReturn(page);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            PageResponse<RsvpResponse> response = rsvpService.getGoingAttendees(EVENT_ID, 0, 20);

            assertThat(response.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Null eventId - throws ValidationException")
        void getGoingAttendees_NullEventId() {
            assertThatThrownBy(() -> rsvpService.getGoingAttendees(null, 0, 20))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Event ID is required");
        }
    }

    // ==================== GET CHECKED-IN ATTENDEES ====================

    @Nested
    @DisplayName("getCheckedInAttendees")
    class GetCheckedInAttendeesTests {

        @Test
        @DisplayName("Success - returns checked-in attendees")
        void getCheckedInAttendees_Success() {
            testRsvp.setStatus(RsvpStatus.CHECKED_IN);
            testRsvp.setCheckedInAt(LocalDateTime.now());
            Page<EventRsvp> page = new PageImpl<>(List.of(testRsvp), PageRequest.of(0, 20), 1);

            when(rsvpRepository.findByEventIdAndStatus(eq(EVENT_ID), eq(RsvpStatus.CHECKED_IN), any(Pageable.class)))
                    .thenReturn(page);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            PageResponse<RsvpResponse> response = rsvpService.getCheckedInAttendees(EVENT_ID, 0, 20);

            assertThat(response.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Null eventId - throws ValidationException")
        void getCheckedInAttendees_NullEventId() {
            assertThatThrownBy(() -> rsvpService.getCheckedInAttendees(null, 0, 20))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Event ID is required");
        }
    }

    // ==================== GET GOING USER IDS ====================

    @Nested
    @DisplayName("getGoingUserIds")
    class GetGoingUserIdsTests {

        @Test
        @DisplayName("Success - returns user IDs")
        void getGoingUserIds_Success() {
            List<UUID> userIds = List.of(USER_ID, OTHER_USER_ID);

            when(rsvpRepository.findGoingUserIds(EVENT_ID)).thenReturn(userIds);

            List<UUID> result = rsvpService.getGoingUserIds(EVENT_ID);

            assertThat(result).hasSize(2);
            assertThat(result).containsExactly(USER_ID, OTHER_USER_ID);
        }

        @Test
        @DisplayName("Success - empty list")
        void getGoingUserIds_Empty() {
            when(rsvpRepository.findGoingUserIds(EVENT_ID)).thenReturn(Collections.emptyList());

            List<UUID> result = rsvpService.getGoingUserIds(EVENT_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Null eventId - throws ValidationException")
        void getGoingUserIds_NullEventId() {
            assertThatThrownBy(() -> rsvpService.getGoingUserIds(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Event ID is required");
        }
    }

    // ==================== CHECK-IN ====================

    @Nested
    @DisplayName("checkIn")
    class CheckInTests {

        @Test
        @DisplayName("Success - host checks in attendee")
        void checkIn_HostSuccess() {
            testRsvp.setStatus(RsvpStatus.GOING);

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(rsvpRepository.findByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.of(testRsvp));
            when(rsvpRepository.save(any(EventRsvp.class))).thenReturn(testRsvp);
            when(eventRepository.save(any(Event.class))).thenReturn(testEvent);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            RsvpResponse response = rsvpService.checkIn(HOST_ID, EVENT_ID, USER_ID);

            assertThat(response).isNotNull();
            verify(rsvpRepository).save(argThat(rsvp -> rsvp.getStatus() == RsvpStatus.CHECKED_IN));
            verify(eventPublisher).publishCheckIn(any(EventRsvp.class));
        }

        @Test
        @DisplayName("Success - co-host checks in attendee")
        void checkIn_CoHostSuccess() {
            testRsvp.setStatus(RsvpStatus.GOING);

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(coHostRepository.existsByEventIdAndUserId(EVENT_ID, OTHER_USER_ID)).thenReturn(true);
            when(rsvpRepository.findByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.of(testRsvp));
            when(rsvpRepository.save(any(EventRsvp.class))).thenReturn(testRsvp);
            when(eventRepository.save(any(Event.class))).thenReturn(testEvent);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            RsvpResponse response = rsvpService.checkIn(OTHER_USER_ID, EVENT_ID, USER_ID);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Null eventId - throws ValidationException")
        void checkIn_NullEventId() {
            assertThatThrownBy(() -> rsvpService.checkIn(HOST_ID, null, USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Event ID is required");
        }

        @Test
        @DisplayName("Null userId - throws ValidationException")
        void checkIn_NullUserId() {
            assertThatThrownBy(() -> rsvpService.checkIn(HOST_ID, EVENT_ID, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("User ID is required");
        }

        @Test
        @DisplayName("Event not found - throws ValidationException")
        void checkIn_EventNotFound() {
            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> rsvpService.checkIn(HOST_ID, EVENT_ID, USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Event not found with id: " + EVENT_ID);
        }

        @Test
        @DisplayName("Not host or co-host - throws ForbiddenException")
        void checkIn_NotHostOrCoHost() {
            UUID randomUserId = UUID.randomUUID();

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(coHostRepository.existsByEventIdAndUserId(EVENT_ID, randomUserId)).thenReturn(false);

            assertThatThrownBy(() -> rsvpService.checkIn(randomUserId, EVENT_ID, USER_ID))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Only host or co-host can perform this action");
        }

        @Test
        @DisplayName("RSVP not found - throws ValidationException")
        void checkIn_RsvpNotFound() {
            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(rsvpRepository.findByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> rsvpService.checkIn(HOST_ID, EVENT_ID, USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("RSVP not found for this user");
        }

        @Test
        @DisplayName("Already checked in - throws ValidationException")
        void checkIn_AlreadyCheckedIn() {
            testRsvp.setStatus(RsvpStatus.CHECKED_IN);

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(rsvpRepository.findByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.of(testRsvp));

            assertThatThrownBy(() -> rsvpService.checkIn(HOST_ID, EVENT_ID, USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("User is already checked in");
        }

        @Test
        @DisplayName("Not GOING status - throws ValidationException")
        void checkIn_NotGoingStatus() {
            testRsvp.setStatus(RsvpStatus.MAYBE);

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(rsvpRepository.findByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.of(testRsvp));

            assertThatThrownBy(() -> rsvpService.checkIn(HOST_ID, EVENT_ID, USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("User must be marked as GOING to check in. Current status: MAYBE");
        }

        @Test
        @DisplayName("NOT_GOING status - throws ValidationException")
        void checkIn_NotGoingStatusNotGoing() {
            testRsvp.setStatus(RsvpStatus.NOT_GOING);

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(rsvpRepository.findByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.of(testRsvp));

            assertThatThrownBy(() -> rsvpService.checkIn(HOST_ID, EVENT_ID, USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("User must be marked as GOING to check in. Current status: NOT_GOING");
        }
    }

    // ==================== UNDO CHECK-IN ====================

    @Nested
    @DisplayName("undoCheckIn")
    class UndoCheckInTests {

        @Test
        @DisplayName("Success - undo check-in")
        void undoCheckIn_Success() {
            testRsvp.setStatus(RsvpStatus.CHECKED_IN);
            testRsvp.setCheckedInAt(LocalDateTime.now());

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(rsvpRepository.findByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.of(testRsvp));
            when(rsvpRepository.save(any(EventRsvp.class))).thenReturn(testRsvp);
            when(eventRepository.save(any(Event.class))).thenReturn(testEvent);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            RsvpResponse response = rsvpService.undoCheckIn(HOST_ID, EVENT_ID, USER_ID);

            assertThat(response).isNotNull();
            verify(rsvpRepository).save(argThat(rsvp ->
                    rsvp.getStatus() == RsvpStatus.GOING && rsvp.getCheckedInAt() == null));
            verify(eventRepository).save(argThat(event -> event.getGoingCount() == 11)); // Incremented
        }

        @Test
        @DisplayName("Null eventId - throws ValidationException")
        void undoCheckIn_NullEventId() {
            assertThatThrownBy(() -> rsvpService.undoCheckIn(HOST_ID, null, USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Event ID is required");
        }

        @Test
        @DisplayName("Null userId - throws ValidationException")
        void undoCheckIn_NullUserId() {
            assertThatThrownBy(() -> rsvpService.undoCheckIn(HOST_ID, EVENT_ID, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("User ID is required");
        }

        @Test
        @DisplayName("RSVP not found - throws ValidationException")
        void undoCheckIn_RsvpNotFound() {
            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(rsvpRepository.findByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> rsvpService.undoCheckIn(HOST_ID, EVENT_ID, USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("RSVP not found for this user");
        }

        @Test
        @DisplayName("Not checked in - throws ValidationException")
        void undoCheckIn_NotCheckedIn() {
            testRsvp.setStatus(RsvpStatus.GOING);

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(rsvpRepository.findByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.of(testRsvp));

            assertThatThrownBy(() -> rsvpService.undoCheckIn(HOST_ID, EVENT_ID, USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("User is not checked in");
        }

        @Test
        @DisplayName("Not host or co-host - throws ForbiddenException")
        void undoCheckIn_NotHostOrCoHost() {
            UUID randomUserId = UUID.randomUUID();
            testRsvp.setStatus(RsvpStatus.CHECKED_IN);

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(coHostRepository.existsByEventIdAndUserId(EVENT_ID, randomUserId)).thenReturn(false);

            assertThatThrownBy(() -> rsvpService.undoCheckIn(randomUserId, EVENT_ID, USER_ID))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Only host or co-host can perform this action");
        }
    }

    // ==================== GET MY RSVP STATUS ====================

    @Nested
    @DisplayName("getMyRsvpStatus")
    class GetMyRsvpStatusTests {

        @Test
        @DisplayName("Success - returns RSVP status")
        void getMyRsvpStatus_Success() {
            when(rsvpRepository.findByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.of(testRsvp));
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            RsvpResponse response = rsvpService.getMyRsvpStatus(USER_ID, EVENT_ID);

            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(RsvpStatus.GOING);
        }

        @Test
        @DisplayName("Null eventId - throws ValidationException")
        void getMyRsvpStatus_NullEventId() {
            assertThatThrownBy(() -> rsvpService.getMyRsvpStatus(USER_ID, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Event ID is required");
        }

        @Test
        @DisplayName("Not RSVP'd - throws ValidationException")
        void getMyRsvpStatus_NotRsvpd() {
            when(rsvpRepository.findByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> rsvpService.getMyRsvpStatus(USER_ID, EVENT_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("You have not RSVP'd to this event");
        }
    }

    // ==================== ADD CO-HOST ====================

    @Nested
    @DisplayName("addCoHost")
    class AddCoHostTests {

        @Test
        @DisplayName("Success - add co-host")
        void addCoHost_Success() {
            AddCoHostRequest request = AddCoHostRequest.builder()
                    .userId(OTHER_USER_ID)
                    .build();

            UserSummary coHostSummary = UserSummary.builder()
                    .id(OTHER_USER_ID)
                    .username("cohost")
                    .build();

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(coHostRepository.existsByEventIdAndUserId(EVENT_ID, OTHER_USER_ID)).thenReturn(false);
            when(userServiceClient.getUserSummary(OTHER_USER_ID)).thenReturn(coHostSummary);
            when(coHostRepository.save(any(EventCoHost.class))).thenAnswer(invocation -> {
                EventCoHost saved = invocation.getArgument(0);
                saved.setId(COHOST_ID);
                return saved;
            });

            CoHostResponse response = rsvpService.addCoHost(HOST_ID, EVENT_ID, request);

            assertThat(response).isNotNull();
            assertThat(response.getUserId()).isEqualTo(OTHER_USER_ID);
        }

        @Test
        @DisplayName("Null request - throws ValidationException")
        void addCoHost_NullRequest() {
            assertThatThrownBy(() -> rsvpService.addCoHost(HOST_ID, EVENT_ID, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Request body is required");
        }

        @Test
        @DisplayName("Null userId in request - throws ValidationException")
        void addCoHost_NullUserId() {
            AddCoHostRequest request = AddCoHostRequest.builder().build();

            assertThatThrownBy(() -> rsvpService.addCoHost(HOST_ID, EVENT_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("User ID is required");
        }

        @Test
        @DisplayName("Not host - throws ForbiddenException")
        void addCoHost_NotHost() {
            AddCoHostRequest request = AddCoHostRequest.builder()
                    .userId(OTHER_USER_ID)
                    .build();

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));

            assertThatThrownBy(() -> rsvpService.addCoHost(USER_ID, EVENT_ID, request))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Only the event host can manage co-hosts");
        }

        @Test
        @DisplayName("Adding host as co-host - throws ValidationException")
        void addCoHost_AddingHostAsCoHost() {
            AddCoHostRequest request = AddCoHostRequest.builder()
                    .userId(HOST_ID)
                    .build();

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));

            assertThatThrownBy(() -> rsvpService.addCoHost(HOST_ID, EVENT_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Cannot add event host as co-host");
        }

        @Test
        @DisplayName("Already co-host - throws ValidationException")
        void addCoHost_AlreadyCoHost() {
            AddCoHostRequest request = AddCoHostRequest.builder()
                    .userId(OTHER_USER_ID)
                    .build();

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(coHostRepository.existsByEventIdAndUserId(EVENT_ID, OTHER_USER_ID)).thenReturn(true);

            assertThatThrownBy(() -> rsvpService.addCoHost(HOST_ID, EVENT_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("User is already a co-host of this event");
        }

        @Test
        @DisplayName("User not found - throws ValidationException")
        void addCoHost_UserNotFound() {
            AddCoHostRequest request = AddCoHostRequest.builder()
                    .userId(OTHER_USER_ID)
                    .build();

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(coHostRepository.existsByEventIdAndUserId(EVENT_ID, OTHER_USER_ID)).thenReturn(false);
            when(userServiceClient.getUserSummary(OTHER_USER_ID)).thenThrow(new RuntimeException("User not found"));

            assertThatThrownBy(() -> rsvpService.addCoHost(HOST_ID, EVENT_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("User not found with id: " + OTHER_USER_ID);
        }

        @Test
        @DisplayName("Event not found - throws ValidationException")
        void addCoHost_EventNotFound() {
            AddCoHostRequest request = AddCoHostRequest.builder()
                    .userId(OTHER_USER_ID)
                    .build();

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> rsvpService.addCoHost(HOST_ID, EVENT_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Event not found with id: " + EVENT_ID);
        }
    }

    // ==================== REMOVE CO-HOST ====================

    @Nested
    @DisplayName("removeCoHost")
    class RemoveCoHostTests {

        @Test
        @DisplayName("Success - remove co-host")
        void removeCoHost_Success() {
            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(coHostRepository.findByEventIdAndUserId(EVENT_ID, OTHER_USER_ID))
                    .thenReturn(Optional.of(testCoHost));
            doNothing().when(coHostRepository).delete(testCoHost);

            rsvpService.removeCoHost(HOST_ID, EVENT_ID, OTHER_USER_ID);

            verify(coHostRepository).delete(testCoHost);
        }

        @Test
        @DisplayName("Not host - throws ForbiddenException")
        void removeCoHost_NotHost() {
            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));

            assertThatThrownBy(() -> rsvpService.removeCoHost(USER_ID, EVENT_ID, OTHER_USER_ID))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Only the event host can manage co-hosts");

            verify(coHostRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Co-host not found - throws ValidationException")
        void removeCoHost_NotFound() {
            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(coHostRepository.findByEventIdAndUserId(EVENT_ID, OTHER_USER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> rsvpService.removeCoHost(HOST_ID, EVENT_ID, OTHER_USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Co-host not found");
        }

        @Test
        @DisplayName("Event not found - throws ValidationException")
        void removeCoHost_EventNotFound() {
            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> rsvpService.removeCoHost(HOST_ID, EVENT_ID, OTHER_USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Event not found with id: " + EVENT_ID);
        }
    }

    // ==================== GET CO-HOSTS ====================

    @Nested
    @DisplayName("getCoHosts")
    class GetCoHostsTests {

        @Test
        @DisplayName("Success - returns co-hosts")
        void getCoHosts_Success() {
            UserSummary coHostSummary = UserSummary.builder()
                    .id(OTHER_USER_ID)
                    .username("cohost")
                    .build();

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(coHostRepository.findByEventId(EVENT_ID)).thenReturn(List.of(testCoHost));
            when(userServiceClient.getUserSummary(OTHER_USER_ID)).thenReturn(coHostSummary);

            List<CoHostResponse> response = rsvpService.getCoHosts(EVENT_ID);

            assertThat(response).hasSize(1);
            assertThat(response.get(0).getUserId()).isEqualTo(OTHER_USER_ID);
        }

        @Test
        @DisplayName("Success - empty list")
        void getCoHosts_Empty() {
            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(coHostRepository.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());

            List<CoHostResponse> response = rsvpService.getCoHosts(EVENT_ID);

            assertThat(response).isEmpty();
        }

        @Test
        @DisplayName("Null eventId - throws ValidationException")
        void getCoHosts_NullEventId() {
            assertThatThrownBy(() -> rsvpService.getCoHosts(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Event ID is required");
        }

        @Test
        @DisplayName("Event not found - throws ValidationException")
        void getCoHosts_EventNotFound() {
            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> rsvpService.getCoHosts(EVENT_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Event not found with id: " + EVENT_ID);
        }
    }

    // ==================== HELPER METHODS ====================

    @Nested
    @DisplayName("Helper Methods")
    class HelperMethodTests {

        @Test
        @DisplayName("fetchUserSummary - returns fallback on exception")
        void fetchUserSummary_Fallback() {
            when(rsvpRepository.findByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.of(testRsvp));
            when(userServiceClient.getUserSummary(USER_ID))
                    .thenThrow(new RuntimeException("Service unavailable"));

            RsvpResponse response = rsvpService.getMyRsvpStatus(USER_ID, EVENT_ID);

            assertThat(response.getUser()).isNotNull();
            assertThat(response.getUser().getId()).isEqualTo(USER_ID);
            assertThat(response.getUser().getUsername()).isEqualTo("Unknown");
        }

        @Test
        @DisplayName("mapToRsvpResponse - maps all fields")
        void mapToRsvpResponse_AllFields() {
            testRsvp.setGuestCount(3);
            testRsvp.setNote("Test note");
            testRsvp.setCheckedInAt(LocalDateTime.now());
            testRsvp.setNotificationsEnabled(false);

            when(rsvpRepository.findByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.of(testRsvp));
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            RsvpResponse response = rsvpService.getMyRsvpStatus(USER_ID, EVENT_ID);

            assertThat(response.getId()).isEqualTo(RSVP_ID);
            assertThat(response.getEventId()).isEqualTo(EVENT_ID);
            assertThat(response.getUserId()).isEqualTo(USER_ID);
            assertThat(response.getStatus()).isEqualTo(RsvpStatus.GOING);
            assertThat(response.getGuestCount()).isEqualTo(3);
            assertThat(response.getNote()).isEqualTo("Test note");
            assertThat(response.getCheckedInAt()).isNotNull();
            assertThat(response.getNotificationsEnabled()).isFalse();
            assertThat(response.getUser()).isNotNull();
        }

        @Test
        @DisplayName("mapToCoHostResponse - maps all fields")
        void mapToCoHostResponse_AllFields() {
            UserSummary coHostSummary = UserSummary.builder()
                    .id(OTHER_USER_ID)
                    .username("cohost")
                    .email("test@gmail.com")
                    .build();

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(coHostRepository.findByEventId(EVENT_ID)).thenReturn(List.of(testCoHost));
            when(userServiceClient.getUserSummary(OTHER_USER_ID)).thenReturn(coHostSummary);

            List<CoHostResponse> response = rsvpService.getCoHosts(EVENT_ID);

            assertThat(response).hasSize(1);
            CoHostResponse coHost = response.get(0);
            assertThat(coHost.getId()).isEqualTo(COHOST_ID);
            assertThat(coHost.getEventId()).isEqualTo(EVENT_ID);
            assertThat(coHost.getUserId()).isEqualTo(OTHER_USER_ID);
            assertThat(coHost.getUser()).isNotNull();
            assertThat(coHost.getUser().getUsername()).isEqualTo("cohost");
        }

        @Test
        @DisplayName("updateEventCounts - INVITED to GOING")
        void updateEventCounts_InvitedToGoing() {
            RsvpRequest request = RsvpRequest.builder()
                    .status(RsvpStatus.GOING)
                    .build();

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(rsvpRepository.findByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
            when(rsvpRepository.save(any(EventRsvp.class))).thenAnswer(invocation -> {
                EventRsvp saved = invocation.getArgument(0);
                saved.setId(RSVP_ID);
                return saved;
            });
            when(eventRepository.save(any(Event.class))).thenReturn(testEvent);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            rsvpService.rsvp(USER_ID, EVENT_ID, request);

            // INVITED doesn't decrement anything, GOING increments goingCount
            verify(eventRepository).save(argThat(event -> event.getGoingCount() == 11));
        }

        @Test
        @DisplayName("updateEventCounts - GOING to NOT_GOING")
        void updateEventCounts_GoingToNotGoing() {
            testRsvp.setStatus(RsvpStatus.GOING);
            RsvpRequest request = RsvpRequest.builder()
                    .status(RsvpStatus.NOT_GOING)
                    .build();

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(rsvpRepository.findByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.of(testRsvp));
            when(rsvpRepository.save(any(EventRsvp.class))).thenReturn(testRsvp);
            when(eventRepository.save(any(Event.class))).thenReturn(testEvent);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            rsvpService.rsvp(USER_ID, EVENT_ID, request);

            // GOING decrements, NOT_GOING doesn't increment
            verify(eventRepository).save(argThat(event -> event.getGoingCount() == 9));
        }
    }
}