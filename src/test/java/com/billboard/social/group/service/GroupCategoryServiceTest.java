package com.billboard.social.group.service;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.group.dto.request.GroupCategoryRequests.CreateGroupCategoryRequest;
import com.billboard.social.group.dto.request.GroupCategoryRequests.UpdateGroupCategoryRequest;
import com.billboard.social.group.dto.response.GroupCategoryResponses.GroupCategoryResponse;
import com.billboard.social.group.entity.GroupCategory;
import com.billboard.social.group.repository.GroupCategoryRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupCategoryServiceTest {

    @Mock
    private GroupCategoryRepository categoryRepository;

    @InjectMocks
    private GroupCategoryService categoryService;

    // Test constants
    private static final UUID CATEGORY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PARENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OTHER_CATEGORY_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private GroupCategory testCategory;
    private GroupCategory parentCategory;

    @BeforeEach
    void setUp() {
        testCategory = GroupCategory.builder()
                .id(CATEGORY_ID)
                .name("Technology")
                .slug("technology")
                .description("Technology related groups")
                .icon("💻")
                .parentId(null)
                .displayOrder(1)
                .groupCount(0)
                .isActive(true)
                .build();
        testCategory.setCreatedAt(LocalDateTime.now());

        parentCategory = GroupCategory.builder()
                .id(PARENT_ID)
                .name("Parent Category")
                .slug("parent-category")
                .displayOrder(1)
                .groupCount(0)
                .isActive(true)
                .build();
        parentCategory.setCreatedAt(LocalDateTime.now());
    }

    // ==================== CREATE CATEGORY ====================

    @Nested
    @DisplayName("createCategory")
    class CreateCategoryTests {

        @Test
        @DisplayName("Success - with all fields")
        void createCategory_SuccessAllFields() {
            CreateGroupCategoryRequest request = CreateGroupCategoryRequest.builder()
                    .name("Technology")
                    .description("Technology related groups")
                    .icon("💻")
                    .displayOrder(5)
                    .isActive(true)
                    .build();

            when(categoryRepository.existsByName("Technology")).thenReturn(false);
            when(categoryRepository.existsBySlug("technology")).thenReturn(false);
            when(categoryRepository.save(any(GroupCategory.class))).thenAnswer(invocation -> {
                GroupCategory saved = invocation.getArgument(0);
                saved.setId(CATEGORY_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });

            GroupCategoryResponse response = categoryService.createCategory(request);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(CATEGORY_ID);
            assertThat(response.getName()).isEqualTo("Technology");
            assertThat(response.getSlug()).isEqualTo("technology");
            assertThat(response.getDescription()).isEqualTo("Technology related groups");
            assertThat(response.getIcon()).isEqualTo("💻");
            assertThat(response.getDisplayOrder()).isEqualTo(5);
            assertThat(response.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("generateSlug - empty after processing generates fallback slug")
        void generateSlug_EmptyAfterProcessingGeneratesFallbackSlug() {
            when(categoryRepository.existsBySlug(argThat(s -> s != null && s.startsWith("category-"))))
                    .thenReturn(false);

            String slug = ReflectionTestUtils.invokeMethod(categoryService, "generateSlug", "@#$%^&*()");

            assertThat(slug).startsWith("category-");
            assertThat(slug).matches("category-\\d+");
        }

        @Test
        @DisplayName("Success - displayOrder null, maxOrder not null (uses maxOrder + 1)")
        void createCategory_DisplayOrderNullMaxOrderNotNull() {
            CreateGroupCategoryRequest request = CreateGroupCategoryRequest.builder()
                    .name("Technology")
                    .build();

            when(categoryRepository.existsByName("Technology")).thenReturn(false);
            when(categoryRepository.existsBySlug("technology")).thenReturn(false);
            when(categoryRepository.findMaxDisplayOrder()).thenReturn(10);
            when(categoryRepository.save(any(GroupCategory.class))).thenAnswer(invocation -> {
                GroupCategory saved = invocation.getArgument(0);
                saved.setId(CATEGORY_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });

            GroupCategoryResponse response = categoryService.createCategory(request);

            assertThat(response.getDisplayOrder()).isEqualTo(11);
        }

        @Test
        @DisplayName("Success - displayOrder null, maxOrder null (uses 1)")
        void createCategory_DisplayOrderNullMaxOrderNull() {
            CreateGroupCategoryRequest request = CreateGroupCategoryRequest.builder()
                    .name("Technology")
                    .build();

            when(categoryRepository.existsByName("Technology")).thenReturn(false);
            when(categoryRepository.existsBySlug("technology")).thenReturn(false);
            when(categoryRepository.findMaxDisplayOrder()).thenReturn(null);
            when(categoryRepository.save(any(GroupCategory.class))).thenAnswer(invocation -> {
                GroupCategory saved = invocation.getArgument(0);
                saved.setId(CATEGORY_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });

            GroupCategoryResponse response = categoryService.createCategory(request);

            assertThat(response.getDisplayOrder()).isEqualTo(1);
        }

        @Test
        @DisplayName("Success - with explicit displayOrder (uses provided value)")
        void createCategory_ExplicitDisplayOrder() {
            CreateGroupCategoryRequest request = CreateGroupCategoryRequest.builder()
                    .name("Technology")
                    .displayOrder(99)
                    .build();

            when(categoryRepository.existsByName("Technology")).thenReturn(false);
            when(categoryRepository.existsBySlug("technology")).thenReturn(false);
            when(categoryRepository.findMaxDisplayOrder()).thenReturn(10); // Still called but result ignored
            when(categoryRepository.save(any(GroupCategory.class))).thenAnswer(invocation -> {
                GroupCategory saved = invocation.getArgument(0);
                saved.setId(CATEGORY_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });

            GroupCategoryResponse response = categoryService.createCategory(request);

            // Uses the explicit value (99), not maxOrder + 1 (11)
            assertThat(response.getDisplayOrder()).isEqualTo(99);
        }

        @Test
        @DisplayName("Success - with parent category")
        void createCategory_WithParent() {
            CreateGroupCategoryRequest request = CreateGroupCategoryRequest.builder()
                    .name("Programming")
                    .parentId(PARENT_ID)
                    .build();

            when(categoryRepository.existsByName("Programming")).thenReturn(false);
            when(categoryRepository.existsBySlug("programming")).thenReturn(false);
            when(categoryRepository.findMaxDisplayOrder()).thenReturn(null);
            when(categoryRepository.findById(PARENT_ID)).thenReturn(Optional.of(parentCategory));
            when(categoryRepository.save(any(GroupCategory.class))).thenAnswer(invocation -> {
                GroupCategory saved = invocation.getArgument(0);
                saved.setId(CATEGORY_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });

            GroupCategoryResponse response = categoryService.createCategory(request);

            assertThat(response.getParentId()).isEqualTo(PARENT_ID);
            verify(categoryRepository).findById(PARENT_ID);
        }

        @Test
        @DisplayName("Success - without parent (parentId null, skip parent check)")
        void createCategory_WithoutParent() {
            CreateGroupCategoryRequest request = CreateGroupCategoryRequest.builder()
                    .name("Technology")
                    .parentId(null)
                    .build();

            when(categoryRepository.existsByName("Technology")).thenReturn(false);
            when(categoryRepository.existsBySlug("technology")).thenReturn(false);
            when(categoryRepository.findMaxDisplayOrder()).thenReturn(null);
            when(categoryRepository.save(any(GroupCategory.class))).thenAnswer(invocation -> {
                GroupCategory saved = invocation.getArgument(0);
                saved.setId(CATEGORY_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });

            GroupCategoryResponse response = categoryService.createCategory(request);

            assertThat(response.getParentId()).isNull();
            // findById for parent should NOT be called when parentId is null
            verify(categoryRepository, never()).findById(any(UUID.class));
        }

        @Test
        @DisplayName("Success - isActive null (defaults to true)")
        void createCategory_IsActiveNull() {
            CreateGroupCategoryRequest request = CreateGroupCategoryRequest.builder()
                    .name("Technology")
                    .isActive(null)
                    .build();

            when(categoryRepository.existsByName("Technology")).thenReturn(false);
            when(categoryRepository.existsBySlug("technology")).thenReturn(false);
            when(categoryRepository.findMaxDisplayOrder()).thenReturn(null);
            when(categoryRepository.save(any(GroupCategory.class))).thenAnswer(invocation -> {
                GroupCategory saved = invocation.getArgument(0);
                saved.setId(CATEGORY_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });

            GroupCategoryResponse response = categoryService.createCategory(request);

            assertThat(response.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("Success - isActive explicit false")
        void createCategory_IsActiveFalse() {
            CreateGroupCategoryRequest request = CreateGroupCategoryRequest.builder()
                    .name("Technology")
                    .isActive(false)
                    .build();

            when(categoryRepository.existsByName("Technology")).thenReturn(false);
            when(categoryRepository.existsBySlug("technology")).thenReturn(false);
            when(categoryRepository.findMaxDisplayOrder()).thenReturn(null);
            when(categoryRepository.save(any(GroupCategory.class))).thenAnswer(invocation -> {
                GroupCategory saved = invocation.getArgument(0);
                saved.setId(CATEGORY_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });

            GroupCategoryResponse response = categoryService.createCategory(request);

            assertThat(response.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("Success - slug collision handled (generates unique slug)")
        void createCategory_SlugCollision() {
            CreateGroupCategoryRequest request = CreateGroupCategoryRequest.builder()
                    .name("Technology")
                    .build();

            when(categoryRepository.existsByName("Technology")).thenReturn(false);
            when(categoryRepository.existsBySlug("technology")).thenReturn(true);
            when(categoryRepository.existsBySlug("technology-1")).thenReturn(true);
            when(categoryRepository.existsBySlug("technology-2")).thenReturn(false);
            when(categoryRepository.findMaxDisplayOrder()).thenReturn(null);
            when(categoryRepository.save(any(GroupCategory.class))).thenAnswer(invocation -> {
                GroupCategory saved = invocation.getArgument(0);
                saved.setId(CATEGORY_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });

            GroupCategoryResponse response = categoryService.createCategory(request);

            assertThat(response.getSlug()).isEqualTo("technology-2");
        }

        @Test
        @DisplayName("Category name already exists - throws ValidationException")
        void createCategory_NameAlreadyExists() {
            CreateGroupCategoryRequest request = CreateGroupCategoryRequest.builder()
                    .name("Technology")
                    .build();

            when(categoryRepository.existsByName("Technology")).thenReturn(true);

            assertThatThrownBy(() -> categoryService.createCategory(request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Category with this name already exists");

            verify(categoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("Parent category not found - throws ValidationException")
        void createCategory_ParentNotFound() {
            CreateGroupCategoryRequest request = CreateGroupCategoryRequest.builder()
                    .name("Programming")
                    .parentId(PARENT_ID)
                    .build();

            when(categoryRepository.existsByName("Programming")).thenReturn(false);
            when(categoryRepository.existsBySlug("programming")).thenReturn(false);
            when(categoryRepository.findMaxDisplayOrder()).thenReturn(null);
            when(categoryRepository.findById(PARENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.createCategory(request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Parent category not found");

            verify(categoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("Race condition (DataIntegrityViolationException) - throws ValidationException")
        void createCategory_RaceCondition() {
            CreateGroupCategoryRequest request = CreateGroupCategoryRequest.builder()
                    .name("Technology")
                    .build();

            when(categoryRepository.existsByName("Technology")).thenReturn(false);
            when(categoryRepository.existsBySlug("technology")).thenReturn(false);
            when(categoryRepository.findMaxDisplayOrder()).thenReturn(null);
            when(categoryRepository.save(any(GroupCategory.class)))
                    .thenThrow(new DataIntegrityViolationException("Duplicate entry"));

            assertThatThrownBy(() -> categoryService.createCategory(request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Category with this name already exists");
        }
    }

    // ==================== UPDATE CATEGORY ====================

    @Nested
    @DisplayName("updateCategory")
    class UpdateCategoryTests {

        @Test
        @DisplayName("Success - update all fields")
        void updateCategory_SuccessAllFields() {
            UpdateGroupCategoryRequest request = UpdateGroupCategoryRequest.builder()
                    .name("Tech & Innovation")
                    .description("Updated description")
                    .icon("🚀")
                    .displayOrder(10)
                    .isActive(false)
                    .build();

            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.existsByName("Tech & Innovation")).thenReturn(false);
            when(categoryRepository.existsBySlug("tech-innovation")).thenReturn(false);
            when(categoryRepository.save(any(GroupCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

            GroupCategoryResponse response = categoryService.updateCategory(CATEGORY_ID, request);

            assertThat(response.getName()).isEqualTo("Tech & Innovation");
            assertThat(response.getDescription()).isEqualTo("Updated description");
            assertThat(response.getIcon()).isEqualTo("🚀");
            assertThat(response.getDisplayOrder()).isEqualTo(10);
            assertThat(response.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("Success - name is null (not updated)")
        void updateCategory_NameNull() {
            UpdateGroupCategoryRequest request = UpdateGroupCategoryRequest.builder()
                    .description("Updated description")
                    .build();

            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.save(any(GroupCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

            GroupCategoryResponse response = categoryService.updateCategory(CATEGORY_ID, request);

            assertThat(response.getName()).isEqualTo("Technology"); // Original name
            verify(categoryRepository, never()).existsByName(any());
        }

        @Test
        @DisplayName("Success - name same as current (no duplicate check)")
        void updateCategory_NameSameAsCurrent() {
            UpdateGroupCategoryRequest request = UpdateGroupCategoryRequest.builder()
                    .name("Technology") // Same as current
                    .build();

            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.save(any(GroupCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

            GroupCategoryResponse response = categoryService.updateCategory(CATEGORY_ID, request);

            assertThat(response.getName()).isEqualTo("Technology");
            // existsByName should NOT be called since name didn't change
            verify(categoryRepository, never()).existsByName(any());
        }

        @Test
        @DisplayName("Success - name changed (updates slug too)")
        void updateCategory_NameChanged() {
            UpdateGroupCategoryRequest request = UpdateGroupCategoryRequest.builder()
                    .name("Science")
                    .build();

            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.existsByName("Science")).thenReturn(false);
            when(categoryRepository.existsBySlug("science")).thenReturn(false);
            when(categoryRepository.save(any(GroupCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

            GroupCategoryResponse response = categoryService.updateCategory(CATEGORY_ID, request);

            assertThat(response.getName()).isEqualTo("Science");
            assertThat(response.getSlug()).isEqualTo("science");
        }

        @Test
        @DisplayName("Success - description is null (not updated)")
        void updateCategory_DescriptionNull() {
            testCategory.setDescription("Original description");
            UpdateGroupCategoryRequest request = UpdateGroupCategoryRequest.builder()
                    .displayOrder(5)
                    .build();

            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.save(any(GroupCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

            GroupCategoryResponse response = categoryService.updateCategory(CATEGORY_ID, request);

            assertThat(response.getDescription()).isEqualTo("Original description");
        }

        @Test
        @DisplayName("Success - description provided (updated)")
        void updateCategory_DescriptionProvided() {
            UpdateGroupCategoryRequest request = UpdateGroupCategoryRequest.builder()
                    .description("New description")
                    .build();

            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.save(any(GroupCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

            GroupCategoryResponse response = categoryService.updateCategory(CATEGORY_ID, request);

            assertThat(response.getDescription()).isEqualTo("New description");
        }

        @Test
        @DisplayName("Success - icon is null (not updated)")
        void updateCategory_IconNull() {
            testCategory.setIcon("💻");
            UpdateGroupCategoryRequest request = UpdateGroupCategoryRequest.builder()
                    .displayOrder(5)
                    .build();

            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.save(any(GroupCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

            GroupCategoryResponse response = categoryService.updateCategory(CATEGORY_ID, request);

            assertThat(response.getIcon()).isEqualTo("💻");
        }

        @Test
        @DisplayName("Success - icon provided (updated)")
        void updateCategory_IconProvided() {
            UpdateGroupCategoryRequest request = UpdateGroupCategoryRequest.builder()
                    .icon("🎯")
                    .build();

            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.save(any(GroupCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

            GroupCategoryResponse response = categoryService.updateCategory(CATEGORY_ID, request);

            assertThat(response.getIcon()).isEqualTo("🎯");
        }

        @Test
        @DisplayName("Success - parentId is null (not updated)")
        void updateCategory_ParentIdNull() {
            testCategory.setParentId(PARENT_ID);
            UpdateGroupCategoryRequest request = UpdateGroupCategoryRequest.builder()
                    .displayOrder(5)
                    .build();

            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.save(any(GroupCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

            GroupCategoryResponse response = categoryService.updateCategory(CATEGORY_ID, request);

            assertThat(response.getParentId()).isEqualTo(PARENT_ID); // Not changed
            // Should not check for parent when parentId in request is null
            verify(categoryRepository, times(1)).findById(any()); // Only for the category itself
        }

        @Test
        @DisplayName("Success - parentId provided (updated)")
        void updateCategory_ParentIdProvided() {
            UpdateGroupCategoryRequest request = UpdateGroupCategoryRequest.builder()
                    .parentId(PARENT_ID)
                    .build();

            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.findById(PARENT_ID)).thenReturn(Optional.of(parentCategory));
            when(categoryRepository.save(any(GroupCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

            GroupCategoryResponse response = categoryService.updateCategory(CATEGORY_ID, request);

            assertThat(response.getParentId()).isEqualTo(PARENT_ID);
        }

        @Test
        @DisplayName("Success - displayOrder is null (not updated)")
        void updateCategory_DisplayOrderNull() {
            testCategory.setDisplayOrder(5);
            UpdateGroupCategoryRequest request = UpdateGroupCategoryRequest.builder()
                    .description("Updated")
                    .build();

            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.save(any(GroupCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

            GroupCategoryResponse response = categoryService.updateCategory(CATEGORY_ID, request);

            assertThat(response.getDisplayOrder()).isEqualTo(5);
        }

        @Test
        @DisplayName("Success - displayOrder provided (updated)")
        void updateCategory_DisplayOrderProvided() {
            UpdateGroupCategoryRequest request = UpdateGroupCategoryRequest.builder()
                    .displayOrder(99)
                    .build();

            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.save(any(GroupCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

            GroupCategoryResponse response = categoryService.updateCategory(CATEGORY_ID, request);

            assertThat(response.getDisplayOrder()).isEqualTo(99);
        }

        @Test
        @DisplayName("Success - isActive is null (not updated)")
        void updateCategory_IsActiveNull() {
            testCategory.setIsActive(true);
            UpdateGroupCategoryRequest request = UpdateGroupCategoryRequest.builder()
                    .description("Updated")
                    .build();

            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.save(any(GroupCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

            GroupCategoryResponse response = categoryService.updateCategory(CATEGORY_ID, request);

            assertThat(response.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("Success - isActive provided (updated)")
        void updateCategory_IsActiveProvided() {
            UpdateGroupCategoryRequest request = UpdateGroupCategoryRequest.builder()
                    .isActive(false)
                    .build();

            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.save(any(GroupCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

            GroupCategoryResponse response = categoryService.updateCategory(CATEGORY_ID, request);

            assertThat(response.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("Category not found - throws ValidationException")
        void updateCategory_NotFound() {
            UpdateGroupCategoryRequest request = UpdateGroupCategoryRequest.builder()
                    .name("Updated")
                    .build();

            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.updateCategory(CATEGORY_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Category not found");
        }

        @Test
        @DisplayName("Name already exists - throws ValidationException")
        void updateCategory_NameAlreadyExists() {
            UpdateGroupCategoryRequest request = UpdateGroupCategoryRequest.builder()
                    .name("Existing Name")
                    .build();

            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.existsByName("Existing Name")).thenReturn(true);

            assertThatThrownBy(() -> categoryService.updateCategory(CATEGORY_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Category with this name already exists");
        }

        @Test
        @DisplayName("Self-parent - throws ValidationException")
        void updateCategory_SelfParent() {
            UpdateGroupCategoryRequest request = UpdateGroupCategoryRequest.builder()
                    .parentId(CATEGORY_ID) // Same as category being updated
                    .build();

            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));

            assertThatThrownBy(() -> categoryService.updateCategory(CATEGORY_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Category cannot be its own parent");
        }

        @Test
        @DisplayName("Parent not found - throws ValidationException")
        void updateCategory_ParentNotFound() {
            UpdateGroupCategoryRequest request = UpdateGroupCategoryRequest.builder()
                    .parentId(PARENT_ID)
                    .build();

            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.findById(PARENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.updateCategory(CATEGORY_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Parent category not found");
        }

        @Test
        @DisplayName("Race condition (DataIntegrityViolationException) - throws ValidationException")
        void updateCategory_RaceCondition() {
            UpdateGroupCategoryRequest request = UpdateGroupCategoryRequest.builder()
                    .name("New Name")
                    .build();

            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.existsByName("New Name")).thenReturn(false);
            when(categoryRepository.existsBySlug("new-name")).thenReturn(false);
            when(categoryRepository.save(any(GroupCategory.class)))
                    .thenThrow(new DataIntegrityViolationException("Duplicate entry"));

            assertThatThrownBy(() -> categoryService.updateCategory(CATEGORY_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Category with this name already exists");
        }
    }

    // ==================== DELETE CATEGORY ====================

    @Nested
    @DisplayName("deleteCategory")
    class DeleteCategoryTests {

        @Test
        @DisplayName("Success - deletes category")
        void deleteCategory_Success() {
            testCategory.setGroupCount(0);
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.countByParentId(CATEGORY_ID)).thenReturn(0L);

            categoryService.deleteCategory(CATEGORY_ID);

            verify(categoryRepository).delete(testCategory);
        }

        @Test
        @DisplayName("Success - groupCount is null (treated as 0)")
        void deleteCategory_GroupCountNull() {
            testCategory.setGroupCount(null);
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.countByParentId(CATEGORY_ID)).thenReturn(0L);

            categoryService.deleteCategory(CATEGORY_ID);

            verify(categoryRepository).delete(testCategory);
        }

        @Test
        @DisplayName("Category not found - throws ValidationException")
        void deleteCategory_NotFound() {
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.deleteCategory(CATEGORY_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Category not found");

            verify(categoryRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Category has groups - throws ValidationException")
        void deleteCategory_HasGroups() {
            testCategory.setGroupCount(5);
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));

            assertThatThrownBy(() -> categoryService.deleteCategory(CATEGORY_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Cannot delete category with existing groups");

            verify(categoryRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Category has subcategories - throws ValidationException")
        void deleteCategory_HasSubcategories() {
            testCategory.setGroupCount(0);
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.countByParentId(CATEGORY_ID)).thenReturn(3L);

            assertThatThrownBy(() -> categoryService.deleteCategory(CATEGORY_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Cannot delete category with subcategories");

            verify(categoryRepository, never()).delete(any());
        }
    }

    // ==================== GET CATEGORY ====================

    @Nested
    @DisplayName("getCategory")
    class GetCategoryTests {

        @Test
        @DisplayName("Success - returns category")
        void getCategory_Success() {
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));

            GroupCategoryResponse response = categoryService.getCategory(CATEGORY_ID);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(CATEGORY_ID);
            assertThat(response.getName()).isEqualTo("Technology");
        }

        @Test
        @DisplayName("Category not found - throws ValidationException")
        void getCategory_NotFound() {
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.getCategory(CATEGORY_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Category not found");
        }
    }

    // ==================== GET CATEGORY BY SLUG ====================

    @Nested
    @DisplayName("getCategoryBySlug")
    class GetCategoryBySlugTests {

        @Test
        @DisplayName("Success - returns category")
        void getCategoryBySlug_Success() {
            when(categoryRepository.findBySlug("technology")).thenReturn(Optional.of(testCategory));

            GroupCategoryResponse response = categoryService.getCategoryBySlug("technology");

            assertThat(response).isNotNull();
            assertThat(response.getSlug()).isEqualTo("technology");
        }

        @Test
        @DisplayName("Slug is null - throws ValidationException")
        void getCategoryBySlug_NullSlug() {
            assertThatThrownBy(() -> categoryService.getCategoryBySlug(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Slug is required");
        }

        @Test
        @DisplayName("Slug is blank - throws ValidationException")
        void getCategoryBySlug_BlankSlug() {
            assertThatThrownBy(() -> categoryService.getCategoryBySlug("   "))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Slug is required");
        }

        @Test
        @DisplayName("Slug contains null character - sanitized")
        void getCategoryBySlug_NullCharacterSanitized() {
            when(categoryRepository.findBySlug("technology")).thenReturn(Optional.of(testCategory));

            GroupCategoryResponse response = categoryService.getCategoryBySlug("tech\u0000nology");

            assertThat(response).isNotNull();
            verify(categoryRepository).findBySlug("technology");
        }

        @Test
        @DisplayName("Category not found - throws ValidationException")
        void getCategoryBySlug_NotFound() {
            when(categoryRepository.findBySlug("nonexistent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.getCategoryBySlug("nonexistent"))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Category not found with slug");
        }
    }

    // ==================== GET ALL CATEGORIES (PAGINATED) ====================

    @Nested
    @DisplayName("getAllCategories")
    class GetAllCategoriesTests {

        @Test
        @DisplayName("Success - returns paginated categories")
        void getAllCategories_Success() {
            Page<GroupCategory> page = new PageImpl<>(
                    List.of(testCategory),
                    PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "displayOrder")),
                    1
            );

            when(categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc(any(Pageable.class)))
                    .thenReturn(page);

            PageResponse<GroupCategoryResponse> response = categoryService.getAllCategories(0, 20);

            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("Success - empty list")
        void getAllCategories_Empty() {
            Page<GroupCategory> emptyPage = new PageImpl<>(
                    Collections.emptyList(),
                    PageRequest.of(0, 20),
                    0
            );

            when(categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc(any(Pageable.class)))
                    .thenReturn(emptyPage);

            PageResponse<GroupCategoryResponse> response = categoryService.getAllCategories(0, 20);

            assertThat(response.getContent()).isEmpty();
        }

        @Test
        @DisplayName("Success - custom pagination")
        void getAllCategories_CustomPagination() {
            Page<GroupCategory> page = new PageImpl<>(
                    Collections.emptyList(),
                    PageRequest.of(5, 50),
                    0
            );

            when(categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc(any(Pageable.class)))
                    .thenReturn(page);

            PageResponse<GroupCategoryResponse> response = categoryService.getAllCategories(5, 50);

            assertThat(response.getPage()).isEqualTo(5);
            assertThat(response.getSize()).isEqualTo(50);
        }
    }

    // ==================== GET ALL CATEGORIES LIST (NON-PAGINATED) ====================

    @Nested
    @DisplayName("getAllCategoriesList")
    class GetAllCategoriesListTests {

        @Test
        @DisplayName("Success - returns all categories")
        void getAllCategoriesList_Success() {
            when(categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc())
                    .thenReturn(List.of(testCategory));

            List<GroupCategoryResponse> response = categoryService.getAllCategoriesList();

            assertThat(response).hasSize(1);
            assertThat(response.get(0).getName()).isEqualTo("Technology");
        }

        @Test
        @DisplayName("Success - empty list")
        void getAllCategoriesList_Empty() {
            when(categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc())
                    .thenReturn(Collections.emptyList());

            List<GroupCategoryResponse> response = categoryService.getAllCategoriesList();

            assertThat(response).isEmpty();
        }

        @Test
        @DisplayName("Success - multiple categories")
        void getAllCategoriesList_Multiple() {
            GroupCategory category2 = GroupCategory.builder()
                    .id(OTHER_CATEGORY_ID)
                    .name("Sports")
                    .slug("sports")
                    .displayOrder(2)
                    .isActive(true)
                    .build();
            category2.setCreatedAt(LocalDateTime.now());

            when(categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc())
                    .thenReturn(List.of(testCategory, category2));

            List<GroupCategoryResponse> response = categoryService.getAllCategoriesList();

            assertThat(response).hasSize(2);
        }
    }

    // ==================== GET ROOT CATEGORIES ====================

    @Nested
    @DisplayName("getRootCategories")
    class GetRootCategoriesTests {

        @Test
        @DisplayName("Success - returns root categories")
        void getRootCategories_Success() {
            Page<GroupCategory> page = new PageImpl<>(
                    List.of(testCategory),
                    PageRequest.of(0, 20),
                    1
            );

            when(categoryRepository.findByParentIdIsNullAndIsActiveTrueOrderByDisplayOrderAsc(any(Pageable.class)))
                    .thenReturn(page);

            PageResponse<GroupCategoryResponse> response = categoryService.getRootCategories(0, 20);

            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getContent().get(0).getParentId()).isNull();
        }

        @Test
        @DisplayName("Success - empty list")
        void getRootCategories_Empty() {
            Page<GroupCategory> emptyPage = new PageImpl<>(
                    Collections.emptyList(),
                    PageRequest.of(0, 20),
                    0
            );

            when(categoryRepository.findByParentIdIsNullAndIsActiveTrueOrderByDisplayOrderAsc(any(Pageable.class)))
                    .thenReturn(emptyPage);

            PageResponse<GroupCategoryResponse> response = categoryService.getRootCategories(0, 20);

            assertThat(response.getContent()).isEmpty();
        }
    }

    // ==================== GET SUBCATEGORIES ====================

    @Nested
    @DisplayName("getSubcategories")
    class GetSubcategoriesTests {

        @Test
        @DisplayName("Success - returns subcategories")
        void getSubcategories_Success() {
            GroupCategory subcategory = GroupCategory.builder()
                    .id(OTHER_CATEGORY_ID)
                    .name("Programming")
                    .slug("programming")
                    .parentId(PARENT_ID)
                    .displayOrder(1)
                    .isActive(true)
                    .build();
            subcategory.setCreatedAt(LocalDateTime.now());

            Page<GroupCategory> page = new PageImpl<>(
                    List.of(subcategory),
                    PageRequest.of(0, 20),
                    1
            );

            when(categoryRepository.findById(PARENT_ID)).thenReturn(Optional.of(parentCategory));
            when(categoryRepository.findByParentIdAndIsActiveTrueOrderByDisplayOrderAsc(eq(PARENT_ID), any(Pageable.class)))
                    .thenReturn(page);

            PageResponse<GroupCategoryResponse> response = categoryService.getSubcategories(PARENT_ID, 0, 20);

            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getContent().get(0).getParentId()).isEqualTo(PARENT_ID);
        }

        @Test
        @DisplayName("Success - empty list")
        void getSubcategories_Empty() {
            Page<GroupCategory> emptyPage = new PageImpl<>(
                    Collections.emptyList(),
                    PageRequest.of(0, 20),
                    0
            );

            when(categoryRepository.findById(PARENT_ID)).thenReturn(Optional.of(parentCategory));
            when(categoryRepository.findByParentIdAndIsActiveTrueOrderByDisplayOrderAsc(eq(PARENT_ID), any(Pageable.class)))
                    .thenReturn(emptyPage);

            PageResponse<GroupCategoryResponse> response = categoryService.getSubcategories(PARENT_ID, 0, 20);

            assertThat(response.getContent()).isEmpty();
        }

        @Test
        @DisplayName("Parent not found - throws ValidationException")
        void getSubcategories_ParentNotFound() {
            when(categoryRepository.findById(PARENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.getSubcategories(PARENT_ID, 0, 20))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Parent category not found");
        }
    }

    // ==================== SEARCH CATEGORIES ====================

    @Nested
    @DisplayName("searchCategories")
    class SearchCategoriesTests {

        @Test
        @DisplayName("Success - finds by name")
        void searchCategories_FindsByName() {
            when(categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc())
                    .thenReturn(List.of(testCategory));

            PageResponse<GroupCategoryResponse> response = categoryService.searchCategories("Tech", 0, 20);

            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getContent().get(0).getName()).isEqualTo("Technology");
        }

        @Test
        @DisplayName("Success - finds by description")
        void searchCategories_FindsByDescription() {
            testCategory.setDescription("Groups about technology and innovation");
            when(categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc())
                    .thenReturn(List.of(testCategory));

            PageResponse<GroupCategoryResponse> response = categoryService.searchCategories("innovation", 0, 20);

            assertThat(response.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Success - name matches (description check short-circuited)")
        void searchCategories_NameMatchesShortCircuit() {
            testCategory.setName("Technology");
            testCategory.setDescription("Something else entirely");
            when(categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc())
                    .thenReturn(List.of(testCategory));

            PageResponse<GroupCategoryResponse> response = categoryService.searchCategories("Tech", 0, 20);

            assertThat(response.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Success - name doesn't match but description matches")
        void searchCategories_NameNoMatchDescriptionMatches() {
            testCategory.setName("Sports");
            testCategory.setDescription("Technology and innovation");
            when(categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc())
                    .thenReturn(List.of(testCategory));

            PageResponse<GroupCategoryResponse> response = categoryService.searchCategories("Tech", 0, 20);

            assertThat(response.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Filtered out - name is null and description is null")
        void searchCategories_BothNameAndDescriptionNull() {
            testCategory.setName(null);
            testCategory.setDescription(null);
            when(categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc())
                    .thenReturn(List.of(testCategory));

            PageResponse<GroupCategoryResponse> response = categoryService.searchCategories("Tech", 0, 20);

            assertThat(response.getContent()).isEmpty();
        }

        @Test
        @DisplayName("Filtered out - name is null and description doesn't match")
        void searchCategories_NameNullDescriptionNoMatch() {
            testCategory.setName(null);
            testCategory.setDescription("Sports and fitness");
            when(categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc())
                    .thenReturn(List.of(testCategory));

            PageResponse<GroupCategoryResponse> response = categoryService.searchCategories("Tech", 0, 20);

            assertThat(response.getContent()).isEmpty();
        }

        @Test
        @DisplayName("Filtered out - name doesn't match and description is null")
        void searchCategories_NameNoMatchDescriptionNull() {
            testCategory.setName("Sports");
            testCategory.setDescription(null);
            when(categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc())
                    .thenReturn(List.of(testCategory));

            PageResponse<GroupCategoryResponse> response = categoryService.searchCategories("Tech", 0, 20);

            assertThat(response.getContent()).isEmpty();
        }

        @Test
        @DisplayName("Filtered out - neither name nor description matches")
        void searchCategories_NeitherMatches() {
            testCategory.setName("Sports");
            testCategory.setDescription("Fitness and wellness");
            when(categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc())
                    .thenReturn(List.of(testCategory));

            PageResponse<GroupCategoryResponse> response = categoryService.searchCategories("Tech", 0, 20);

            assertThat(response.getContent()).isEmpty();
        }

        @Test
        @DisplayName("Success - case insensitive search")
        void searchCategories_CaseInsensitive() {
            when(categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc())
                    .thenReturn(List.of(testCategory));

            PageResponse<GroupCategoryResponse> response = categoryService.searchCategories("TECHNOLOGY", 0, 20);

            assertThat(response.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Success - no matches")
        void searchCategories_NoMatches() {
            when(categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc())
                    .thenReturn(List.of(testCategory));

            PageResponse<GroupCategoryResponse> response = categoryService.searchCategories("xyz", 0, 20);

            assertThat(response.getContent()).isEmpty();
            assertThat(response.getTotalElements()).isEqualTo(0);
        }

        @Test
        @DisplayName("Success - with pagination (page 0)")
        void searchCategories_PaginationPage0() {
            GroupCategory category1 = createCategory("Category A", "category-a");
            GroupCategory category2 = createCategory("Category B", "category-b");
            GroupCategory category3 = createCategory("Category C", "category-c");

            when(categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc())
                    .thenReturn(List.of(category1, category2, category3));

            PageResponse<GroupCategoryResponse> response = categoryService.searchCategories("Category", 0, 2);

            assertThat(response.getContent()).hasSize(2);
            assertThat(response.getPage()).isEqualTo(0);
            assertThat(response.getTotalElements()).isEqualTo(3);
            assertThat(response.getTotalPages()).isEqualTo(2);
            assertThat(response.isFirst()).isTrue();
            assertThat(response.isLast()).isFalse();
        }

        @Test
        @DisplayName("Success - with pagination (page 1)")
        void searchCategories_PaginationPage1() {
            GroupCategory category1 = createCategory("Category A", "category-a");
            GroupCategory category2 = createCategory("Category B", "category-b");
            GroupCategory category3 = createCategory("Category C", "category-c");

            when(categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc())
                    .thenReturn(List.of(category1, category2, category3));

            PageResponse<GroupCategoryResponse> response = categoryService.searchCategories("Category", 1, 2);

            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getPage()).isEqualTo(1);
            assertThat(response.isFirst()).isFalse();
            assertThat(response.isLast()).isTrue();
        }

        @Test
        @DisplayName("Success - page beyond results (returns empty)")
        void searchCategories_PageBeyondResults() {
            when(categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc())
                    .thenReturn(List.of(testCategory));

            PageResponse<GroupCategoryResponse> response = categoryService.searchCategories("Tech", 10, 20);

            assertThat(response.getContent()).isEmpty();
            assertThat(response.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("Success - category with null name (filtered correctly)")
        void searchCategories_NullName() {
            testCategory.setName(null);
            testCategory.setDescription("Tech description");
            when(categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc())
                    .thenReturn(List.of(testCategory));

            PageResponse<GroupCategoryResponse> response = categoryService.searchCategories("Tech", 0, 20);

            assertThat(response.getContent()).hasSize(1); // Found by description
        }

        @Test
        @DisplayName("Success - category with null description (filtered correctly)")
        void searchCategories_NullDescription() {
            testCategory.setDescription(null);
            when(categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc())
                    .thenReturn(List.of(testCategory));

            PageResponse<GroupCategoryResponse> response = categoryService.searchCategories("Tech", 0, 20);

            assertThat(response.getContent()).hasSize(1); // Found by name
        }

        @Test
        @DisplayName("Sanitized query is empty - returns empty response")
        void searchCategories_SanitizedQueryEmpty() {
            // Assuming InputValidator.sanitizeSearchQuery returns empty for invalid input
            PageResponse<GroupCategoryResponse> response = categoryService.searchCategories("", 0, 20);

            assertThat(response.getContent()).isEmpty();
            assertThat(response.isEmpty()).isTrue();
            verifyNoInteractions(categoryRepository);
        }
    }

    // ==================== MAP TO RESPONSE ====================

    @Nested
    @DisplayName("mapToResponse (via getCategory)")
    class MapToResponseTests {

        @Test
        @DisplayName("Maps all fields correctly")
        void mapToResponse_AllFields() {
            testCategory.setParentId(PARENT_ID);
            testCategory.setGroupCount(42);

            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));

            GroupCategoryResponse response = categoryService.getCategory(CATEGORY_ID);

            assertThat(response.getId()).isEqualTo(CATEGORY_ID);
            assertThat(response.getName()).isEqualTo("Technology");
            assertThat(response.getSlug()).isEqualTo("technology");
            assertThat(response.getDescription()).isEqualTo("Technology related groups");
            assertThat(response.getIcon()).isEqualTo("💻");
            assertThat(response.getParentId()).isEqualTo(PARENT_ID);
            assertThat(response.getDisplayOrder()).isEqualTo(1);
            assertThat(response.getGroupCount()).isEqualTo(42);
            assertThat(response.getIsActive()).isTrue();
            assertThat(response.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("Maps with null optional fields")
        void mapToResponse_NullOptionalFields() {
            testCategory.setDescription(null);
            testCategory.setIcon(null);
            testCategory.setParentId(null);
            testCategory.setGroupCount(null);

            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));

            GroupCategoryResponse response = categoryService.getCategory(CATEGORY_ID);

            assertThat(response.getDescription()).isNull();
            assertThat(response.getIcon()).isNull();
            assertThat(response.getParentId()).isNull();
            assertThat(response.getGroupCount()).isNull();
        }
    }

    // ==================== HELPER METHODS ====================

    private GroupCategory createCategory(String name, String slug) {
        GroupCategory category = GroupCategory.builder()
                .id(UUID.randomUUID())
                .name(name)
                .slug(slug)
                .displayOrder(1)
                .isActive(true)
                .build();
        category.setCreatedAt(LocalDateTime.now());
        return category;
    }
}