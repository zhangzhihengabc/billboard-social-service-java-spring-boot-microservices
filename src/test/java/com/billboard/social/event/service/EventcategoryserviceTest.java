package com.billboard.social.event.service;

import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.common.security.InputValidator;
import com.billboard.social.event.dto.request.EventRequests.CreateCategoryRequest;
import com.billboard.social.event.dto.request.EventRequests.UpdateCategoryRequest;
import com.billboard.social.event.dto.response.EventResponses.CategoryResponse;
import com.billboard.social.event.entity.EventCategory;
import com.billboard.social.event.repository.EventCategoryRepository;
import com.billboard.social.event.repository.EventRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventcategoryserviceTest {

    @Mock
    private EventCategoryRepository categoryRepository;

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private Eventcategoryservice categoryService;

    // Test constants
    private static final UUID CATEGORY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CATEGORY_ID_2 = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private EventCategory testCategory;

    @BeforeEach
    void setUp() {
        testCategory = EventCategory.builder()
                .id(CATEGORY_ID)
                .name("Conference")
                .slug("conference")
                .description("Professional conferences")
                .icon("calendar")
                .color("#3B82F6")
                .displayOrder(1)
                .isActive(true)
                .eventCount(10)
                .build();
        testCategory.setCreatedAt(LocalDateTime.now());
        testCategory.setUpdatedAt(LocalDateTime.now());
    }

    // ==================== GET ALL ACTIVE CATEGORIES ====================

    @Nested
    @DisplayName("getAllActiveCategories")
    class GetAllActiveCategoriesTests {

        @Test
        @DisplayName("Success - returns list of active categories")
        void getAllActiveCategories_Success() {
            EventCategory category2 = EventCategory.builder()
                    .id(CATEGORY_ID_2)
                    .name("Workshop")
                    .slug("workshop")
                    .displayOrder(2)
                    .isActive(true)
                    .eventCount(5)
                    .build();

            when(categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc())
                    .thenReturn(List.of(testCategory, category2));

            List<CategoryResponse> result = categoryService.getAllActiveCategories();

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getName()).isEqualTo("Conference");
            assertThat(result.get(1).getName()).isEqualTo("Workshop");

            verify(categoryRepository).findByIsActiveTrueOrderByDisplayOrderAsc();
        }

        @Test
        @DisplayName("Success - returns empty list when no active categories")
        void getAllActiveCategories_Empty() {
            when(categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc())
                    .thenReturn(Collections.emptyList());

            List<CategoryResponse> result = categoryService.getAllActiveCategories();

            assertThat(result).isEmpty();
        }
    }

    // ==================== GET ALL CATEGORIES (ADMIN) ====================

    @Nested
    @DisplayName("getAllCategories")
    class GetAllCategoriesTests {

        @Test
        @DisplayName("Success - returns all categories sorted by displayOrder then name")
        void getAllCategories_Success() {
            EventCategory category2 = EventCategory.builder()
                    .id(CATEGORY_ID_2)
                    .name("Workshop")
                    .slug("workshop")
                    .displayOrder(1) // Same display order as testCategory
                    .isActive(false)
                    .eventCount(0)
                    .build();

            EventCategory category3 = EventCategory.builder()
                    .id(UUID.randomUUID())
                    .name("Seminar")
                    .slug("seminar")
                    .displayOrder(2)
                    .isActive(true)
                    .eventCount(3)
                    .build();

            when(categoryRepository.findAll()).thenReturn(List.of(category3, testCategory, category2));

            List<CategoryResponse> result = categoryService.getAllCategories();

            assertThat(result).hasSize(3);
            // displayOrder 1: Conference comes before Workshop (alphabetically)
            assertThat(result.get(0).getName()).isEqualTo("Conference");
            assertThat(result.get(1).getName()).isEqualTo("Workshop");
            // displayOrder 2
            assertThat(result.get(2).getName()).isEqualTo("Seminar");
        }

        @Test
        @DisplayName("Success - returns empty list")
        void getAllCategories_Empty() {
            when(categoryRepository.findAll()).thenReturn(Collections.emptyList());

            List<CategoryResponse> result = categoryService.getAllCategories();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Sorting - different display orders")
        void getAllCategories_SortByDisplayOrder() {
            EventCategory category2 = EventCategory.builder()
                    .id(CATEGORY_ID_2)
                    .name("AAA Category") // Alphabetically first
                    .slug("aaa")
                    .displayOrder(5) // But higher display order
                    .isActive(true)
                    .eventCount(0)
                    .build();

            when(categoryRepository.findAll()).thenReturn(List.of(category2, testCategory));

            List<CategoryResponse> result = categoryService.getAllCategories();

            // testCategory (displayOrder=1) should come first
            assertThat(result.get(0).getName()).isEqualTo("Conference");
            assertThat(result.get(1).getName()).isEqualTo("AAA Category");
        }
    }

    // ==================== GET CATEGORY BY ID ====================

    @Nested
    @DisplayName("getCategoryById")
    class GetCategoryByIdTests {

        @Test
        @DisplayName("Success - returns category")
        void getCategoryById_Success() {
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));

            CategoryResponse result = categoryService.getCategoryById(CATEGORY_ID);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(CATEGORY_ID);
            assertThat(result.getName()).isEqualTo("Conference");
            assertThat(result.getSlug()).isEqualTo("conference");
            assertThat(result.getDescription()).isEqualTo("Professional conferences");
            assertThat(result.getIcon()).isEqualTo("calendar");
            assertThat(result.getColor()).isEqualTo("#3B82F6");
            assertThat(result.getDisplayOrder()).isEqualTo(1);
            assertThat(result.getIsActive()).isTrue();
            assertThat(result.getEventCount()).isEqualTo(10);
        }

        @Test
        @DisplayName("Null ID - throws ValidationException")
        void getCategoryById_NullId() {
            assertThatThrownBy(() -> categoryService.getCategoryById(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Category ID is required");

            verifyNoInteractions(categoryRepository);
        }

        @Test
        @DisplayName("Not found - throws ValidationException")
        void getCategoryById_NotFound() {
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.getCategoryById(CATEGORY_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Event category not found with id: " + CATEGORY_ID);
        }
    }

    // ==================== GET CATEGORY BY SLUG ====================

    @Nested
    @DisplayName("getCategoryBySlug")
    class GetCategoryBySlugTests {

        @Test
        @DisplayName("Success - returns category")
        void getCategoryBySlug_Success() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                mockedValidator.when(() -> InputValidator.sanitizeSearchQuery("conference"))
                        .thenReturn("conference");

                when(categoryRepository.findBySlug("conference")).thenReturn(Optional.of(testCategory));

                CategoryResponse result = categoryService.getCategoryBySlug("conference");

                assertThat(result).isNotNull();
                assertThat(result.getSlug()).isEqualTo("conference");
            }
        }

        @Test
        @DisplayName("Null slug - throws ValidationException")
        void getCategoryBySlug_NullSlug() {
            assertThatThrownBy(() -> categoryService.getCategoryBySlug(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Slug is required");

            verifyNoInteractions(categoryRepository);
        }

        @Test
        @DisplayName("Blank slug - throws ValidationException")
        void getCategoryBySlug_BlankSlug() {
            assertThatThrownBy(() -> categoryService.getCategoryBySlug("   "))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Slug is required");

            verifyNoInteractions(categoryRepository);
        }

        @Test
        @DisplayName("Sanitized slug is blank - throws ValidationException")
        void getCategoryBySlug_SanitizedBlank() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                mockedValidator.when(() -> InputValidator.sanitizeSearchQuery("\u0001\u0002"))
                        .thenReturn("");

                assertThatThrownBy(() -> categoryService.getCategoryBySlug("\u0001\u0002"))
                        .isInstanceOf(ValidationException.class)
                        .hasMessage("Invalid slug format");
            }
        }

        @Test
        @DisplayName("Category not found - throws ValidationException")
        void getCategoryBySlug_NotFound() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                mockedValidator.when(() -> InputValidator.sanitizeSearchQuery("nonexistent"))
                        .thenReturn("nonexistent");

                when(categoryRepository.findBySlug("nonexistent")).thenReturn(Optional.empty());

                assertThatThrownBy(() -> categoryService.getCategoryBySlug("nonexistent"))
                        .isInstanceOf(ValidationException.class)
                        .hasMessage("Event category not found with slug: nonexistent");
            }
        }
    }

    // ==================== CREATE CATEGORY ====================

    @Nested
    @DisplayName("createCategory")
    class CreateCategoryTests {

        @Test
        @DisplayName("Success - creates category with all fields")
        void createCategory_Success() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                CreateCategoryRequest request = CreateCategoryRequest.builder()
                        .name("New Category")
                        .slug("new-category")
                        .description("A new category")
                        .icon("star")
                        .color("#FF5733")
                        .displayOrder(5)
                        .build();

                mockedValidator.when(() -> InputValidator.validateName("New Category", "Category name"))
                        .thenReturn("New Category");
                mockedValidator.when(() -> InputValidator.validateText("A new category", "Description", 500))
                        .thenReturn("A new category");
                mockedValidator.when(() -> InputValidator.validateText("star", "Icon", 50))
                        .thenReturn("star");

                when(categoryRepository.existsBySlug("new-category")).thenReturn(false);
                when(categoryRepository.save(any(EventCategory.class))).thenAnswer(invocation -> {
                    EventCategory saved = invocation.getArgument(0);
                    saved.setId(UUID.randomUUID());
                    return saved;
                });

                CategoryResponse result = categoryService.createCategory(request);

                assertThat(result).isNotNull();
                assertThat(result.getName()).isEqualTo("New Category");
                assertThat(result.getSlug()).isEqualTo("new-category");
                assertThat(result.getDescription()).isEqualTo("A new category");
                assertThat(result.getIcon()).isEqualTo("star");
                assertThat(result.getColor()).isEqualTo("#FF5733");
                assertThat(result.getDisplayOrder()).isEqualTo(5);
                assertThat(result.getIsActive()).isTrue();
                assertThat(result.getEventCount()).isEqualTo(0);

                verify(categoryRepository).save(any(EventCategory.class));
            }
        }

        @Test
        @DisplayName("Success - generates slug from name when slug not provided")
        void createCategory_GeneratesSlugFromName() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                CreateCategoryRequest request = CreateCategoryRequest.builder()
                        .name("My New Category")
                        .build();

                mockedValidator.when(() -> InputValidator.validateName("My New Category", "Category name"))
                        .thenReturn("My New Category");

                when(categoryRepository.existsBySlug("my-new-category")).thenReturn(false);
                when(categoryRepository.findAll()).thenReturn(Collections.emptyList()); // For getNextDisplayOrder
                when(categoryRepository.save(any(EventCategory.class))).thenAnswer(invocation -> {
                    EventCategory saved = invocation.getArgument(0);
                    saved.setId(UUID.randomUUID());
                    return saved;
                });

                CategoryResponse result = categoryService.createCategory(request);

                assertThat(result.getSlug()).isEqualTo("my-new-category");
            }
        }

        @Test
        @DisplayName("Success - calculates next display order")
        void createCategory_CalculatesNextDisplayOrder() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                CreateCategoryRequest request = CreateCategoryRequest.builder()
                        .name("New Category")
                        .build();

                mockedValidator.when(() -> InputValidator.validateName("New Category", "Category name"))
                        .thenReturn("New Category");

                EventCategory existing1 = EventCategory.builder().displayOrder(5).build();
                EventCategory existing2 = EventCategory.builder().displayOrder(10).build();

                when(categoryRepository.existsBySlug(anyString())).thenReturn(false);
                when(categoryRepository.findAll()).thenReturn(List.of(existing1, existing2));
                when(categoryRepository.save(any(EventCategory.class))).thenAnswer(invocation -> {
                    EventCategory saved = invocation.getArgument(0);
                    assertThat(saved.getDisplayOrder()).isEqualTo(11); // max(5,10) + 1
                    saved.setId(UUID.randomUUID());
                    return saved;
                });

                categoryService.createCategory(request);

                verify(categoryRepository).save(any(EventCategory.class));
            }
        }

        @Test
        @DisplayName("Success - next display order when no existing categories")
        void createCategory_NextDisplayOrderEmpty() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                CreateCategoryRequest request = CreateCategoryRequest.builder()
                        .name("First Category")
                        .build();

                mockedValidator.when(() -> InputValidator.validateName("First Category", "Category name"))
                        .thenReturn("First Category");

                when(categoryRepository.existsBySlug(anyString())).thenReturn(false);
                when(categoryRepository.findAll()).thenReturn(Collections.emptyList());
                when(categoryRepository.save(any(EventCategory.class))).thenAnswer(invocation -> {
                    EventCategory saved = invocation.getArgument(0);
                    assertThat(saved.getDisplayOrder()).isEqualTo(1); // 0 + 1
                    saved.setId(UUID.randomUUID());
                    return saved;
                });

                categoryService.createCategory(request);
            }
        }

        @Test
        @DisplayName("Null request - throws ValidationException")
        void createCategory_NullRequest() {
            assertThatThrownBy(() -> categoryService.createCategory(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Request body is required");

            verifyNoInteractions(categoryRepository);
        }

        @Test
        @DisplayName("Duplicate slug - throws ValidationException")
        void createCategory_DuplicateSlug() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                CreateCategoryRequest request = CreateCategoryRequest.builder()
                        .name("Conference")
                        .slug("conference")
                        .build();

                mockedValidator.when(() -> InputValidator.validateName("Conference", "Category name"))
                        .thenReturn("Conference");

                when(categoryRepository.existsBySlug("conference")).thenReturn(true);

                assertThatThrownBy(() -> categoryService.createCategory(request))
                        .isInstanceOf(ValidationException.class)
                        .hasMessage("Category with slug 'conference' already exists");

                verify(categoryRepository, never()).save(any());
            }
        }

        @Test
        @DisplayName("Invalid color format - throws ValidationException")
        void createCategory_InvalidColor() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                CreateCategoryRequest request = CreateCategoryRequest.builder()
                        .name("Category")
                        .color("invalid-color")
                        .build();

                mockedValidator.when(() -> InputValidator.validateName("Category", "Category name"))
                        .thenReturn("Category");

                when(categoryRepository.existsBySlug(anyString())).thenReturn(false);

                assertThatThrownBy(() -> categoryService.createCategory(request))
                        .isInstanceOf(ValidationException.class)
                        .hasMessage("Invalid color format. Use hex format like #FF5733");
            }
        }

        @Test
        @DisplayName("Valid 3-digit hex color")
        void createCategory_ThreeDigitHexColor() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                CreateCategoryRequest request = CreateCategoryRequest.builder()
                        .name("Category")
                        .color("#F00")
                        .build();

                mockedValidator.when(() -> InputValidator.validateName("Category", "Category name"))
                        .thenReturn("Category");

                when(categoryRepository.existsBySlug(anyString())).thenReturn(false);
                when(categoryRepository.findAll()).thenReturn(Collections.emptyList());
                when(categoryRepository.save(any(EventCategory.class))).thenAnswer(invocation -> {
                    EventCategory saved = invocation.getArgument(0);
                    assertThat(saved.getColor()).isEqualTo("#F00");
                    saved.setId(UUID.randomUUID());
                    return saved;
                });

                categoryService.createCategory(request);
            }
        }

        @Test
        @DisplayName("Blank description - not set")
        void createCategory_BlankDescription() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                CreateCategoryRequest request = CreateCategoryRequest.builder()
                        .name("Category")
                        .description("   ")
                        .build();

                mockedValidator.when(() -> InputValidator.validateName("Category", "Category name"))
                        .thenReturn("Category");

                when(categoryRepository.existsBySlug(anyString())).thenReturn(false);
                when(categoryRepository.findAll()).thenReturn(Collections.emptyList());
                when(categoryRepository.save(any(EventCategory.class))).thenAnswer(invocation -> {
                    EventCategory saved = invocation.getArgument(0);
                    assertThat(saved.getDescription()).isNull();
                    saved.setId(UUID.randomUUID());
                    return saved;
                });

                categoryService.createCategory(request);
            }
        }

        @Test
        @DisplayName("Blank icon - not set")
        void createCategory_BlankIcon() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                CreateCategoryRequest request = CreateCategoryRequest.builder()
                        .name("Category")
                        .icon("   ")
                        .build();

                mockedValidator.when(() -> InputValidator.validateName("Category", "Category name"))
                        .thenReturn("Category");

                when(categoryRepository.existsBySlug(anyString())).thenReturn(false);
                when(categoryRepository.findAll()).thenReturn(Collections.emptyList());
                when(categoryRepository.save(any(EventCategory.class))).thenAnswer(invocation -> {
                    EventCategory saved = invocation.getArgument(0);
                    assertThat(saved.getIcon()).isNull();
                    saved.setId(UUID.randomUUID());
                    return saved;
                });

                categoryService.createCategory(request);
            }
        }

        @Test
        @DisplayName("Blank color - not set")
        void createCategory_BlankColor() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                CreateCategoryRequest request = CreateCategoryRequest.builder()
                        .name("Category")
                        .color("   ")
                        .build();

                mockedValidator.when(() -> InputValidator.validateName("Category", "Category name"))
                        .thenReturn("Category");

                when(categoryRepository.existsBySlug(anyString())).thenReturn(false);
                when(categoryRepository.findAll()).thenReturn(Collections.emptyList());
                when(categoryRepository.save(any(EventCategory.class))).thenAnswer(invocation -> {
                    EventCategory saved = invocation.getArgument(0);
                    assertThat(saved.getColor()).isNull();
                    saved.setId(UUID.randomUUID());
                    return saved;
                });

                categoryService.createCategory(request);
            }
        }

        @Test
        @DisplayName("Blank slug - generates from name")
        void createCategory_BlankSlug() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                CreateCategoryRequest request = CreateCategoryRequest.builder()
                        .name("My Category")
                        .slug("   ")
                        .build();

                mockedValidator.when(() -> InputValidator.validateName("My Category", "Category name"))
                        .thenReturn("My Category");

                when(categoryRepository.existsBySlug("my-category")).thenReturn(false);
                when(categoryRepository.findAll()).thenReturn(Collections.emptyList());
                when(categoryRepository.save(any(EventCategory.class))).thenAnswer(invocation -> {
                    EventCategory saved = invocation.getArgument(0);
                    assertThat(saved.getSlug()).isEqualTo("my-category");
                    saved.setId(UUID.randomUUID());
                    return saved;
                });

                categoryService.createCategory(request);
            }
        }
    }

    // ==================== UPDATE CATEGORY ====================

    @Nested
    @DisplayName("updateCategory")
    class UpdateCategoryTests {

        @Test
        @DisplayName("Success - updates all fields")
        void updateCategory_AllFields() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                UpdateCategoryRequest request = UpdateCategoryRequest.builder()
                        .name("Updated Name")
                        .slug("updated-slug")
                        .description("Updated description")
                        .icon("new-icon")
                        .color("#AABBCC")
                        .displayOrder(99)
                        .isActive(false)
                        .build();

                mockedValidator.when(() -> InputValidator.validateName("Updated Name", "Category name"))
                        .thenReturn("Updated Name");
                mockedValidator.when(() -> InputValidator.validateText("Updated description", "Description", 500))
                        .thenReturn("Updated description");
                mockedValidator.when(() -> InputValidator.validateText("new-icon", "Icon", 50))
                        .thenReturn("new-icon");

                when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
                when(categoryRepository.existsBySlug("updated-slug")).thenReturn(false);
                when(categoryRepository.save(any(EventCategory.class))).thenReturn(testCategory);

                CategoryResponse result = categoryService.updateCategory(CATEGORY_ID, request);

                assertThat(result).isNotNull();
                verify(categoryRepository).save(argThat(cat ->
                        cat.getName().equals("Updated Name") &&
                                cat.getSlug().equals("updated-slug") &&
                                cat.getDescription().equals("Updated description") &&
                                cat.getIcon().equals("new-icon") &&
                                cat.getColor().equals("#AABBCC") &&
                                cat.getDisplayOrder() == 99 &&
                                !cat.getIsActive()
                ));
            }
        }

        @Test
        @DisplayName("Null request - throws ValidationException")
        void updateCategory_NullRequest() {
            assertThatThrownBy(() -> categoryService.updateCategory(CATEGORY_ID, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Request body is required");

            verifyNoInteractions(categoryRepository);
        }

        @Test
        @DisplayName("Category not found - throws ValidationException")
        void updateCategory_NotFound() {
            UpdateCategoryRequest request = UpdateCategoryRequest.builder()
                    .name("Updated")
                    .build();

            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.updateCategory(CATEGORY_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Event category not found with id: " + CATEGORY_ID);
        }

        @Test
        @DisplayName("Duplicate slug - throws ValidationException")
        void updateCategory_DuplicateSlug() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                UpdateCategoryRequest request = UpdateCategoryRequest.builder()
                        .slug("existing-slug")
                        .build();

                when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
                when(categoryRepository.existsBySlug("existing-slug")).thenReturn(true);

                assertThatThrownBy(() -> categoryService.updateCategory(CATEGORY_ID, request))
                        .isInstanceOf(ValidationException.class)
                        .hasMessage("Category with slug 'existing-slug' already exists");
            }
        }

        @Test
        @DisplayName("Same slug - no duplicate check needed")
        void updateCategory_SameSlug() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                UpdateCategoryRequest request = UpdateCategoryRequest.builder()
                        .slug("conference") // Same as existing
                        .build();

                when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
                when(categoryRepository.save(any(EventCategory.class))).thenReturn(testCategory);

                CategoryResponse result = categoryService.updateCategory(CATEGORY_ID, request);

                assertThat(result).isNotNull();
                verify(categoryRepository, never()).existsBySlug(anyString());
            }
        }

        @Test
        @DisplayName("Blank name - not updated")
        void updateCategory_BlankName() {
            UpdateCategoryRequest request = UpdateCategoryRequest.builder()
                    .name("   ")
                    .build();

            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.save(any(EventCategory.class))).thenReturn(testCategory);

            categoryService.updateCategory(CATEGORY_ID, request);

            verify(categoryRepository).save(argThat(cat ->
                    cat.getName().equals("Conference") // Unchanged
            ));
        }

        @Test
        @DisplayName("Null name - not updated")
        void updateCategory_NullName() {
            UpdateCategoryRequest request = UpdateCategoryRequest.builder()
                    .displayOrder(5) // Only update this
                    .build();

            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.save(any(EventCategory.class))).thenReturn(testCategory);

            categoryService.updateCategory(CATEGORY_ID, request);

            verify(categoryRepository).save(argThat(cat ->
                    cat.getName().equals("Conference") // Unchanged
            ));
        }

        @Test
        @DisplayName("Blank description - sets to null")
        void updateCategory_BlankDescription() {
            UpdateCategoryRequest request = UpdateCategoryRequest.builder()
                    .description("   ")
                    .build();

            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.save(any(EventCategory.class))).thenReturn(testCategory);

            categoryService.updateCategory(CATEGORY_ID, request);

            verify(categoryRepository).save(argThat(cat ->
                    cat.getDescription() == null
            ));
        }

        @Test
        @DisplayName("Blank icon - sets to null")
        void updateCategory_BlankIcon() {
            UpdateCategoryRequest request = UpdateCategoryRequest.builder()
                    .icon("   ")
                    .build();

            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.save(any(EventCategory.class))).thenReturn(testCategory);

            categoryService.updateCategory(CATEGORY_ID, request);

            verify(categoryRepository).save(argThat(cat ->
                    cat.getIcon() == null
            ));
        }

        @Test
        @DisplayName("Blank color - sets to null")
        void updateCategory_BlankColor() {
            UpdateCategoryRequest request = UpdateCategoryRequest.builder()
                    .color("   ")
                    .build();

            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.save(any(EventCategory.class))).thenReturn(testCategory);

            categoryService.updateCategory(CATEGORY_ID, request);

            verify(categoryRepository).save(argThat(cat ->
                    cat.getColor() == null
            ));
        }

        @Test
        @DisplayName("Invalid color - throws ValidationException")
        void updateCategory_InvalidColor() {
            UpdateCategoryRequest request = UpdateCategoryRequest.builder()
                    .color("not-a-color")
                    .build();

            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));

            assertThatThrownBy(() -> categoryService.updateCategory(CATEGORY_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Invalid color format. Use hex format like #FF5733");
        }

        @Test
        @DisplayName("Blank slug - not updated")
        void updateCategory_BlankSlug() {
            UpdateCategoryRequest request = UpdateCategoryRequest.builder()
                    .slug("   ")
                    .build();

            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.save(any(EventCategory.class))).thenReturn(testCategory);

            categoryService.updateCategory(CATEGORY_ID, request);

            verify(categoryRepository).save(argThat(cat ->
                    cat.getSlug().equals("conference") // Unchanged
            ));
        }
    }

    // ==================== DELETE CATEGORY ====================

    @Nested
    @DisplayName("deleteCategory")
    class DeleteCategoryTests {

        @Test
        @DisplayName("Success - deletes category")
        void deleteCategory_Success() {
            testCategory.setEventCount(0);

            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
            when(eventRepository.countByCategoryId(CATEGORY_ID)).thenReturn(0L);
            doNothing().when(categoryRepository).delete(testCategory);

            categoryService.deleteCategory(CATEGORY_ID);

            verify(categoryRepository).delete(testCategory);
        }

        @Test
        @DisplayName("Null ID - throws ValidationException")
        void deleteCategory_NullId() {
            assertThatThrownBy(() -> categoryService.deleteCategory(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Category ID is required");

            verifyNoInteractions(categoryRepository);
        }

        @Test
        @DisplayName("Category not found - throws ValidationException")
        void deleteCategory_NotFound() {
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.deleteCategory(CATEGORY_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Event category not found with id: " + CATEGORY_ID);
        }

        @Test
        @DisplayName("Has events (eventCount > 0) - throws ValidationException")
        void deleteCategory_HasEventsByCount() {
            testCategory.setEventCount(5);

            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));

            assertThatThrownBy(() -> categoryService.deleteCategory(CATEGORY_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Cannot delete category with existing events. Deactivate it instead.");

            verify(categoryRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Has events (from database) - throws ValidationException")
        void deleteCategory_HasEventsInDb() {
            testCategory.setEventCount(0);

            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
            when(eventRepository.countByCategoryId(CATEGORY_ID)).thenReturn(3L);

            assertThatThrownBy(() -> categoryService.deleteCategory(CATEGORY_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Cannot delete category with 3 existing events. Deactivate it instead.");

            verify(categoryRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Null eventCount - checks database")
        void deleteCategory_NullEventCount() {
            testCategory.setEventCount(null);

            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
            when(eventRepository.countByCategoryId(CATEGORY_ID)).thenReturn(0L);
            doNothing().when(categoryRepository).delete(testCategory);

            categoryService.deleteCategory(CATEGORY_ID);

            verify(eventRepository).countByCategoryId(CATEGORY_ID);
            verify(categoryRepository).delete(testCategory);
        }
    }

    // ==================== TOGGLE ACTIVE ====================

    @Nested
    @DisplayName("toggleActive")
    class ToggleActiveTests {

        @Test
        @DisplayName("Success - activates inactive category")
        void toggleActive_Activate() {
            testCategory.setIsActive(false);

            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.save(any(EventCategory.class))).thenReturn(testCategory);

            CategoryResponse result = categoryService.toggleActive(CATEGORY_ID);

            verify(categoryRepository).save(argThat(cat -> cat.getIsActive()));
        }

        @Test
        @DisplayName("Success - deactivates active category")
        void toggleActive_Deactivate() {
            testCategory.setIsActive(true);

            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.save(any(EventCategory.class))).thenReturn(testCategory);

            categoryService.toggleActive(CATEGORY_ID);

            verify(categoryRepository).save(argThat(cat -> !cat.getIsActive()));
        }

        @Test
        @DisplayName("Null isActive treated as false - toggles to true")
        void toggleActive_NullIsActive() {
            testCategory.setIsActive(null);

            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.save(any(EventCategory.class))).thenReturn(testCategory);

            categoryService.toggleActive(CATEGORY_ID);

            // !Boolean.TRUE.equals(null) = true
            verify(categoryRepository).save(argThat(cat -> cat.getIsActive()));
        }

        @Test
        @DisplayName("Null ID - throws ValidationException")
        void toggleActive_NullId() {
            assertThatThrownBy(() -> categoryService.toggleActive(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Category ID is required");

            verifyNoInteractions(categoryRepository);
        }

        @Test
        @DisplayName("Category not found - throws ValidationException")
        void toggleActive_NotFound() {
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.toggleActive(CATEGORY_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Event category not found with id: " + CATEGORY_ID);
        }
    }

    // ==================== UPDATE DISPLAY ORDER ====================

    @Nested
    @DisplayName("updateDisplayOrder")
    class UpdateDisplayOrderTests {

        @Test
        @DisplayName("Success - updates display order")
        void updateDisplayOrder_Success() {
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.save(any(EventCategory.class))).thenReturn(testCategory);

            CategoryResponse result = categoryService.updateDisplayOrder(CATEGORY_ID, 50);

            verify(categoryRepository).save(argThat(cat -> cat.getDisplayOrder() == 50));
        }

        @Test
        @DisplayName("Success - display order at minimum (0)")
        void updateDisplayOrder_AtMinimum() {
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.save(any(EventCategory.class))).thenReturn(testCategory);

            categoryService.updateDisplayOrder(CATEGORY_ID, 0);

            verify(categoryRepository).save(argThat(cat -> cat.getDisplayOrder() == 0));
        }

        @Test
        @DisplayName("Success - display order at maximum (1000)")
        void updateDisplayOrder_AtMaximum() {
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.save(any(EventCategory.class))).thenReturn(testCategory);

            categoryService.updateDisplayOrder(CATEGORY_ID, 1000);

            verify(categoryRepository).save(argThat(cat -> cat.getDisplayOrder() == 1000));
        }

        @Test
        @DisplayName("Null ID - throws ValidationException")
        void updateDisplayOrder_NullId() {
            assertThatThrownBy(() -> categoryService.updateDisplayOrder(null, 5))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Category ID is required");

            verifyNoInteractions(categoryRepository);
        }

        @Test
        @DisplayName("Null display order - throws ValidationException")
        void updateDisplayOrder_NullDisplayOrder() {
            assertThatThrownBy(() -> categoryService.updateDisplayOrder(CATEGORY_ID, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Display order is required");

            verifyNoInteractions(categoryRepository);
        }

        @Test
        @DisplayName("Display order below minimum - throws ValidationException")
        void updateDisplayOrder_BelowMinimum() {
            assertThatThrownBy(() -> categoryService.updateDisplayOrder(CATEGORY_ID, -1))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Display order must be between 0 and 1000");

            verifyNoInteractions(categoryRepository);
        }

        @Test
        @DisplayName("Display order above maximum - throws ValidationException")
        void updateDisplayOrder_AboveMaximum() {
            assertThatThrownBy(() -> categoryService.updateDisplayOrder(CATEGORY_ID, 1001))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Display order must be between 0 and 1000");

            verifyNoInteractions(categoryRepository);
        }

        @Test
        @DisplayName("Category not found - throws ValidationException")
        void updateDisplayOrder_NotFound() {
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.updateDisplayOrder(CATEGORY_ID, 5))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Event category not found with id: " + CATEGORY_ID);
        }
    }

    // ==================== HELPER METHOD TESTS ====================

    @Nested
    @DisplayName("Helper Methods (via public APIs)")
    class HelperMethodTests {

        @Test
        @DisplayName("generateSlug - handles special characters")
        void generateSlug_SpecialCharacters() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                CreateCategoryRequest request = CreateCategoryRequest.builder()
                        .name("Test & Demo's Category!")
                        .build();

                mockedValidator.when(() -> InputValidator.validateName("Test & Demo's Category!", "Category name"))
                        .thenReturn("Test & Demo's Category!");

                when(categoryRepository.existsBySlug(anyString())).thenReturn(false);
                when(categoryRepository.findAll()).thenReturn(Collections.emptyList());
                when(categoryRepository.save(any(EventCategory.class))).thenAnswer(invocation -> {
                    EventCategory saved = invocation.getArgument(0);
                    // Slug should have special chars removed
                    assertThat(saved.getSlug()).doesNotContain("&", "'", "!");
                    saved.setId(UUID.randomUUID());
                    return saved;
                });

                categoryService.createCategory(request);
            }
        }

        @Test
        @DisplayName("validateColor - returns null for null input")
        void validateColor_NullInput() throws Exception {
            java.lang.reflect.Method method = Eventcategoryservice.class
                    .getDeclaredMethod("validateColor", String.class);
            method.setAccessible(true);

            Object result = method.invoke(categoryService, (String) null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("validateColor - returns null for blank input")
        void validateColor_BlankInput() throws Exception {
            java.lang.reflect.Method method = Eventcategoryservice.class
                    .getDeclaredMethod("validateColor", String.class);
            method.setAccessible(true);

            Object result = method.invoke(categoryService, "   ");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("generateSlug - handles multiple spaces/dashes")
        void generateSlug_MultipleSpaces() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                CreateCategoryRequest request = CreateCategoryRequest.builder()
                        .name("Test   Multiple   Spaces")
                        .build();

                mockedValidator.when(() -> InputValidator.validateName("Test   Multiple   Spaces", "Category name"))
                        .thenReturn("Test   Multiple   Spaces");

                when(categoryRepository.existsBySlug(anyString())).thenReturn(false);
                when(categoryRepository.findAll()).thenReturn(Collections.emptyList());
                when(categoryRepository.save(any(EventCategory.class))).thenAnswer(invocation -> {
                    EventCategory saved = invocation.getArgument(0);
                    // Multiple dashes should be collapsed
                    assertThat(saved.getSlug()).doesNotContain("--");
                    saved.setId(UUID.randomUUID());
                    return saved;
                });

                categoryService.createCategory(request);
            }
        }

        @Test
        @DisplayName("validateColor - lowercase hex converted to uppercase")
        void validateColor_LowercaseToUppercase() {
            try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
                CreateCategoryRequest request = CreateCategoryRequest.builder()
                        .name("Category")
                        .color("#aabbcc")
                        .build();

                mockedValidator.when(() -> InputValidator.validateName("Category", "Category name"))
                        .thenReturn("Category");

                when(categoryRepository.existsBySlug(anyString())).thenReturn(false);
                when(categoryRepository.findAll()).thenReturn(Collections.emptyList());
                when(categoryRepository.save(any(EventCategory.class))).thenAnswer(invocation -> {
                    EventCategory saved = invocation.getArgument(0);
                    assertThat(saved.getColor()).isEqualTo("#AABBCC");
                    saved.setId(UUID.randomUUID());
                    return saved;
                });

                categoryService.createCategory(request);
            }
        }

        @Test
        @DisplayName("mapToResponse - maps all fields correctly")
        void mapToResponse_AllFields() {
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));

            CategoryResponse result = categoryService.getCategoryById(CATEGORY_ID);

            assertThat(result.getId()).isEqualTo(testCategory.getId());
            assertThat(result.getName()).isEqualTo(testCategory.getName());
            assertThat(result.getSlug()).isEqualTo(testCategory.getSlug());
            assertThat(result.getDescription()).isEqualTo(testCategory.getDescription());
            assertThat(result.getIcon()).isEqualTo(testCategory.getIcon());
            assertThat(result.getColor()).isEqualTo(testCategory.getColor());
            assertThat(result.getDisplayOrder()).isEqualTo(testCategory.getDisplayOrder());
            assertThat(result.getEventCount()).isEqualTo(testCategory.getEventCount());
            assertThat(result.getIsActive()).isEqualTo(testCategory.getIsActive());
            assertThat(result.getCreatedAt()).isEqualTo(testCategory.getCreatedAt());
            assertThat(result.getUpdatedAt()).isEqualTo(testCategory.getUpdatedAt());
        }
    }
}