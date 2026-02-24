package com.billboard.social.group.controller;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.exception.GlobalExceptionHandler;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.common.security.UserPrincipal;
import com.billboard.social.group.dto.request.GroupCategoryRequests.CreateGroupCategoryRequest;
import com.billboard.social.group.dto.request.GroupCategoryRequests.UpdateGroupCategoryRequest;
import com.billboard.social.group.dto.response.GroupCategoryResponses.GroupCategoryResponse;
import com.billboard.social.group.service.GroupCategoryService;
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

@WebMvcTest(GroupCategoryController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class GroupCategoryControllerTest {

    private static final Long USER_ID = 1L;
    private static final UUID CATEGORY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PARENT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String BASE_URL = "/groups/categories";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GroupCategoryService categoryService;

    private GroupCategoryResponse testCategoryResponse;

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

        testCategoryResponse = GroupCategoryResponse.builder()
                .id(CATEGORY_ID)
                .name("Technology")
                .slug("technology")
                .description("Technology related groups")
                .icon("https://example.com/icon.png")
                .displayOrder(1)
                .isActive(true)
                .groupCount(10)
                .parentId(null)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ==================== CREATE CATEGORY ====================

    @Nested
    @DisplayName("POST /groups/categories - createCategory")
    class CreateCategoryTests {

        @Test
        @DisplayName("Success - returns 201")
        void createCategory_Success() throws Exception {
            CreateGroupCategoryRequest request = CreateGroupCategoryRequest.builder()
                    .name("Technology")
                    .description("Technology related groups")
                    .icon("https://example.com/icon.png")
                    .displayOrder(1)
                    .isActive(true)
                    .build();

            when(categoryService.createCategory(any(CreateGroupCategoryRequest.class)))
                    .thenReturn(testCategoryResponse);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(CATEGORY_ID.toString()))
                    .andExpect(jsonPath("$.name").value("Technology"))
                    .andExpect(jsonPath("$.slug").value("technology"))
                    .andExpect(jsonPath("$.description").value("Technology related groups"))
                    .andExpect(jsonPath("$.isActive").value(true));
        }

        @Test
        @DisplayName("Success - with parent category")
        void createCategory_WithParent() throws Exception {
            CreateGroupCategoryRequest request = CreateGroupCategoryRequest.builder()
                    .name("Programming")
                    .parentId(PARENT_ID)
                    .build();

            testCategoryResponse.setParentId(PARENT_ID);
            testCategoryResponse.setName("Programming");
            testCategoryResponse.setSlug("programming");
            when(categoryService.createCategory(any(CreateGroupCategoryRequest.class)))
                    .thenReturn(testCategoryResponse);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.parentId").value(PARENT_ID.toString()));
        }

        @Test
        @DisplayName("Success - minimal fields")
        void createCategory_MinimalFields() throws Exception {
            CreateGroupCategoryRequest request = CreateGroupCategoryRequest.builder()
                    .name("Sports")
                    .build();

            testCategoryResponse.setName("Sports");
            testCategoryResponse.setSlug("sports");
            testCategoryResponse.setDescription(null);
            when(categoryService.createCategory(any(CreateGroupCategoryRequest.class)))
                    .thenReturn(testCategoryResponse);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("Missing name - returns 400")
        void createCategory_MissingName() throws Exception {
            CreateGroupCategoryRequest request = CreateGroupCategoryRequest.builder()
                    .description("Some description")
                    .build();

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(categoryService);
        }

        @Test
        @DisplayName("Category already exists - returns 400")
        void createCategory_AlreadyExists() throws Exception {
            CreateGroupCategoryRequest request = CreateGroupCategoryRequest.builder()
                    .name("Technology")
                    .build();

            when(categoryService.createCategory(any(CreateGroupCategoryRequest.class)))
                    .thenThrow(new ValidationException("Category with slug 'technology' already exists"));

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Category with slug 'technology' already exists"));
        }

        @Test
        @DisplayName("Parent category not found - returns 400")
        void createCategory_ParentNotFound() throws Exception {
            CreateGroupCategoryRequest request = CreateGroupCategoryRequest.builder()
                    .name("Programming")
                    .parentId(PARENT_ID)
                    .build();

            when(categoryService.createCategory(any(CreateGroupCategoryRequest.class)))
                    .thenThrow(new ValidationException("Parent category not found"));

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Missing request body - returns 400")
        void createCategory_MissingBody() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(categoryService);
        }

        @Test
        @DisplayName("Malformed JSON - returns 400")
        void createCategory_MalformedJson() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": }"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(categoryService);
        }
    }

    // ==================== GET CATEGORY ====================

    @Nested
    @DisplayName("GET /groups/categories/{categoryId} - getCategory")
    class GetCategoryTests {

        @Test
        @DisplayName("Success - returns 200")
        void getCategory_Success() throws Exception {
            when(categoryService.getCategory(CATEGORY_ID)).thenReturn(testCategoryResponse);

            mockMvc.perform(get(BASE_URL + "/{categoryId}", CATEGORY_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(CATEGORY_ID.toString()))
                    .andExpect(jsonPath("$.name").value("Technology"))
                    .andExpect(jsonPath("$.slug").value("technology"));
        }

        @Test
        @DisplayName("Category not found - returns 400")
        void getCategory_NotFound() throws Exception {
            when(categoryService.getCategory(CATEGORY_ID))
                    .thenThrow(new ValidationException("Category not found"));

            mockMvc.perform(get(BASE_URL + "/{categoryId}", CATEGORY_ID))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Category not found"));
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void getCategory_InvalidUuid() throws Exception {
            mockMvc.perform(get(BASE_URL + "/{categoryId}", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(categoryService);
        }
    }

    // ==================== GET CATEGORY BY SLUG ====================

    @Nested
    @DisplayName("GET /api/v1/groups/categories/slug/{slug} - getCategoryBySlug")
    class GetCategoryBySlugTests {

        @Test
        @DisplayName("Success - returns 200")
        void getCategoryBySlug_Success() throws Exception {
            when(categoryService.getCategoryBySlug("technology")).thenReturn(testCategoryResponse);

            mockMvc.perform(get(BASE_URL + "/slug/{slug}", "technology"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.slug").value("technology"))
                    .andExpect(jsonPath("$.name").value("Technology"));
        }

        @Test
        @DisplayName("Category not found - returns 400")
        void getCategoryBySlug_NotFound() throws Exception {
            when(categoryService.getCategoryBySlug("nonexistent"))
                    .thenThrow(new ValidationException("Category not found with slug: nonexistent"));

            mockMvc.perform(get(BASE_URL + "/slug/{slug}", "nonexistent"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Slug too long (>120 chars) - returns 400")
        void getCategoryBySlug_TooLong() throws Exception {
            String longSlug = "a".repeat(121);

            mockMvc.perform(get(BASE_URL + "/slug/{slug}", longSlug))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(categoryService);
        }

        @Test
        @DisplayName("Slug at max length (120 chars) - returns 200")
        void getCategoryBySlug_MaxLength() throws Exception {
            String maxSlug = "a".repeat(120);
            when(categoryService.getCategoryBySlug(maxSlug)).thenReturn(testCategoryResponse);

            mockMvc.perform(get(BASE_URL + "/slug/{slug}", maxSlug))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Slug with hyphens - returns 200")
        void getCategoryBySlug_WithHyphens() throws Exception {
            when(categoryService.getCategoryBySlug("web-development")).thenReturn(testCategoryResponse);

            mockMvc.perform(get(BASE_URL + "/slug/{slug}", "web-development"))
                    .andExpect(status().isOk());
        }
    }

    // ==================== UPDATE CATEGORY ====================

    @Nested
    @DisplayName("PUT /groups/categories/{categoryId} - updateCategory")
    class UpdateCategoryTests {

        @Test
        @DisplayName("Success - returns 200")
        void updateCategory_Success() throws Exception {
            UpdateGroupCategoryRequest request = UpdateGroupCategoryRequest.builder()
                    .name("Tech & Innovation")
                    .description("Updated description")
                    .displayOrder(2)
                    .build();

            testCategoryResponse.setName("Tech & Innovation");
            testCategoryResponse.setDescription("Updated description");
            testCategoryResponse.setDisplayOrder(2);
            when(categoryService.updateCategory(eq(CATEGORY_ID), any(UpdateGroupCategoryRequest.class)))
                    .thenReturn(testCategoryResponse);

            mockMvc.perform(put(BASE_URL + "/{categoryId}", CATEGORY_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Tech & Innovation"))
                    .andExpect(jsonPath("$.description").value("Updated description"));
        }

        @Test
        @DisplayName("Success - update only name")
        void updateCategory_OnlyName() throws Exception {
            UpdateGroupCategoryRequest request = UpdateGroupCategoryRequest.builder()
                    .name("Updated Name")
                    .build();

            testCategoryResponse.setName("Updated Name");
            when(categoryService.updateCategory(eq(CATEGORY_ID), any(UpdateGroupCategoryRequest.class)))
                    .thenReturn(testCategoryResponse);

            mockMvc.perform(put(BASE_URL + "/{categoryId}", CATEGORY_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Updated Name"));
        }

        @Test
        @DisplayName("Success - update isActive")
        void updateCategory_IsActive() throws Exception {
            UpdateGroupCategoryRequest request = UpdateGroupCategoryRequest.builder()
                    .isActive(false)
                    .build();

            testCategoryResponse.setIsActive(false);
            when(categoryService.updateCategory(eq(CATEGORY_ID), any(UpdateGroupCategoryRequest.class)))
                    .thenReturn(testCategoryResponse);

            mockMvc.perform(put(BASE_URL + "/{categoryId}", CATEGORY_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isActive").value(false));
        }

        @Test
        @DisplayName("Category not found - returns 400")
        void updateCategory_NotFound() throws Exception {
            UpdateGroupCategoryRequest request = UpdateGroupCategoryRequest.builder()
                    .name("Updated Name")
                    .build();

            when(categoryService.updateCategory(eq(CATEGORY_ID), any(UpdateGroupCategoryRequest.class)))
                    .thenThrow(new ValidationException("Category not found"));

            mockMvc.perform(put(BASE_URL + "/{categoryId}", CATEGORY_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void updateCategory_InvalidUuid() throws Exception {
            UpdateGroupCategoryRequest request = UpdateGroupCategoryRequest.builder()
                    .name("Updated Name")
                    .build();

            mockMvc.perform(put(BASE_URL + "/{categoryId}", "invalid-uuid")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(categoryService);
        }

        @Test
        @DisplayName("Missing request body - returns 400")
        void updateCategory_MissingBody() throws Exception {
            mockMvc.perform(put(BASE_URL + "/{categoryId}", CATEGORY_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(categoryService);
        }

        @Test
        @DisplayName("Empty body - updates nothing, returns 200")
        void updateCategory_EmptyBody() throws Exception {
            UpdateGroupCategoryRequest request = UpdateGroupCategoryRequest.builder().build();

            when(categoryService.updateCategory(eq(CATEGORY_ID), any(UpdateGroupCategoryRequest.class)))
                    .thenReturn(testCategoryResponse);

            mockMvc.perform(put(BASE_URL + "/{categoryId}", CATEGORY_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }
    }

    // ==================== DELETE CATEGORY ====================

    @Nested
    @DisplayName("DELETE /groups/categories/{categoryId} - deleteCategory")
    class DeleteCategoryTests {

        @Test
        @DisplayName("Success - returns 204")
        void deleteCategory_Success() throws Exception {
            doNothing().when(categoryService).deleteCategory(CATEGORY_ID);

            mockMvc.perform(delete(BASE_URL + "/{categoryId}", CATEGORY_ID))
                    .andExpect(status().isNoContent());

            verify(categoryService).deleteCategory(CATEGORY_ID);
        }

        @Test
        @DisplayName("Category not found - returns 400")
        void deleteCategory_NotFound() throws Exception {
            doThrow(new ValidationException("Category not found"))
                    .when(categoryService).deleteCategory(CATEGORY_ID);

            mockMvc.perform(delete(BASE_URL + "/{categoryId}", CATEGORY_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Category has groups - returns 400")
        void deleteCategory_HasGroups() throws Exception {
            doThrow(new ValidationException("Cannot delete category with existing groups"))
                    .when(categoryService).deleteCategory(CATEGORY_ID);

            mockMvc.perform(delete(BASE_URL + "/{categoryId}", CATEGORY_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Category has subcategories - returns 400")
        void deleteCategory_HasSubcategories() throws Exception {
            doThrow(new ValidationException("Cannot delete category with subcategories"))
                    .when(categoryService).deleteCategory(CATEGORY_ID);

            mockMvc.perform(delete(BASE_URL + "/{categoryId}", CATEGORY_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void deleteCategory_InvalidUuid() throws Exception {
            mockMvc.perform(delete(BASE_URL + "/{categoryId}", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(categoryService);
        }
    }

    // ==================== GET ALL CATEGORIES (PAGINATED) ====================

    @Nested
    @DisplayName("GET /groups/categories - getAllCategories")
    class GetAllCategoriesTests {

        @Test
        @DisplayName("Success - returns paginated categories")
        void getAllCategories_Success() throws Exception {
            PageResponse<GroupCategoryResponse> pageResponse = PageResponse.<GroupCategoryResponse>builder()
                    .content(List.of(testCategoryResponse))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .build();

            when(categoryService.getAllCategories(0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].name").value("Technology"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("Success - empty list")
        void getAllCategories_Empty() throws Exception {
            PageResponse<GroupCategoryResponse> pageResponse = PageResponse.<GroupCategoryResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(categoryService.getAllCategories(0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        @DisplayName("Custom pagination")
        void getAllCategories_CustomPagination() throws Exception {
            PageResponse<GroupCategoryResponse> pageResponse = PageResponse.<GroupCategoryResponse>builder()
                    .content(Collections.emptyList())
                    .page(5)
                    .size(50)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(categoryService.getAllCategories(5, 50)).thenReturn(pageResponse);

            mockMvc.perform(get(BASE_URL)
                            .param("page", "5")
                            .param("size", "50"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(5))
                    .andExpect(jsonPath("$.size").value(50));
        }

        @Test
        @DisplayName("Page below minimum - returns 400")
        void getAllCategories_PageBelowMin() throws Exception {
            mockMvc.perform(get(BASE_URL)
                            .param("page", "-1"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(categoryService);
        }

        @Test
        @DisplayName("Page above maximum - returns 400")
        void getAllCategories_PageAboveMax() throws Exception {
            mockMvc.perform(get(BASE_URL)
                            .param("page", "1001"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(categoryService);
        }

        @Test
        @DisplayName("Size below minimum - returns 400")
        void getAllCategories_SizeBelowMin() throws Exception {
            mockMvc.perform(get(BASE_URL)
                            .param("size", "0"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(categoryService);
        }

        @Test
        @DisplayName("Size above maximum - returns 400")
        void getAllCategories_SizeAboveMax() throws Exception {
            mockMvc.perform(get(BASE_URL)
                            .param("size", "101"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(categoryService);
        }

        @Test
        @DisplayName("Boundary - page at max (1000)")
        void getAllCategories_PageAtMax() throws Exception {
            PageResponse<GroupCategoryResponse> pageResponse = PageResponse.<GroupCategoryResponse>builder()
                    .content(Collections.emptyList())
                    .page(1000)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(categoryService.getAllCategories(1000, 20)).thenReturn(pageResponse);

            mockMvc.perform(get(BASE_URL)
                            .param("page", "1000"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Boundary - size at min (1)")
        void getAllCategories_SizeAtMin() throws Exception {
            PageResponse<GroupCategoryResponse> pageResponse = PageResponse.<GroupCategoryResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(1)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(categoryService.getAllCategories(0, 1)).thenReturn(pageResponse);

            mockMvc.perform(get(BASE_URL)
                            .param("size", "1"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Boundary - size at max (100)")
        void getAllCategories_SizeAtMax() throws Exception {
            PageResponse<GroupCategoryResponse> pageResponse = PageResponse.<GroupCategoryResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(100)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(categoryService.getAllCategories(0, 100)).thenReturn(pageResponse);

            mockMvc.perform(get(BASE_URL)
                            .param("size", "100"))
                    .andExpect(status().isOk());
        }
    }

    // ==================== GET ALL CATEGORIES LIST (NON-PAGINATED) ====================

    @Nested
    @DisplayName("GET /groups/categories/list - getAllCategoriesList")
    class GetAllCategoriesListTests {

        @Test
        @DisplayName("Success - returns all categories")
        void getAllCategoriesList_Success() throws Exception {
            when(categoryService.getAllCategoriesList()).thenReturn(List.of(testCategoryResponse));

            mockMvc.perform(get(BASE_URL + "/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].name").value("Technology"));
        }

        @Test
        @DisplayName("Success - empty list")
        void getAllCategoriesList_Empty() throws Exception {
            when(categoryService.getAllCategoriesList()).thenReturn(Collections.emptyList());

            mockMvc.perform(get(BASE_URL + "/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("Success - multiple categories")
        void getAllCategoriesList_Multiple() throws Exception {
            GroupCategoryResponse category2 = GroupCategoryResponse.builder()
                    .id(UUID.randomUUID())
                    .name("Sports")
                    .slug("sports")
                    .isActive(true)
                    .build();

            when(categoryService.getAllCategoriesList())
                    .thenReturn(List.of(testCategoryResponse, category2));

            mockMvc.perform(get(BASE_URL + "/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)));
        }
    }

    // ==================== GET ROOT CATEGORIES ====================

    @Nested
    @DisplayName("GET /groups/categories/root - getRootCategories")
    class GetRootCategoriesTests {

        @Test
        @DisplayName("Success - returns root categories")
        void getRootCategories_Success() throws Exception {
            PageResponse<GroupCategoryResponse> pageResponse = PageResponse.<GroupCategoryResponse>builder()
                    .content(List.of(testCategoryResponse))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .build();

            when(categoryService.getRootCategories(0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get(BASE_URL + "/root"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].parentId").doesNotExist());
        }

        @Test
        @DisplayName("Success - empty list")
        void getRootCategories_Empty() throws Exception {
            PageResponse<GroupCategoryResponse> pageResponse = PageResponse.<GroupCategoryResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(categoryService.getRootCategories(0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get(BASE_URL + "/root"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        @DisplayName("Custom pagination")
        void getRootCategories_CustomPagination() throws Exception {
            PageResponse<GroupCategoryResponse> pageResponse = PageResponse.<GroupCategoryResponse>builder()
                    .content(Collections.emptyList())
                    .page(3)
                    .size(25)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(categoryService.getRootCategories(3, 25)).thenReturn(pageResponse);

            mockMvc.perform(get(BASE_URL + "/root")
                            .param("page", "3")
                            .param("size", "25"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Page above maximum - returns 400")
        void getRootCategories_PageAboveMax() throws Exception {
            mockMvc.perform(get(BASE_URL + "/root")
                            .param("page", "1001"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(categoryService);
        }

        @Test
        @DisplayName("Size below minimum - returns 400")
        void getRootCategories_SizeBelowMin() throws Exception {
            mockMvc.perform(get(BASE_URL + "/root")
                            .param("size", "0"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(categoryService);
        }
    }

    // ==================== GET SUBCATEGORIES ====================

    @Nested
    @DisplayName("GET /groups/categories/{parentId}/subcategories - getSubcategories")
    class GetSubcategoriesTests {

        @Test
        @DisplayName("Success - returns subcategories")
        void getSubcategories_Success() throws Exception {
            GroupCategoryResponse subcategory = GroupCategoryResponse.builder()
                    .id(UUID.randomUUID())
                    .name("Programming")
                    .slug("programming")
                    .parentId(PARENT_ID)
                    .isActive(true)
                    .build();

            PageResponse<GroupCategoryResponse> pageResponse = PageResponse.<GroupCategoryResponse>builder()
                    .content(List.of(subcategory))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .build();

            when(categoryService.getSubcategories(PARENT_ID, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get(BASE_URL + "/{parentId}/subcategories", PARENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].parentId").value(PARENT_ID.toString()));
        }

        @Test
        @DisplayName("Success - empty list")
        void getSubcategories_Empty() throws Exception {
            PageResponse<GroupCategoryResponse> pageResponse = PageResponse.<GroupCategoryResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(categoryService.getSubcategories(PARENT_ID, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get(BASE_URL + "/{parentId}/subcategories", PARENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        @DisplayName("Custom pagination")
        void getSubcategories_CustomPagination() throws Exception {
            PageResponse<GroupCategoryResponse> pageResponse = PageResponse.<GroupCategoryResponse>builder()
                    .content(Collections.emptyList())
                    .page(2)
                    .size(10)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(categoryService.getSubcategories(PARENT_ID, 2, 10)).thenReturn(pageResponse);

            mockMvc.perform(get(BASE_URL + "/{parentId}/subcategories", PARENT_ID)
                            .param("page", "2")
                            .param("size", "10"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Parent not found - returns 400")
        void getSubcategories_ParentNotFound() throws Exception {
            when(categoryService.getSubcategories(PARENT_ID, 0, 20))
                    .thenThrow(new ValidationException("Parent category not found"));

            mockMvc.perform(get(BASE_URL + "/{parentId}/subcategories", PARENT_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void getSubcategories_InvalidUuid() throws Exception {
            mockMvc.perform(get(BASE_URL + "/{parentId}/subcategories", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(categoryService);
        }

        @Test
        @DisplayName("Page above maximum - returns 400")
        void getSubcategories_PageAboveMax() throws Exception {
            mockMvc.perform(get(BASE_URL + "/{parentId}/subcategories", PARENT_ID)
                            .param("page", "1001"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(categoryService);
        }

        @Test
        @DisplayName("Size above maximum - returns 400")
        void getSubcategories_SizeAboveMax() throws Exception {
            mockMvc.perform(get(BASE_URL + "/{parentId}/subcategories", PARENT_ID)
                            .param("size", "101"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(categoryService);
        }
    }

    // ==================== SEARCH CATEGORIES ====================

    @Nested
    @DisplayName("GET /groups/categories/search - searchCategories")
    class SearchCategoriesTests {

        @Test
        @DisplayName("Success - returns matching categories")
        void searchCategories_Success() throws Exception {
            PageResponse<GroupCategoryResponse> pageResponse = PageResponse.<GroupCategoryResponse>builder()
                    .content(List.of(testCategoryResponse))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .build();

            when(categoryService.searchCategories("tech", 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get(BASE_URL + "/search")
                            .param("query", "tech"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].name").value("Technology"));
        }

        @Test
        @DisplayName("Success - empty results")
        void searchCategories_Empty() throws Exception {
            PageResponse<GroupCategoryResponse> pageResponse = PageResponse.<GroupCategoryResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(categoryService.searchCategories("xyz", 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get(BASE_URL + "/search")
                            .param("query", "xyz"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        @DisplayName("Custom pagination")
        void searchCategories_CustomPagination() throws Exception {
            PageResponse<GroupCategoryResponse> pageResponse = PageResponse.<GroupCategoryResponse>builder()
                    .content(Collections.emptyList())
                    .page(3)
                    .size(50)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(categoryService.searchCategories("tech", 3, 50)).thenReturn(pageResponse);

            mockMvc.perform(get(BASE_URL + "/search")
                            .param("query", "tech")
                            .param("page", "3")
                            .param("size", "50"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Missing query parameter - returns 400")
        void searchCategories_MissingQuery() throws Exception {
            mockMvc.perform(get(BASE_URL + "/search"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(categoryService);
        }

        @Test
        @DisplayName("Query too long (>100 chars) - returns 400")
        void searchCategories_QueryTooLong() throws Exception {
            String longQuery = "a".repeat(101);

            mockMvc.perform(get(BASE_URL + "/search")
                            .param("query", longQuery))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(categoryService);
        }

        @Test
        @DisplayName("Query at max length (100 chars) - returns 200")
        void searchCategories_QueryMaxLength() throws Exception {
            String maxQuery = "a".repeat(100);
            PageResponse<GroupCategoryResponse> pageResponse = PageResponse.<GroupCategoryResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(categoryService.searchCategories(maxQuery, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get(BASE_URL + "/search")
                            .param("query", maxQuery))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Single character query - returns 200")
        void searchCategories_SingleCharQuery() throws Exception {
            PageResponse<GroupCategoryResponse> pageResponse = PageResponse.<GroupCategoryResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(categoryService.searchCategories("t", 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get(BASE_URL + "/search")
                            .param("query", "t"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Page above maximum - returns 400")
        void searchCategories_PageAboveMax() throws Exception {
            mockMvc.perform(get(BASE_URL + "/search")
                            .param("query", "tech")
                            .param("page", "1001"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(categoryService);
        }

        @Test
        @DisplayName("Size below minimum - returns 400")
        void searchCategories_SizeBelowMin() throws Exception {
            mockMvc.perform(get(BASE_URL + "/search")
                            .param("query", "tech")
                            .param("size", "0"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(categoryService);
        }
    }
}