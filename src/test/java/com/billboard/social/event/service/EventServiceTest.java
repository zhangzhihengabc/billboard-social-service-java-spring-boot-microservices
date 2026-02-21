package com.billboard.social.event.service;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.dto.UserSummary;
import com.billboard.social.common.exception.ForbiddenException;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.common.client.UserServiceClient;
import com.billboard.social.common.security.InputValidator;
import com.billboard.social.event.dto.request.EventRequests.CreateEventRequest;
import com.billboard.social.event.dto.request.EventRequests.UpdateEventRequest;
import com.billboard.social.event.dto.response.EventResponses.EventResponse;
import com.billboard.social.event.dto.response.EventResponses.EventSummaryResponse;
import com.billboard.social.event.entity.*;
import com.billboard.social.event.entity.enums.*;
import com.billboard.social.event.event.EventEventPublisher;
import com.billboard.social.event.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventRsvpRepository rsvpRepository;

    @Mock
    private EventCoHostRepository coHostRepository;

    @Mock
    private EventCategoryRepository categoryRepository;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private EventEventPublisher eventPublisher;

    @InjectMocks
    private EventService eventService;

    // Test constants
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID EVENT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID CATEGORY_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID GROUP_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private Event testEvent;
    private EventCategory testCategory;
    private UserSummary testUserSummary;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(eventService, "maxUserEvents", 100);

        testCategory = EventCategory.builder()
                .id(CATEGORY_ID)
                .name("Conference")
                .slug("conference")
                .isActive(true)
                .build();

        testEvent = Event.builder()
                .id(EVENT_ID)
                .title("Test Event")
                .slug("test-event")
                .description("Test description")
                .hostId(USER_ID)
                .categoryId(CATEGORY_ID)
                .eventType(EventType.IN_PERSON)
                .visibility(EventVisibility.PUBLIC)
                .status(EventStatus.DRAFT)
                .startTime(LocalDateTime.now().plusDays(7))
                .endTime(LocalDateTime.now().plusDays(7).plusHours(2))
                .timezone("UTC")
                .isAllDay(false)
                .venueName("Test Venue")
                .city("Test City")
                .maxAttendees(100)
                .goingCount(10)
                .maybeCount(5)
                .invitedCount(20)
                .isTicketed(false)
                .allowGuests(true)
                .guestsPerRsvp(2)
                .showGuestList(true)
                .allowComments(true)
                .requireApproval(false)
                .acceptingRsvps(true)
                .build();
        testEvent.setCreatedAt(LocalDateTime.now());

        testUserSummary = UserSummary.builder()
                .id(USER_ID)
                .username("testuser")
                .email("test@gmail.com")
                .build();
    }

    // ==================== CREATE EVENT ====================

    @Nested
    @DisplayName("createEvent")
    class CreateEventTests {

        @Test
        @DisplayName("Success - creates event with all fields")
        void createEvent_Success() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                CreateEventRequest request = CreateEventRequest.builder()
                        .title("New Event")
                        .description("Event description")
                        .startTime(LocalDateTime.now().plusDays(7))
                        .endTime(LocalDateTime.now().plusDays(7).plusHours(2))
                        .venueName("Venue Name")
                        .address("123 Main St")
                        .city("City")
                        .country("Country")
                        .categoryId(CATEGORY_ID)
                        .eventType(EventType.IN_PERSON)
                        .visibility(EventVisibility.PUBLIC)
                        .maxAttendees(50)
                        .isTicketed(true)
                        .ticketPrice(BigDecimal.valueOf(25.00))
                        .ticketCurrency("USD")
                        .allowGuests(true)
                        .guestsPerRsvp(2)
                        .showGuestList(true)
                        .allowComments(true)
                        .requireApproval(false)
                        .build();

                mockedValidator.when(() -> InputValidator.validateName("New Event", "Title"))
                        .thenReturn("New Event");
                mockedValidator.when(() -> InputValidator.validateText("Event description", "Description", 10000))
                        .thenReturn("Event description");
                mockedValidator.when(() -> InputValidator.validateText("Venue Name", "Venue name", 200))
                        .thenReturn("Venue Name");
                mockedValidator.when(() -> InputValidator.validateText("123 Main St", "Address", 500))
                        .thenReturn("123 Main St");

                when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
                when(eventRepository.countByHostId(USER_ID)).thenReturn(5L);
                when(eventRepository.existsBySlug(anyString())).thenReturn(false);
                when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
                    Event saved = invocation.getArgument(0);
                    saved.setId(EVENT_ID);
                    saved.setCreatedAt(LocalDateTime.now());
                    return saved;
                });
                when(rsvpRepository.save(any(EventRsvp.class))).thenReturn(null);
                when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);
                when(coHostRepository.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());

                EventResponse response = eventService.createEvent(USER_ID, request);

                assertThat(response).isNotNull();
                assertThat(response.getId()).isEqualTo(EVENT_ID);
                verify(eventRepository, times(2)).save(any(Event.class));
                verify(rsvpRepository).save(any(EventRsvp.class));
                verify(eventPublisher).publishEventCreated(any(Event.class));
            }
        }

        @Test
        @DisplayName("Success - uses provided timezone")
        void createEvent_WithTimezone() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                CreateEventRequest request = CreateEventRequest.builder()
                        .title("Event with Timezone")
                        .startTime(LocalDateTime.now().plusDays(7))
                        .timezone("America/New_York")
                        .build();

                mockedValidator.when(() -> InputValidator.validateName("Event with Timezone", "Title"))
                        .thenReturn("Event with Timezone");

                when(eventRepository.countByHostId(USER_ID)).thenReturn(0L);
                when(eventRepository.existsBySlug(anyString())).thenReturn(false);
                when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
                    Event saved = invocation.getArgument(0);
                    assertThat(saved.getTimezone()).isEqualTo("America/New_York");
                    saved.setId(EVENT_ID);
                    saved.setCreatedAt(LocalDateTime.now());
                    return saved;
                });
                when(rsvpRepository.save(any(EventRsvp.class))).thenReturn(null);
                when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);
                when(coHostRepository.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());

                eventService.createEvent(USER_ID, request);
            }
        }

        @Test
        @DisplayName("Success - null timezone defaults to UTC")
        void createEvent_NullTimezoneDefaultsToUtc() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                CreateEventRequest request = CreateEventRequest.builder()
                        .title("Event without Timezone")
                        .startTime(LocalDateTime.now().plusDays(7))
                        // timezone is null
                        .build();

                mockedValidator.when(() -> InputValidator.validateName("Event without Timezone", "Title"))
                        .thenReturn("Event without Timezone");

                when(eventRepository.countByHostId(USER_ID)).thenReturn(0L);
                when(eventRepository.existsBySlug(anyString())).thenReturn(false);
                when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
                    Event saved = invocation.getArgument(0);
                    assertThat(saved.getTimezone()).isEqualTo("UTC");
                    saved.setId(EVENT_ID);
                    saved.setCreatedAt(LocalDateTime.now());
                    return saved;
                });
                when(rsvpRepository.save(any(EventRsvp.class))).thenReturn(null);
                when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);
                when(coHostRepository.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());

                eventService.createEvent(USER_ID, request);
            }
        }

        @Test
        @DisplayName("Success - uses provided isAllDay true")
        void createEvent_WithIsAllDayTrue() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                CreateEventRequest request = CreateEventRequest.builder()
                        .title("All Day Event")
                        .startTime(LocalDateTime.now().plusDays(7))
                        .isAllDay(true)
                        .build();

                mockedValidator.when(() -> InputValidator.validateName("All Day Event", "Title"))
                        .thenReturn("All Day Event");

                when(eventRepository.countByHostId(USER_ID)).thenReturn(0L);
                when(eventRepository.existsBySlug(anyString())).thenReturn(false);
                when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
                    Event saved = invocation.getArgument(0);
                    assertThat(saved.getIsAllDay()).isTrue();
                    saved.setId(EVENT_ID);
                    saved.setCreatedAt(LocalDateTime.now());
                    return saved;
                });
                when(rsvpRepository.save(any(EventRsvp.class))).thenReturn(null);
                when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);
                when(coHostRepository.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());

                eventService.createEvent(USER_ID, request);
            }
        }

        @Test
        @DisplayName("Success - null isAllDay defaults to false")
        void createEvent_NullIsAllDayDefaultsToFalse() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                CreateEventRequest request = CreateEventRequest.builder()
                        .title("Regular Event")
                        .startTime(LocalDateTime.now().plusDays(7))
                        // isAllDay is null
                        .build();

                mockedValidator.when(() -> InputValidator.validateName("Regular Event", "Title"))
                        .thenReturn("Regular Event");

                when(eventRepository.countByHostId(USER_ID)).thenReturn(0L);
                when(eventRepository.existsBySlug(anyString())).thenReturn(false);
                when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
                    Event saved = invocation.getArgument(0);
                    assertThat(saved.getIsAllDay()).isFalse();
                    saved.setId(EVENT_ID);
                    saved.setCreatedAt(LocalDateTime.now());
                    return saved;
                });
                when(rsvpRepository.save(any(EventRsvp.class))).thenReturn(null);
                when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);
                when(coHostRepository.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());

                eventService.createEvent(USER_ID, request);
            }
        }

        @Test
        @DisplayName("Success - uses provided recurrenceType")
        void createEvent_WithRecurrenceType() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                CreateEventRequest request = CreateEventRequest.builder()
                        .title("Weekly Event")
                        .startTime(LocalDateTime.now().plusDays(7))
                        .recurrenceType(RecurrenceType.WEEKLY)
                        .recurrenceEndDate(LocalDateTime.now().plusMonths(3))
                        .build();

                mockedValidator.when(() -> InputValidator.validateName("Weekly Event", "Title"))
                        .thenReturn("Weekly Event");

                when(eventRepository.countByHostId(USER_ID)).thenReturn(0L);
                when(eventRepository.existsBySlug(anyString())).thenReturn(false);
                when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
                    Event saved = invocation.getArgument(0);
                    assertThat(saved.getRecurrenceType()).isEqualTo(RecurrenceType.WEEKLY);
                    assertThat(saved.getRecurrenceEndDate()).isNotNull();
                    saved.setId(EVENT_ID);
                    saved.setCreatedAt(LocalDateTime.now());
                    return saved;
                });
                when(rsvpRepository.save(any(EventRsvp.class))).thenReturn(null);
                when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);
                when(coHostRepository.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());

                EventResponse response = eventService.createEvent(USER_ID, request);

                assertThat(response).isNotNull();
            }
        }

        @Test
        @DisplayName("Success - null recurrenceType defaults to NONE")
        void createEvent_NullRecurrenceTypeDefaultsToNone() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                CreateEventRequest request = CreateEventRequest.builder()
                        .title("One-time Event")
                        .startTime(LocalDateTime.now().plusDays(7))
                        // recurrenceType is null
                        .build();

                mockedValidator.when(() -> InputValidator.validateName("One-time Event", "Title"))
                        .thenReturn("One-time Event");

                when(eventRepository.countByHostId(USER_ID)).thenReturn(0L);
                when(eventRepository.existsBySlug(anyString())).thenReturn(false);
                when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
                    Event saved = invocation.getArgument(0);
                    assertThat(saved.getRecurrenceType()).isEqualTo(RecurrenceType.NONE);
                    saved.setId(EVENT_ID);
                    saved.setCreatedAt(LocalDateTime.now());
                    return saved;
                });
                when(rsvpRepository.save(any(EventRsvp.class))).thenReturn(null);
                when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);
                when(coHostRepository.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());

                EventResponse response = eventService.createEvent(USER_ID, request);

                assertThat(response).isNotNull();
            }
        }

        @Test
        @DisplayName("Null title - generates random slug")
        void createEvent_NullTitle() {
            // Title is null, so validateName is skipped, generateSlug(null) returns early
            CreateEventRequest request = CreateEventRequest.builder()
                    .startTime(LocalDateTime.now().plusDays(7))
                    .build();  // title is null

            when(eventRepository.countByHostId(USER_ID)).thenReturn(0L);
            // REMOVED: existsBySlug - not called when generateSlug receives null (early return)
            when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
                Event saved = invocation.getArgument(0);
                assertThat(saved.getSlug()).hasSize(8); // Random UUID substring
                saved.setId(EVENT_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(rsvpRepository.save(any(EventRsvp.class))).thenReturn(null);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);
            when(coHostRepository.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());

            EventResponse response = eventService.createEvent(USER_ID, request);

            assertThat(response).isNotNull();
            verify(eventRepository, never()).existsBySlug(anyString());  // Confirms early return path
        }

        @Test
        @DisplayName("generateSlug - blank input returns random slug")
        void generateSlug_BlankInput() throws Exception {
            // isBlank() branch is unreachable via createEvent because validateName throws first
            // Test directly via reflection
            java.lang.reflect.Method method = EventService.class
                    .getDeclaredMethod("generateSlug", String.class);
            method.setAccessible(true);

            String result = (String) method.invoke(eventService, "   ");

            assertThat(result).hasSize(8);
            verifyNoInteractions(eventRepository);  // Confirms early return
        }

        @Test
        @DisplayName("generateSlug - null input returns random slug")
        void generateSlug_NullInput() throws Exception {
            java.lang.reflect.Method method = EventService.class
                    .getDeclaredMethod("generateSlug", String.class);
            method.setAccessible(true);

            String result = (String) method.invoke(eventService, (String) null);

            assertThat(result).hasSize(8);
            verifyNoInteractions(eventRepository);  // Confirms early return
        }

        @Test
        @DisplayName("Title with only special chars - generates random slug after normalization")
        void createEvent_TitleOnlySpecialChars() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                CreateEventRequest request = CreateEventRequest.builder()
                        .title("!@#$%^&*()")  // Only special chars - becomes empty after normalization
                        .startTime(LocalDateTime.now().plusDays(7))
                        .build();

                mockedValidator.when(() -> InputValidator.validateName("!@#$%^&*()", "Title"))
                        .thenReturn("!@#$%^&*()");

                when(eventRepository.countByHostId(USER_ID)).thenReturn(0L);
                when(eventRepository.existsBySlug(anyString())).thenReturn(false);
                when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
                    Event saved = invocation.getArgument(0);
                    assertThat(saved.getSlug()).hasSize(8); // Random UUID substring
                    saved.setId(EVENT_ID);
                    saved.setCreatedAt(LocalDateTime.now());
                    return saved;
                });
                when(rsvpRepository.save(any(EventRsvp.class))).thenReturn(null);
                when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);
                when(coHostRepository.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());

                eventService.createEvent(USER_ID, request);
            }
        }

        @Test
        @DisplayName("Success - creates event with minimal fields (defaults applied)")
        void createEvent_MinimalFields() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                CreateEventRequest request = CreateEventRequest.builder()
                        .title("Minimal Event")
                        .startTime(LocalDateTime.now().plusDays(7))
                        .build();

                mockedValidator.when(() -> InputValidator.validateName("Minimal Event", "Title"))
                        .thenReturn("Minimal Event");

                when(eventRepository.countByHostId(USER_ID)).thenReturn(0L);
                when(eventRepository.existsBySlug(anyString())).thenReturn(false);
                when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
                    Event saved = invocation.getArgument(0);
                    saved.setId(EVENT_ID);
                    saved.setCreatedAt(LocalDateTime.now());
                    // Verify defaults
                    assertThat(saved.getEventType()).isEqualTo(EventType.IN_PERSON);
                    assertThat(saved.getVisibility()).isEqualTo(EventVisibility.PUBLIC);
                    assertThat(saved.getStatus()).isEqualTo(EventStatus.DRAFT);
                    assertThat(saved.getTimezone()).isEqualTo("UTC");
                    assertThat(saved.getIsAllDay()).isFalse();
                    assertThat(saved.getIsTicketed()).isFalse();
                    assertThat(saved.getRecurrenceType()).isEqualTo(RecurrenceType.NONE);
                    assertThat(saved.getAllowGuests()).isTrue();
                    assertThat(saved.getGuestsPerRsvp()).isEqualTo(1);
                    assertThat(saved.getShowGuestList()).isTrue();
                    assertThat(saved.getAllowComments()).isTrue();
                    assertThat(saved.getRequireApproval()).isFalse();
                    return saved;
                });
                when(rsvpRepository.save(any(EventRsvp.class))).thenReturn(null);
                when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);
                when(coHostRepository.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());

                EventResponse response = eventService.createEvent(USER_ID, request);

                assertThat(response).isNotNull();
            }
        }

        @Test
        @DisplayName("Null request - throws ValidationException")
        void createEvent_NullRequest() {
            assertThatThrownBy(() -> eventService.createEvent(USER_ID, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Request body is required");

            verifyNoInteractions(eventRepository);
        }

        @Test
        @DisplayName("Category not found - throws ValidationException")
        void createEvent_CategoryNotFound() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                CreateEventRequest request = CreateEventRequest.builder()
                        .title("Event")
                        .startTime(LocalDateTime.now().plusDays(7))
                        .categoryId(CATEGORY_ID)
                        .build();

                mockedValidator.when(() -> InputValidator.validateName("Event", "Title"))
                        .thenReturn("Event");

                when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.empty());

                assertThatThrownBy(() -> eventService.createEvent(USER_ID, request))
                        .isInstanceOf(ValidationException.class)
                        .hasMessage("Category not found with id: " + CATEGORY_ID);
            }
        }

        @Test
        @DisplayName("Maximum event limit reached - throws ValidationException")
        void createEvent_MaxLimitReached() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                CreateEventRequest request = CreateEventRequest.builder()
                        .title("Event")
                        .startTime(LocalDateTime.now().plusDays(7))
                        .build();

                mockedValidator.when(() -> InputValidator.validateName("Event", "Title"))
                        .thenReturn("Event");

                when(eventRepository.countByHostId(USER_ID)).thenReturn(100L); // At limit

                assertThatThrownBy(() -> eventService.createEvent(USER_ID, request))
                        .isInstanceOf(ValidationException.class)
                        .hasMessage("Maximum event limit reached (100)");
            }
        }

        @Test
        @DisplayName("Slug collision - generates unique slug")
        void createEvent_SlugCollision() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                CreateEventRequest request = CreateEventRequest.builder()
                        .title("Test Event")
                        .startTime(LocalDateTime.now().plusDays(7))
                        .build();

                mockedValidator.when(() -> InputValidator.validateName("Test Event", "Title"))
                        .thenReturn("Test Event");

                when(eventRepository.countByHostId(USER_ID)).thenReturn(0L);
                when(eventRepository.existsBySlug("test-event")).thenReturn(true);
                when(eventRepository.existsBySlug("test-event-1")).thenReturn(true);
                when(eventRepository.existsBySlug("test-event-2")).thenReturn(false);
                when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
                    Event saved = invocation.getArgument(0);
                    assertThat(saved.getSlug()).isEqualTo("test-event-2");
                    saved.setId(EVENT_ID);
                    saved.setCreatedAt(LocalDateTime.now());
                    return saved;
                });
                when(rsvpRepository.save(any(EventRsvp.class))).thenReturn(null);
                when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);
                when(coHostRepository.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());

                eventService.createEvent(USER_ID, request);

                verify(eventRepository, times(3)).existsBySlug(anyString());
            }
        }

        @Test
        @DisplayName("Title with special chars - slug normalized")
        void createEvent_SpecialCharsInTitle() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                CreateEventRequest request = CreateEventRequest.builder()
                        .title("Test & Event's Special!")
                        .startTime(LocalDateTime.now().plusDays(7))
                        .build();

                mockedValidator.when(() -> InputValidator.validateName("Test & Event's Special!", "Title"))
                        .thenReturn("Test & Event's Special!");

                when(eventRepository.countByHostId(USER_ID)).thenReturn(0L);
                when(eventRepository.existsBySlug(anyString())).thenReturn(false);
                when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
                    Event saved = invocation.getArgument(0);
                    assertThat(saved.getSlug()).doesNotContain("&", "'", "!");
                    saved.setId(EVENT_ID);
                    saved.setCreatedAt(LocalDateTime.now());
                    return saved;
                });
                when(rsvpRepository.save(any(EventRsvp.class))).thenReturn(null);
                when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);
                when(coHostRepository.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());

                eventService.createEvent(USER_ID, request);
            }
        }

        @Test
        @DisplayName("Null description - not validated")
        void createEvent_NullDescription() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                CreateEventRequest request = CreateEventRequest.builder()
                        .title("Event")
                        .startTime(LocalDateTime.now().plusDays(7))
                        .build();

                mockedValidator.when(() -> InputValidator.validateName("Event", "Title"))
                        .thenReturn("Event");

                when(eventRepository.countByHostId(USER_ID)).thenReturn(0L);
                when(eventRepository.existsBySlug(anyString())).thenReturn(false);
                when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
                    Event saved = invocation.getArgument(0);
                    saved.setId(EVENT_ID);
                    saved.setCreatedAt(LocalDateTime.now());
                    return saved;
                });
                when(rsvpRepository.save(any(EventRsvp.class))).thenReturn(null);
                when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);
                when(coHostRepository.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());

                eventService.createEvent(USER_ID, request);

                // validateText should NOT be called for description
                mockedValidator.verify(() -> InputValidator.validateText(anyString(), eq("Description"), anyInt()), never());
            }
        }

        @Test
        @DisplayName("Null venueName - not validated")
        void createEvent_NullVenueName() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                CreateEventRequest request = CreateEventRequest.builder()
                        .title("Event")
                        .startTime(LocalDateTime.now().plusDays(7))
                        .build();

                mockedValidator.when(() -> InputValidator.validateName("Event", "Title"))
                        .thenReturn("Event");

                when(eventRepository.countByHostId(USER_ID)).thenReturn(0L);
                when(eventRepository.existsBySlug(anyString())).thenReturn(false);
                when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
                    Event saved = invocation.getArgument(0);
                    saved.setId(EVENT_ID);
                    saved.setCreatedAt(LocalDateTime.now());
                    return saved;
                });
                when(rsvpRepository.save(any(EventRsvp.class))).thenReturn(null);
                when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);
                when(coHostRepository.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());

                eventService.createEvent(USER_ID, request);

                mockedValidator.verify(() -> InputValidator.validateText(anyString(), eq("Venue name"), anyInt()), never());
            }
        }

        @Test
        @DisplayName("Null address - not validated")
        void createEvent_NullAddress() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                CreateEventRequest request = CreateEventRequest.builder()
                        .title("Event")
                        .startTime(LocalDateTime.now().plusDays(7))
                        .build();

                mockedValidator.when(() -> InputValidator.validateName("Event", "Title"))
                        .thenReturn("Event");

                when(eventRepository.countByHostId(USER_ID)).thenReturn(0L);
                when(eventRepository.existsBySlug(anyString())).thenReturn(false);
                when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
                    Event saved = invocation.getArgument(0);
                    saved.setId(EVENT_ID);
                    saved.setCreatedAt(LocalDateTime.now());
                    return saved;
                });
                when(rsvpRepository.save(any(EventRsvp.class))).thenReturn(null);
                when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);
                when(coHostRepository.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());

                eventService.createEvent(USER_ID, request);

                mockedValidator.verify(() -> InputValidator.validateText(anyString(), eq("Address"), anyInt()), never());
            }
        }

        @Test
        @DisplayName("Null categoryId - skips category validation")
        void createEvent_NullCategoryId() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                CreateEventRequest request = CreateEventRequest.builder()
                        .title("Event")
                        .startTime(LocalDateTime.now().plusDays(7))
                        .build();

                mockedValidator.when(() -> InputValidator.validateName("Event", "Title"))
                        .thenReturn("Event");

                when(eventRepository.countByHostId(USER_ID)).thenReturn(0L);
                when(eventRepository.existsBySlug(anyString())).thenReturn(false);
                when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
                    Event saved = invocation.getArgument(0);
                    saved.setId(EVENT_ID);
                    saved.setCreatedAt(LocalDateTime.now());
                    return saved;
                });
                when(rsvpRepository.save(any(EventRsvp.class))).thenReturn(null);
                when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);
                when(coHostRepository.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());

                eventService.createEvent(USER_ID, request);

                verifyNoInteractions(categoryRepository);
            }
        }
    }

    // ==================== UPDATE EVENT ====================

    @Nested
    @DisplayName("updateEvent")
    class UpdateEventTests {

        @Test
        @DisplayName("Success - updates all fields as host")
        void updateEvent_Success() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                UpdateEventRequest request = UpdateEventRequest.builder()
                        .title("Updated Title")
                        .description("Updated description")
                        .venueName("Updated Venue")
                        .address("Updated Address")
                        .acceptingRsvps(false)
                        .categoryId(CATEGORY_ID)
                        .eventType(EventType.ONLINE)
                        .visibility(EventVisibility.PRIVATE)
                        .status(EventStatus.PUBLISHED)
                        .startTime(LocalDateTime.now().plusDays(14))
                        .endTime(LocalDateTime.now().plusDays(14).plusHours(3))
                        .timezone("PST")
                        .isAllDay(true)
                        .city("New City")
                        .country("New Country")
                        .maxAttendees(200)
                        .allowGuests(false)
                        .build();

                mockedValidator.when(() -> InputValidator.validateName("Updated Title", "Title"))
                        .thenReturn("Updated Title");
                mockedValidator.when(() -> InputValidator.validateText("Updated description", "Description", 10000))
                        .thenReturn("Updated description");
                mockedValidator.when(() -> InputValidator.validateText("Updated Venue", "Venue name", 200))
                        .thenReturn("Updated Venue");
                mockedValidator.when(() -> InputValidator.validateText("Updated Address", "Address", 500))
                        .thenReturn("Updated Address");

                when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
                when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
                when(eventRepository.existsBySlug(anyString())).thenReturn(false);
                when(eventRepository.save(any(Event.class))).thenReturn(testEvent);
                when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);
                when(coHostRepository.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());

                EventResponse response = eventService.updateEvent(USER_ID, EVENT_ID, request);

                assertThat(response).isNotNull();
                verify(eventRepository).save(any(Event.class));
                verify(eventPublisher).publishEventUpdated(any(Event.class));
            }
        }

        @Test
        @DisplayName("Success - updates coverImageUrl")
        void updateEvent_CoverImageUrl() {
            UpdateEventRequest request = UpdateEventRequest.builder()
                    .coverImageUrl("https://example.com/new-image.jpg")
                    .build();

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
                Event saved = invocation.getArgument(0);
                assertThat(saved.getCoverImageUrl()).isEqualTo("https://example.com/new-image.jpg");
                return saved;
            });
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);
            when(coHostRepository.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());

            EventResponse response = eventService.updateEvent(USER_ID, EVENT_ID, request);

            assertThat(response).isNotNull();
            verify(eventPublisher).publishEventUpdated(any(Event.class));
        }

        @Test
        @DisplayName("Success - updates as co-host with edit permission")
        void updateEvent_AsCoHost() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                testEvent.setHostId(OTHER_USER_ID); // Different host
                UpdateEventRequest request = UpdateEventRequest.builder()
                        .title("Updated by Co-Host")
                        .build();

                mockedValidator.when(() -> InputValidator.validateName("Updated by Co-Host", "Title"))
                        .thenReturn("Updated by Co-Host");

                EventCoHost coHost = EventCoHost.builder()
                        .event(testEvent)
                        .userId(USER_ID)
                        .canEdit(true)
                        .build();

                when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
                when(coHostRepository.findByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.of(coHost));
                when(eventRepository.existsBySlug(anyString())).thenReturn(false);
                when(eventRepository.save(any(Event.class))).thenReturn(testEvent);
                when(userServiceClient.getUserSummary(OTHER_USER_ID)).thenReturn(testUserSummary);
                when(coHostRepository.findByEventId(EVENT_ID)).thenReturn(List.of(coHost));
                when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

                EventResponse response = eventService.updateEvent(USER_ID, EVENT_ID, request);

                assertThat(response).isNotNull();
            }
        }

        @Test
        @DisplayName("Null request - throws ValidationException")
        void updateEvent_NullRequest() {
            assertThatThrownBy(() -> eventService.updateEvent(USER_ID, EVENT_ID, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Request body is required");

            verifyNoInteractions(eventRepository);
        }

        @Test
        @DisplayName("Event not found - throws ValidationException")
        void updateEvent_NotFound() {
            UpdateEventRequest request = UpdateEventRequest.builder()
                    .title("Updated")
                    .build();

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> eventService.updateEvent(USER_ID, EVENT_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Event not found with id: " + EVENT_ID);
        }

        @Test
        @DisplayName("No edit permission - throws ForbiddenException")
        void updateEvent_NoPermission() {
            testEvent.setHostId(OTHER_USER_ID); // Different host
            UpdateEventRequest request = UpdateEventRequest.builder()
                    .title("Updated")
                    .build();

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(coHostRepository.findByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> eventService.updateEvent(USER_ID, EVENT_ID, request))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("No edit permission for this event");
        }

        @Test
        @DisplayName("Co-host without edit permission - throws ForbiddenException")
        void updateEvent_CoHostNoEditPermission() {
            testEvent.setHostId(OTHER_USER_ID);
            UpdateEventRequest request = UpdateEventRequest.builder()
                    .title("Updated")
                    .build();

            EventCoHost coHost = EventCoHost.builder()
                    .event(testEvent)
                    .userId(USER_ID)
                    .canEdit(false) // No edit permission
                    .build();

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(coHostRepository.findByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.of(coHost));

            assertThatThrownBy(() -> eventService.updateEvent(USER_ID, EVENT_ID, request))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("No edit permission for this event");
        }

        @Test
        @DisplayName("Category not found - throws ValidationException")
        void updateEvent_CategoryNotFound() {
            UpdateEventRequest request = UpdateEventRequest.builder()
                    .categoryId(CATEGORY_ID)
                    .build();

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> eventService.updateEvent(USER_ID, EVENT_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Category not found with id: " + CATEGORY_ID);
        }

        @Test
        @DisplayName("Null title - not updated")
        void updateEvent_NullTitle() {
            UpdateEventRequest request = UpdateEventRequest.builder()
                    .description("Only description")
                    .build();

            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                mockedValidator.when(() -> InputValidator.validateText("Only description", "Description", 10000))
                        .thenReturn("Only description");

                when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
                when(eventRepository.save(any(Event.class))).thenReturn(testEvent);
                when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);
                when(coHostRepository.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());

                eventService.updateEvent(USER_ID, EVENT_ID, request);

                mockedValidator.verify(() -> InputValidator.validateName(anyString(), eq("Title")), never());
            }
        }

        @Test
        @DisplayName("Null fields - not updated")
        void updateEvent_AllNullFields() {
            UpdateEventRequest request = UpdateEventRequest.builder().build();

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(eventRepository.save(any(Event.class))).thenReturn(testEvent);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);
            when(coHostRepository.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());

            EventResponse response = eventService.updateEvent(USER_ID, EVENT_ID, request);

            assertThat(response).isNotNull();
        }
    }

    // ==================== PUBLISH EVENT ====================

    @Nested
    @DisplayName("publishEvent")
    class PublishEventTests {

        @Test
        @DisplayName("Success - publishes draft event")
        void publishEvent_Success() {
            testEvent.setStatus(EventStatus.DRAFT);

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(eventRepository.save(any(Event.class))).thenReturn(testEvent);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);
            when(coHostRepository.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());

            EventResponse response = eventService.publishEvent(USER_ID, EVENT_ID);

            assertThat(response).isNotNull();
            verify(eventRepository).save(argThat(e -> e.getStatus() == EventStatus.PUBLISHED));
            verify(eventPublisher).publishEventPublished(any(Event.class));
        }

        @Test
        @DisplayName("Not draft - throws ValidationException")
        void publishEvent_NotDraft() {
            testEvent.setStatus(EventStatus.PUBLISHED);

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));

            assertThatThrownBy(() -> eventService.publishEvent(USER_ID, EVENT_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Only draft events can be published");
        }

        @Test
        @DisplayName("No edit permission - throws ForbiddenException")
        void publishEvent_NoPermission() {
            testEvent.setHostId(OTHER_USER_ID);
            testEvent.setStatus(EventStatus.DRAFT);

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(coHostRepository.findByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> eventService.publishEvent(USER_ID, EVENT_ID))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("No edit permission for this event");
        }

        @Test
        @DisplayName("Event not found - throws ValidationException")
        void publishEvent_NotFound() {
            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> eventService.publishEvent(USER_ID, EVENT_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Event not found with id: " + EVENT_ID);
        }
    }

    // ==================== CANCEL EVENT ====================

    @Nested
    @DisplayName("cancelEvent")
    class CancelEventTests {

        @Test
        @DisplayName("Success - cancels event with reason")
        void cancelEvent_WithReason() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                mockedValidator.when(() -> InputValidator.validateText("Weather conditions", "Reason", 500))
                        .thenReturn("Weather conditions");

                when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
                when(eventRepository.save(any(Event.class))).thenReturn(testEvent);
                when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);
                when(coHostRepository.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());

                EventResponse response = eventService.cancelEvent(USER_ID, EVENT_ID, "Weather conditions");

                assertThat(response).isNotNull();
                verify(eventRepository).save(argThat(e -> e.getStatus() == EventStatus.CANCELLED));
                verify(eventPublisher).publishEventCancelled(any(Event.class), eq("Weather conditions"));
            }
        }

        @Test
        @DisplayName("Success - cancels event without reason")
        void cancelEvent_WithoutReason() {
            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(eventRepository.save(any(Event.class))).thenReturn(testEvent);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);
            when(coHostRepository.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());

            EventResponse response = eventService.cancelEvent(USER_ID, EVENT_ID, null);

            assertThat(response).isNotNull();
            verify(eventPublisher).publishEventCancelled(any(Event.class), isNull());
        }

        @Test
        @DisplayName("Success - blank reason treated as no reason")
        void cancelEvent_BlankReason() {
            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(eventRepository.save(any(Event.class))).thenReturn(testEvent);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);
            when(coHostRepository.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());

            eventService.cancelEvent(USER_ID, EVENT_ID, "   ");

            // Blank reason should not trigger validateText
        }

        @Test
        @DisplayName("Not host - throws ForbiddenException")
        void cancelEvent_NotHost() {
            testEvent.setHostId(OTHER_USER_ID);

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));

            assertThatThrownBy(() -> eventService.cancelEvent(USER_ID, EVENT_ID, "Reason"))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Only host can cancel the event");
        }

        @Test
        @DisplayName("Event not found - throws ValidationException")
        void cancelEvent_NotFound() {
            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> eventService.cancelEvent(USER_ID, EVENT_ID, "Reason"))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Event not found with id: " + EVENT_ID);
        }
    }

    // ==================== DELETE EVENT ====================

    @Nested
    @DisplayName("deleteEvent")
    class DeleteEventTests {

        @Test
        @DisplayName("Success - deletes event and related data")
        void deleteEvent_Success() {
            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            doNothing().when(rsvpRepository).deleteByEventId(EVENT_ID);
            doNothing().when(coHostRepository).deleteByEventId(EVENT_ID);
            doNothing().when(eventRepository).delete(testEvent);

            eventService.deleteEvent(USER_ID, EVENT_ID);

            verify(rsvpRepository).deleteByEventId(EVENT_ID);
            verify(coHostRepository).deleteByEventId(EVENT_ID);
            verify(eventRepository).delete(testEvent);
            verify(eventPublisher).publishEventDeleted(testEvent);
        }

        @Test
        @DisplayName("Not host - throws ForbiddenException")
        void deleteEvent_NotHost() {
            testEvent.setHostId(OTHER_USER_ID);

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));

            assertThatThrownBy(() -> eventService.deleteEvent(USER_ID, EVENT_ID))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Only host can delete the event");

            verify(eventRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Event not found - throws ValidationException")
        void deleteEvent_NotFound() {
            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> eventService.deleteEvent(USER_ID, EVENT_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Event not found with id: " + EVENT_ID);
        }
    }

    // ==================== GET EVENT ====================

    @Nested
    @DisplayName("getEvent")
    class GetEventTests {

        @Test
        @DisplayName("Success - public event")
        void getEvent_PublicEvent() {
            testEvent.setVisibility(EventVisibility.PUBLIC);

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);
            when(coHostRepository.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());

            EventResponse response = eventService.getEvent(EVENT_ID, USER_ID);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(EVENT_ID);
        }

        @Test
        @DisplayName("Success - public event with anonymous user")
        void getEvent_PublicEventAnonymous() {
            testEvent.setVisibility(EventVisibility.PUBLIC);

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);
            when(coHostRepository.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());

            EventResponse response = eventService.getEvent(EVENT_ID, null);

            assertThat(response).isNotNull();
            assertThat(response.getIsHost()).isNull();
            assertThat(response.getIsCoHost()).isNull();
        }

        @Test
        @DisplayName("Success - private event as host")
        void getEvent_PrivateEventAsHost() {
            testEvent.setVisibility(EventVisibility.PRIVATE);

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);
            when(coHostRepository.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());

            EventResponse response = eventService.getEvent(EVENT_ID, USER_ID);

            assertThat(response).isNotNull();
            assertThat(response.getIsHost()).isTrue();
        }

        @Test
        @DisplayName("Success - private event as RSVP'd user")
        void getEvent_PrivateEventAsRsvp() {
            testEvent.setVisibility(EventVisibility.PRIVATE);
            testEvent.setHostId(OTHER_USER_ID);

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(rsvpRepository.existsByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(true);
            when(userServiceClient.getUserSummary(OTHER_USER_ID)).thenReturn(testUserSummary);
            when(coHostRepository.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());
            when(coHostRepository.existsByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(false);

            EventResponse response = eventService.getEvent(EVENT_ID, USER_ID);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Success - private event as co-host")
        void getEvent_PrivateEventAsCoHost() {
            testEvent.setVisibility(EventVisibility.PRIVATE);
            testEvent.setHostId(OTHER_USER_ID);

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(rsvpRepository.existsByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(false);
            when(coHostRepository.existsByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(true);
            when(userServiceClient.getUserSummary(OTHER_USER_ID)).thenReturn(testUserSummary);
            when(coHostRepository.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());

            EventResponse response = eventService.getEvent(EVENT_ID, USER_ID);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Private event - no access - throws ForbiddenException")
        void getEvent_PrivateEventNoAccess() {
            testEvent.setVisibility(EventVisibility.PRIVATE);
            testEvent.setHostId(OTHER_USER_ID);

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(rsvpRepository.existsByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(false);
            when(coHostRepository.existsByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(false);

            assertThatThrownBy(() -> eventService.getEvent(EVENT_ID, USER_ID))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("No view permission for this event");
        }

        @Test
        @DisplayName("Private event - anonymous user - throws ForbiddenException")
        void getEvent_PrivateEventAnonymous() {
            testEvent.setVisibility(EventVisibility.PRIVATE);

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));

            assertThatThrownBy(() -> eventService.getEvent(EVENT_ID, null))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("No view permission for this event");
        }

        @Test
        @DisplayName("Event not found - throws ValidationException")
        void getEvent_NotFound() {
            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> eventService.getEvent(EVENT_ID, USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Event not found with id: " + EVENT_ID);
        }

        @Test
        @DisplayName("Null eventId - throws ValidationException")
        void getEvent_NullEventId() {
            assertThatThrownBy(() -> eventService.getEvent(null, USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Event ID is required");
        }
    }

    // ==================== GET EVENT BY SLUG ====================

    @Nested
    @DisplayName("getEventBySlug")
    class GetEventBySlugTests {

        @Test
        @DisplayName("Success - returns event")
        void getEventBySlug_Success() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                mockedValidator.when(() -> InputValidator.sanitizeSearchQuery("test-event"))
                        .thenReturn("test-event");

                when(eventRepository.findBySlug("test-event")).thenReturn(Optional.of(testEvent));
                when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);
                when(coHostRepository.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());

                EventResponse response = eventService.getEventBySlug("test-event", USER_ID);

                assertThat(response).isNotNull();
                assertThat(response.getSlug()).isEqualTo("test-event");
            }
        }

        @Test
        @DisplayName("Null slug - throws ValidationException")
        void getEventBySlug_NullSlug() {
            assertThatThrownBy(() -> eventService.getEventBySlug(null, USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Slug is required");
        }

        @Test
        @DisplayName("Blank slug - throws ValidationException")
        void getEventBySlug_BlankSlug() {
            assertThatThrownBy(() -> eventService.getEventBySlug("   ", USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Slug is required");
        }

        @Test
        @DisplayName("Sanitized slug is blank - throws ValidationException")
        void getEventBySlug_SanitizedBlank() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                mockedValidator.when(() -> InputValidator.sanitizeSearchQuery("\u0001\u0002"))
                        .thenReturn("");

                assertThatThrownBy(() -> eventService.getEventBySlug("\u0001\u0002", USER_ID))
                        .isInstanceOf(ValidationException.class)
                        .hasMessage("Invalid slug format");
            }
        }

        @Test
        @DisplayName("Event not found - throws ValidationException")
        void getEventBySlug_NotFound() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                mockedValidator.when(() -> InputValidator.sanitizeSearchQuery("nonexistent"))
                        .thenReturn("nonexistent");

                when(eventRepository.findBySlug("nonexistent")).thenReturn(Optional.empty());

                assertThatThrownBy(() -> eventService.getEventBySlug("nonexistent", USER_ID))
                        .isInstanceOf(ValidationException.class)
                        .hasMessage("Event not found with slug: nonexistent");
            }
        }
    }

    // ==================== GET UPCOMING EVENTS ====================

    @Nested
    @DisplayName("getUpcomingEvents")
    class GetUpcomingEventsTests {

        @Test
        @DisplayName("Success - returns paginated events")
        void getUpcomingEvents_Success() {
            Page<Event> page = new PageImpl<>(List.of(testEvent), PageRequest.of(0, 20), 1);

            when(eventRepository.findUpcomingPublicEvents(any(LocalDateTime.class), any(Pageable.class)))
                    .thenReturn(page);

            PageResponse<EventSummaryResponse> response = eventService.getUpcomingEvents(0, 20);

            assertThat(response).isNotNull();
            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("Success - empty result")
        void getUpcomingEvents_Empty() {
            Page<Event> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);

            when(eventRepository.findUpcomingPublicEvents(any(LocalDateTime.class), any(Pageable.class)))
                    .thenReturn(emptyPage);

            PageResponse<EventSummaryResponse> response = eventService.getUpcomingEvents(0, 20);

            assertThat(response.getContent()).isEmpty();
            assertThat(response.getTotalElements()).isEqualTo(0);
        }
    }

    // ==================== SEARCH EVENTS ====================

    @Nested
    @DisplayName("searchEvents")
    class SearchEventsTests {

        @Test
        @DisplayName("Success - returns search results")
        void searchEvents_Success() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                mockedValidator.when(() -> InputValidator.sanitizeSearchQuery("conference"))
                        .thenReturn("conference");

                Page<Event> page = new PageImpl<>(List.of(testEvent), PageRequest.of(0, 20), 1);
                when(eventRepository.searchEvents(eq("conference"), any(Pageable.class))).thenReturn(page);

                PageResponse<EventSummaryResponse> response = eventService.searchEvents("conference", 0, 20);

                assertThat(response.getContent()).hasSize(1);
            }
        }

        @Test
        @DisplayName("Empty sanitized query - returns empty result")
        void searchEvents_EmptyQuery() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                mockedValidator.when(() -> InputValidator.sanitizeSearchQuery("\u0001\u0002"))
                        .thenReturn("");

                PageResponse<EventSummaryResponse> response = eventService.searchEvents("\u0001\u0002", 0, 20);

                assertThat(response.getContent()).isEmpty();
                assertThat(response.isFirst()).isTrue();
                assertThat(response.isLast()).isTrue();
                assertThat(response.isEmpty()).isTrue();
                assertThat(response.getPage()).isEqualTo(0);
                assertThat(response.getSize()).isEqualTo(20);
                assertThat(response.getTotalElements()).isEqualTo(0);
                assertThat(response.getTotalPages()).isEqualTo(0);

                verifyNoInteractions(eventRepository);
            }
        }
    }

    // ==================== GET POPULAR EVENTS ====================

    @Nested
    @DisplayName("getPopularEvents")
    class GetPopularEventsTests {

        @Test
        @DisplayName("Success - returns popular events")
        void getPopularEvents_Success() {
            Page<Event> page = new PageImpl<>(List.of(testEvent), PageRequest.of(0, 20), 1);

            when(eventRepository.findPopularEvents(any(LocalDateTime.class), any(Pageable.class)))
                    .thenReturn(page);

            PageResponse<EventSummaryResponse> response = eventService.getPopularEvents(0, 20);

            assertThat(response.getContent()).hasSize(1);
        }
    }

    // ==================== GET USER UPCOMING EVENTS ====================

    @Nested
    @DisplayName("getUserUpcomingEvents")
    class GetUserUpcomingEventsTests {

        @Test
        @DisplayName("Success - returns user's upcoming events")
        void getUserUpcomingEvents_Success() {
            Page<Event> page = new PageImpl<>(List.of(testEvent), PageRequest.of(0, 20), 1);

            when(eventRepository.findUserUpcomingEvents(eq(USER_ID), any(LocalDateTime.class), any(Pageable.class)))
                    .thenReturn(page);

            PageResponse<EventSummaryResponse> response = eventService.getUserUpcomingEvents(USER_ID, 0, 20);

            assertThat(response.getContent()).hasSize(1);
        }
    }

    // ==================== GET HOSTED EVENTS ====================

    @Nested
    @DisplayName("getHostedEvents")
    class GetHostedEventsTests {

        @Test
        @DisplayName("Success - returns hosted events sorted by startTime desc")
        void getHostedEvents_Success() {
            Page<Event> page = new PageImpl<>(List.of(testEvent), PageRequest.of(0, 20), 1);

            when(eventRepository.findEventsHostedOrCohosted(eq(USER_ID), any(Pageable.class)))
                    .thenReturn(page);

            PageResponse<EventSummaryResponse> response = eventService.getHostedEvents(USER_ID, 0, 20);

            assertThat(response.getContent()).hasSize(1);
            verify(eventRepository).findEventsHostedOrCohosted(eq(USER_ID),
                    argThat(pageable -> pageable.getSort().getOrderFor("startTime").getDirection() == Sort.Direction.DESC));
        }
    }

    // ==================== HELPER METHOD TESTS ====================

    @Nested
    @DisplayName("Helper Methods")
    class HelperMethodTests {

        @Test
        @DisplayName("fetchUserSummary - returns fallback on exception")
        void fetchUserSummary_Fallback() {
            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(userServiceClient.getUserSummary(USER_ID)).thenThrow(new RuntimeException("Service unavailable"));
            when(coHostRepository.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());

            EventResponse response = eventService.getEvent(EVENT_ID, USER_ID);

            assertThat(response.getHost()).isNotNull();
            assertThat(response.getHost().getId()).isEqualTo(USER_ID);
            assertThat(response.getHost().getUsername()).isEqualTo("Unknown");
        }

        @Test
        @DisplayName("mapToEventResponse - includes category name")
        void mapToEventResponse_WithCategoryName() {
            testEvent.setCategoryId(CATEGORY_ID);

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);
            when(coHostRepository.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());

            EventResponse response = eventService.getEvent(EVENT_ID, USER_ID);

            assertThat(response.getCategoryName()).isEqualTo("Conference");
        }

        @Test
        @DisplayName("mapToEventResponse - null categoryId skips lookup")
        void mapToEventResponse_NullCategoryId() {
            testEvent.setCategoryId(null);

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);
            when(coHostRepository.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());

            EventResponse response = eventService.getEvent(EVENT_ID, USER_ID);

            assertThat(response.getCategoryName()).isNull();
            verify(categoryRepository, never()).findById(any());
        }

        @Test
        @DisplayName("mapToEventResponse - includes co-hosts")
        void mapToEventResponse_WithCoHosts() {
            EventCoHost coHost = EventCoHost.builder()
                    .event(testEvent)  // Use Event entity, not eventId
                    .userId(OTHER_USER_ID)
                    .build();

            UserSummary coHostSummary = UserSummary.builder()
                    .id(OTHER_USER_ID)
                    .username("cohost")
                    .build();

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);
            when(userServiceClient.getUserSummary(OTHER_USER_ID)).thenReturn(coHostSummary);
            when(coHostRepository.findByEventId(EVENT_ID)).thenReturn(List.of(coHost));

            EventResponse response = eventService.getEvent(EVENT_ID, USER_ID);

            assertThat(response.getCoHosts()).hasSize(1);
            assertThat(response.getCoHosts().get(0).getUsername()).isEqualTo("cohost");
        }

        @Test
        @DisplayName("mapToEventResponse - currentUserId null skips user context")
        void mapToEventResponse_NullCurrentUserId() {
            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);
            when(coHostRepository.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());

            EventResponse response = eventService.getEvent(EVENT_ID, null);

            assertThat(response.getIsHost()).isNull();
            assertThat(response.getIsCoHost()).isNull();
        }

        @Test
        @DisplayName("mapToEventSummary - maps all fields")
        void mapToEventSummary_AllFields() {
            Page<Event> page = new PageImpl<>(List.of(testEvent), PageRequest.of(0, 20), 1);

            when(eventRepository.findUpcomingPublicEvents(any(LocalDateTime.class), any(Pageable.class)))
                    .thenReturn(page);

            PageResponse<EventSummaryResponse> response = eventService.getUpcomingEvents(0, 20);

            EventSummaryResponse summary = response.getContent().get(0);
            assertThat(summary.getId()).isEqualTo(EVENT_ID);
            assertThat(summary.getTitle()).isEqualTo("Test Event");
            assertThat(summary.getSlug()).isEqualTo("test-event");
            assertThat(summary.getVenueName()).isEqualTo("Test Venue");
            assertThat(summary.getCity()).isEqualTo("Test City");
            assertThat(summary.getEventType()).isEqualTo(EventType.IN_PERSON);
            assertThat(summary.getGoingCount()).isEqualTo(10);
            assertThat(summary.getIsTicketed()).isFalse();
        }

        @Test
        @DisplayName("generateSlug - handles unicode characters")
        void generateSlug_Unicode() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                CreateEventRequest request = CreateEventRequest.builder()
                        .title("Événement Spécial")
                        .startTime(LocalDateTime.now().plusDays(7))
                        .build();

                mockedValidator.when(() -> InputValidator.validateName("Événement Spécial", "Title"))
                        .thenReturn("Événement Spécial");

                when(eventRepository.countByHostId(USER_ID)).thenReturn(0L);
                when(eventRepository.existsBySlug(anyString())).thenReturn(false);
                when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
                    Event saved = invocation.getArgument(0);
                    assertThat(saved.getSlug()).doesNotContain("é", "É");
                    saved.setId(EVENT_ID);
                    saved.setCreatedAt(LocalDateTime.now());
                    return saved;
                });
                when(rsvpRepository.save(any(EventRsvp.class))).thenReturn(null);
                when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);
                when(coHostRepository.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());

                eventService.createEvent(USER_ID, request);
            }
        }
    }
}