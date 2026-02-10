package com.billboard.social.event.controller;

import com.billboard.social.common.exception.GlobalExceptionHandler;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.common.security.UserPrincipal;
import com.billboard.social.event.dto.request.EventRequests.CreateCategoryRequest;
import com.billboard.social.event.dto.request.EventRequests.UpdateCategoryRequest;
import com.billboard.social.event.dto.response.EventResponses.CategoryResponse;
import com.billboard.social.event.service.Eventcategoryservice;
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

@WebMvcTest(EventCategoryController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class EventCategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private Eventcategoryservice categoryService;

    // Test constants
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CATEGORY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private UserPrincipal userPrincipal;
    private UserPrincipal adminPrincipal;
    private CategoryResponse testCategoryResponse;

    @BeforeEach
    void setUp() {
        userPrincipal = UserPrincipal.builder()
                .id(USER_ID)
                .username("testuser")
                .email("test@example.com")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();

        adminPrincipal = UserPrincipal.builder()
                .id(USER_ID)
                .username("admin")
                .email("admin@example.com")
                .authorities(List.of(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("ROLE_ADMIN")
                ))
                .build();

        testCategoryResponse = CategoryResponse.builder()
                .id(CATEGORY_ID)
                .name("Conference")
                .slug("conference")
                .description("Professional conferences and seminars")
                .icon("calendar")
                .color("#3B82F6")
                .isActive(true)
                .displayOrder(1)
                .eventCount(10)
                .createdAt(LocalDateTime.now())
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

    // ==================== GET ALL CATEGORIES ====================

    @Nested
    @DisplayName("GET /event-categories - getAllCategories")
    class GetAllCategoriesTests {

        @Test
        @DisplayName("Success - returns list of active categories")
        void getAllCategories_Success() throws Exception {
            setAuthentication(userPrincipal);

            CategoryResponse category2 = CategoryResponse.builder()
                    .id(UUID.randomUUID())
                    .name("Workshop")
                    .slug("workshop")
                    .isActive(true)
                    .build();

            when(categoryService.getAllActiveCategories()).thenReturn(List.of(testCategoryResponse, category2));

            mockMvc.perform(get("/event-categories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].name").value("Conference"))
                    .andExpect(jsonPath("$[1].name").value("Workshop"));

            verify(categoryService).getAllActiveCategories();
        }

        @Test
        @DisplayName("Success - returns empty list when no categories")
        void getAllCategories_EmptyList() throws Exception {
            setAuthentication(userPrincipal);

            when(categoryService.getAllActiveCategories()).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/event-categories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }
    }

    // ==================== GET ALL CATEGORIES ADMIN ====================

    @Nested
    @DisplayName("GET /event-categories/all - getAllCategoriesAdmin")
    class GetAllCategoriesAdminTests {

        @Test
        @DisplayName("Success - admin gets all categories including inactive")
        void getAllCategoriesAdmin_Success() throws Exception {
            setAuthentication(adminPrincipal);

            CategoryResponse inactiveCategory = CategoryResponse.builder()
                    .id(UUID.randomUUID())
                    .name("Deprecated Category")
                    .slug("deprecated")
                    .isActive(false)
                    .build();

            when(categoryService.getAllCategories()).thenReturn(List.of(testCategoryResponse, inactiveCategory));

            mockMvc.perform(get("/event-categories/all"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].isActive").value(true))
                    .andExpect(jsonPath("$[1].isActive").value(false));

            verify(categoryService).getAllCategories();
        }

        @Test
        @DisplayName("Success - returns empty list")
        void getAllCategoriesAdmin_EmptyList() throws Exception {
            setAuthentication(adminPrincipal);

            when(categoryService.getAllCategories()).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/event-categories/all"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }
    }

    // ==================== GET CATEGORY BY ID ====================

    @Nested
    @DisplayName("GET /event-categories/{id} - getCategoryById")
    class GetCategoryByIdTests {

        @Test
        @DisplayName("Success - returns category")
        void getCategoryById_Success() throws Exception {
            setAuthentication(userPrincipal);

            when(categoryService.getCategoryById(CATEGORY_ID)).thenReturn(testCategoryResponse);

            mockMvc.perform(get("/event-categories/{id}", CATEGORY_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(CATEGORY_ID.toString()))
                    .andExpect(jsonPath("$.name").value("Conference"))
                    .andExpect(jsonPath("$.slug").value("conference"));

            verify(categoryService).getCategoryById(CATEGORY_ID);
        }

        @Test
        @DisplayName("Category not found - returns 400")
        void getCategoryById_NotFound() throws Exception {
            setAuthentication(userPrincipal);

            when(categoryService.getCategoryById(CATEGORY_ID))
                    .thenThrow(new ValidationException("Category not found"));

            mockMvc.perform(get("/event-categories/{id}", CATEGORY_ID))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Category not found"));
        }

        @Test
        @DisplayName("Invalid UUID format - returns 400")
        void getCategoryById_InvalidUuid() throws Exception {
            setAuthentication(userPrincipal);

            mockMvc.perform(get("/event-categories/{id}", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(categoryService);
        }
    }

    // ==================== GET CATEGORY BY SLUG ====================

    @Nested
    @DisplayName("GET /event-categories/slug/{slug} - getCategoryBySlug")
    class GetCategoryBySlugTests {

        @Test
        @DisplayName("Success - returns category")
        void getCategoryBySlug_Success() throws Exception {
            setAuthentication(userPrincipal);

            when(categoryService.getCategoryBySlug("conference")).thenReturn(testCategoryResponse);

            mockMvc.perform(get("/event-categories/slug/{slug}", "conference"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.slug").value("conference"))
                    .andExpect(jsonPath("$.name").value("Conference"));

            verify(categoryService).getCategoryBySlug("conference");
        }

        @Test
        @DisplayName("Category not found - returns 400")
        void getCategoryBySlug_NotFound() throws Exception {
            setAuthentication(userPrincipal);

            when(categoryService.getCategoryBySlug("nonexistent"))
                    .thenThrow(new ValidationException("Category not found"));

            mockMvc.perform(get("/event-categories/slug/{slug}", "nonexistent"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Category not found"));
        }

        @Test
        @DisplayName("Slug too long - returns 400")
        void getCategoryBySlug_TooLong() throws Exception {
            setAuthentication(userPrincipal);
            String longSlug = "a".repeat(121); // Exceeds max 120

            mockMvc.perform(get("/event-categories/slug/{slug}", longSlug))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(categoryService);
        }
    }

    // ==================== CREATE CATEGORY ====================

    @Nested
    @DisplayName("POST /event-categories - createCategory")
    class CreateCategoryTests {

        @Test
        @DisplayName("Success - admin creates category")
        void createCategory_Success() throws Exception {
            setAuthentication(adminPrincipal);

            CreateCategoryRequest request = CreateCategoryRequest.builder()
                    .name("New Category")
                    .slug("new-category")
                    .description("A new event category")
                    .icon("star")
                    .color("#FF5733")
                    .displayOrder(5)
                    .build();

            CategoryResponse response = CategoryResponse.builder()
                    .id(UUID.randomUUID())
                    .name("New Category")
                    .slug("new-category")
                    .description("A new event category")
                    .icon("star")
                    .color("#FF5733")
                    .isActive(true)
                    .displayOrder(5)
                    .createdAt(LocalDateTime.now())
                    .build();

            when(categoryService.createCategory(any(CreateCategoryRequest.class))).thenReturn(response);

            mockMvc.perform(post("/event-categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("New Category"))
                    .andExpect(jsonPath("$.slug").value("new-category"));

            verify(categoryService).createCategory(any(CreateCategoryRequest.class));
        }

        @Test
        @DisplayName("Slug already exists - returns 400")
        void createCategory_SlugExists() throws Exception {
            setAuthentication(adminPrincipal);

            CreateCategoryRequest request = CreateCategoryRequest.builder()
                    .name("Conference")
                    .slug("conference")
                    .build();

            when(categoryService.createCategory(any(CreateCategoryRequest.class)))
                    .thenThrow(new ValidationException("Category with slug 'conference' already exists"));

            mockMvc.perform(post("/event-categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Category with slug 'conference' already exists"));
        }

        @Test
        @DisplayName("Missing required field - returns 400")
        void createCategory_MissingName() throws Exception {
            setAuthentication(adminPrincipal);

            CreateCategoryRequest request = CreateCategoryRequest.builder()
                    .slug("test-category")
                    // Missing name
                    .build();

            mockMvc.perform(post("/event-categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(categoryService);
        }

        @Test
        @DisplayName("Invalid JSON - returns 400")
        void createCategory_InvalidJson() throws Exception {
            setAuthentication(adminPrincipal);

            mockMvc.perform(post("/event-categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": }"))  // Malformed JSON
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(categoryService);
        }

        @Test
        @DisplayName("Empty body - returns 400")
        void createCategory_EmptyBody() throws Exception {
            setAuthentication(adminPrincipal);

            mockMvc.perform(post("/event-categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(categoryService);
        }
    }

    // ==================== UPDATE CATEGORY ====================

    @Nested
    @DisplayName("PUT /event-categories/{id} - updateCategory")
    class UpdateCategoryTests {

        @Test
        @DisplayName("Success - admin updates category")
        void updateCategory_Success() throws Exception {
            setAuthentication(adminPrincipal);

            UpdateCategoryRequest request = UpdateCategoryRequest.builder()
                    .name("Updated Conference")
                    .description("Updated description")
                    .build();

            CategoryResponse response = CategoryResponse.builder()
                    .id(CATEGORY_ID)
                    .name("Updated Conference")
                    .slug("conference")
                    .description("Updated description")
                    .isActive(true)
                    .build();

            when(categoryService.updateCategory(eq(CATEGORY_ID), any(UpdateCategoryRequest.class)))
                    .thenReturn(response);

            mockMvc.perform(put("/event-categories/{id}", CATEGORY_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Updated Conference"))
                    .andExpect(jsonPath("$.description").value("Updated description"));

            verify(categoryService).updateCategory(eq(CATEGORY_ID), any(UpdateCategoryRequest.class));
        }

        @Test
        @DisplayName("Category not found - returns 400")
        void updateCategory_NotFound() throws Exception {
            setAuthentication(adminPrincipal);

            UpdateCategoryRequest request = UpdateCategoryRequest.builder()
                    .name("Updated Name")
                    .build();

            when(categoryService.updateCategory(eq(CATEGORY_ID), any(UpdateCategoryRequest.class)))
                    .thenThrow(new ValidationException("Category not found"));

            mockMvc.perform(put("/event-categories/{id}", CATEGORY_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Category not found"));
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void updateCategory_InvalidUuid() throws Exception {
            setAuthentication(adminPrincipal);

            UpdateCategoryRequest request = UpdateCategoryRequest.builder()
                    .name("Updated Name")
                    .build();

            mockMvc.perform(put("/event-categories/{id}", "invalid-uuid")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(categoryService);
        }

    }

    // ==================== DELETE CATEGORY ====================

    @Nested
    @DisplayName("DELETE /event-categories/{id} - deleteCategory")
    class DeleteCategoryTests {

        @Test
        @DisplayName("Success - admin deletes category")
        void deleteCategory_Success() throws Exception {
            setAuthentication(adminPrincipal);

            doNothing().when(categoryService).deleteCategory(CATEGORY_ID);

            mockMvc.perform(delete("/event-categories/{id}", CATEGORY_ID))
                    .andExpect(status().isNoContent());

            verify(categoryService).deleteCategory(CATEGORY_ID);
        }

        @Test
        @DisplayName("Category not found - returns 400")
        void deleteCategory_NotFound() throws Exception {
            setAuthentication(adminPrincipal);

            doThrow(new ValidationException("Category not found"))
                    .when(categoryService).deleteCategory(CATEGORY_ID);

            mockMvc.perform(delete("/event-categories/{id}", CATEGORY_ID))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Category not found"));
        }

        @Test
        @DisplayName("Category has events - returns 400")
        void deleteCategory_HasEvents() throws Exception {
            setAuthentication(adminPrincipal);

            doThrow(new ValidationException("Cannot delete category with existing events"))
                    .when(categoryService).deleteCategory(CATEGORY_ID);

            mockMvc.perform(delete("/event-categories/{id}", CATEGORY_ID))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Cannot delete category with existing events"));
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void deleteCategory_InvalidUuid() throws Exception {
            setAuthentication(adminPrincipal);

            mockMvc.perform(delete("/event-categories/{id}", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(categoryService);
        }
    }

    // ==================== TOGGLE ACTIVE ====================

    @Nested
    @DisplayName("PATCH /event-categories/{id}/toggle-active - toggleActive")
    class ToggleActiveTests {

        @Test
        @DisplayName("Success - deactivates active category")
        void toggleActive_Deactivate() throws Exception {
            setAuthentication(adminPrincipal);

            CategoryResponse response = CategoryResponse.builder()
                    .id(CATEGORY_ID)
                    .name("Conference")
                    .slug("conference")
                    .isActive(false)  // Now inactive
                    .build();

            when(categoryService.toggleActive(CATEGORY_ID)).thenReturn(response);

            mockMvc.perform(patch("/event-categories/{id}/toggle-active", CATEGORY_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isActive").value(false));

            verify(categoryService).toggleActive(CATEGORY_ID);
        }

        @Test
        @DisplayName("Success - activates inactive category")
        void toggleActive_Activate() throws Exception {
            setAuthentication(adminPrincipal);

            CategoryResponse response = CategoryResponse.builder()
                    .id(CATEGORY_ID)
                    .name("Conference")
                    .slug("conference")
                    .isActive(true)  // Now active
                    .build();

            when(categoryService.toggleActive(CATEGORY_ID)).thenReturn(response);

            mockMvc.perform(patch("/event-categories/{id}/toggle-active", CATEGORY_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isActive").value(true));
        }

        @Test
        @DisplayName("Category not found - returns 400")
        void toggleActive_NotFound() throws Exception {
            setAuthentication(adminPrincipal);

            when(categoryService.toggleActive(CATEGORY_ID))
                    .thenThrow(new ValidationException("Category not found"));

            mockMvc.perform(patch("/event-categories/{id}/toggle-active", CATEGORY_ID))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Category not found"));
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void toggleActive_InvalidUuid() throws Exception {
            setAuthentication(adminPrincipal);

            mockMvc.perform(patch("/event-categories/{id}/toggle-active", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(categoryService);
        }
    }

    // ==================== UPDATE DISPLAY ORDER ====================

    @Nested
    @DisplayName("PATCH /event-categories/{id}/reorder - updateDisplayOrder")
    class UpdateDisplayOrderTests {

        @Test
        @DisplayName("Success - updates display order")
        void updateDisplayOrder_Success() throws Exception {
            setAuthentication(adminPrincipal);

            CategoryResponse response = CategoryResponse.builder()
                    .id(CATEGORY_ID)
                    .name("Conference")
                    .slug("conference")
                    .displayOrder(10)
                    .isActive(true)
                    .build();

            when(categoryService.updateDisplayOrder(CATEGORY_ID, 10)).thenReturn(response);

            mockMvc.perform(patch("/event-categories/{id}/reorder", CATEGORY_ID)
                            .param("displayOrder", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.displayOrder").value(10));

            verify(categoryService).updateDisplayOrder(CATEGORY_ID, 10);
        }

        @Test
        @DisplayName("Success - display order at minimum (0)")
        void updateDisplayOrder_AtMinimum() throws Exception {
            setAuthentication(adminPrincipal);

            CategoryResponse response = CategoryResponse.builder()
                    .id(CATEGORY_ID)
                    .displayOrder(0)
                    .build();

            when(categoryService.updateDisplayOrder(CATEGORY_ID, 0)).thenReturn(response);

            mockMvc.perform(patch("/event-categories/{id}/reorder", CATEGORY_ID)
                            .param("displayOrder", "0"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.displayOrder").value(0));
        }

        @Test
        @DisplayName("Success - display order at maximum (1000)")
        void updateDisplayOrder_AtMaximum() throws Exception {
            setAuthentication(adminPrincipal);

            CategoryResponse response = CategoryResponse.builder()
                    .id(CATEGORY_ID)
                    .displayOrder(1000)
                    .build();

            when(categoryService.updateDisplayOrder(CATEGORY_ID, 1000)).thenReturn(response);

            mockMvc.perform(patch("/event-categories/{id}/reorder", CATEGORY_ID)
                            .param("displayOrder", "1000"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.displayOrder").value(1000));
        }

        @Test
        @DisplayName("Display order below minimum - returns 400")
        void updateDisplayOrder_BelowMinimum() throws Exception {
            setAuthentication(adminPrincipal);

            mockMvc.perform(patch("/event-categories/{id}/reorder", CATEGORY_ID)
                            .param("displayOrder", "-1"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(categoryService);
        }

        @Test
        @DisplayName("Display order above maximum - returns 400")
        void updateDisplayOrder_AboveMaximum() throws Exception {
            setAuthentication(adminPrincipal);

            mockMvc.perform(patch("/event-categories/{id}/reorder", CATEGORY_ID)
                            .param("displayOrder", "1001"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(categoryService);
        }

        @Test
        @DisplayName("Missing display order parameter - returns 400")
        void updateDisplayOrder_MissingParameter() throws Exception {
            setAuthentication(adminPrincipal);

            mockMvc.perform(patch("/event-categories/{id}/reorder", CATEGORY_ID))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(categoryService);
        }

        @Test
        @DisplayName("Category not found - returns 400")
        void updateDisplayOrder_NotFound() throws Exception {
            setAuthentication(adminPrincipal);

            when(categoryService.updateDisplayOrder(CATEGORY_ID, 5))
                    .thenThrow(new ValidationException("Category not found"));

            mockMvc.perform(patch("/event-categories/{id}/reorder", CATEGORY_ID)
                            .param("displayOrder", "5"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Category not found"));
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void updateDisplayOrder_InvalidUuid() throws Exception {
            setAuthentication(adminPrincipal);

            mockMvc.perform(patch("/event-categories/{id}/reorder", "invalid-uuid")
                            .param("displayOrder", "5"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(categoryService);
        }

        @Test
        @DisplayName("Invalid display order format - returns 400")
        void updateDisplayOrder_InvalidFormat() throws Exception {
            setAuthentication(adminPrincipal);

            mockMvc.perform(patch("/event-categories/{id}/reorder", CATEGORY_ID)
                            .param("displayOrder", "abc"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(categoryService);
        }
    }

    // ==================== HELPER METHOD ====================

    private CategoryResponse buildCategoryResponse(String name, String slug, boolean isActive) {
        return CategoryResponse.builder()
                .id(UUID.randomUUID())
                .name(name)
                .slug(slug)
                .isActive(isActive)
                .displayOrder(1)
                .eventCount(0)
                .createdAt(LocalDateTime.now())
                .build();
    }
}