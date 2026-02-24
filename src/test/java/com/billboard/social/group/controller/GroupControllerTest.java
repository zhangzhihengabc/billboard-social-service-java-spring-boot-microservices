package com.billboard.social.group.controller;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.exception.GlobalExceptionHandler;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.common.security.UserPrincipal;
import com.billboard.social.group.dto.request.GroupRequests.CreateGroupRequest;
import com.billboard.social.group.dto.request.GroupRequests.UpdateGroupRequest;
import com.billboard.social.group.dto.response.GroupResponses.GroupResponse;
import com.billboard.social.group.dto.response.GroupResponses.GroupSummaryResponse;
import com.billboard.social.group.dto.response.GroupResponses.MembershipResponse;
import com.billboard.social.group.entity.enums.GroupType;
import com.billboard.social.group.entity.enums.MemberRole;
import com.billboard.social.group.entity.enums.MemberStatus;
import com.billboard.social.group.service.GroupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.*;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GroupController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class GroupControllerTest {

    private static final Long USER_ID = 1L;
    private static final UUID GROUP_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CATEGORY_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String BASE_URL = "/api/v1/groups";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GroupService groupService;

    private GroupResponse testGroupResponse;
    private GroupSummaryResponse testGroupSummaryResponse;

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

        testGroupResponse = GroupResponse.builder()
                .id(GROUP_ID)
                .name("Test Group")
                .slug("test-group")
                .description("A test group")
                .coverImageUrl("https://example.com/cover.jpg")
                .iconUrl("https://example.com/icon.jpg")
                .groupType(GroupType.PUBLIC)
                .memberCount(100)
                .postCount(50)
                .ownerId(USER_ID)
                .categoryId(CATEGORY_ID)
                .isFeatured(false)
                .isVerified(false)
                .createdAt(LocalDateTime.now())
                .build();

        testGroupSummaryResponse = GroupSummaryResponse.builder()
                .id(GROUP_ID)
                .name("Test Group")
                .slug("test-group")
                .iconUrl("https://example.com/icon.jpg")
                .groupType(GroupType.PUBLIC)
                .memberCount(100)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ==================== CREATE GROUP ====================

    @Nested
    @DisplayName("POST /groups - createGroup")
    class CreateGroupTests {

        @Test
        @DisplayName("Success - returns 201")
        void createGroup_Success() throws Exception {
            CreateGroupRequest request = CreateGroupRequest.builder()
                    .name("Test Group")
                    .description("A test group")
                    .groupType(GroupType.PUBLIC)
                    .categoryId(CATEGORY_ID)
                    .build();

            when(groupService.createGroup(eq(USER_ID), any(CreateGroupRequest.class)))
                    .thenReturn(testGroupResponse);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(GROUP_ID.toString()))
                    .andExpect(jsonPath("$.name").value("Test Group"))
                    .andExpect(jsonPath("$.slug").value("test-group"))
                    .andExpect(jsonPath("$.groupType").value("PUBLIC"));
        }

        @Test
        @DisplayName("Success - minimal fields")
        void createGroup_MinimalFields() throws Exception {
            CreateGroupRequest request = CreateGroupRequest.builder()
                    .name("Test Group")
                    .build();

            when(groupService.createGroup(eq(USER_ID), any(CreateGroupRequest.class)))
                    .thenReturn(testGroupResponse);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("Missing name - returns 400")
        void createGroup_MissingName() throws Exception {
            CreateGroupRequest request = CreateGroupRequest.builder()
                    .description("A test group")
                    .build();

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(groupService);
        }

        @Test
        @DisplayName("Max group limit reached - returns 400")
        void createGroup_MaxLimitReached() throws Exception {
            CreateGroupRequest request = CreateGroupRequest.builder()
                    .name("Test Group")
                    .build();

            when(groupService.createGroup(eq(USER_ID), any(CreateGroupRequest.class)))
                    .thenThrow(new ValidationException("Maximum group creation limit reached"));

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Maximum group creation limit reached"));
        }

        @Test
        @DisplayName("Category not found - returns 400")
        void createGroup_CategoryNotFound() throws Exception {
            CreateGroupRequest request = CreateGroupRequest.builder()
                    .name("Test Group")
                    .categoryId(CATEGORY_ID)
                    .build();

            when(groupService.createGroup(eq(USER_ID), any(CreateGroupRequest.class)))
                    .thenThrow(new ValidationException("Category not found"));

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Missing request body - returns 400")
        void createGroup_MissingBody() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(groupService);
        }

        @Test
        @DisplayName("Malformed JSON - returns 400")
        void createGroup_MalformedJson() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": }"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(groupService);
        }
    }

    // ==================== GET GROUP ====================

    @Nested
    @DisplayName("GET /groups/{groupId} - getGroup")
    class GetGroupTests {

        @Test
        @DisplayName("Success - authenticated user")
        void getGroup_SuccessAuthenticated() throws Exception {
            when(groupService.getGroup(GROUP_ID, USER_ID)).thenReturn(testGroupResponse);

            mockMvc.perform(get(BASE_URL + "/{groupId}", GROUP_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(GROUP_ID.toString()))
                    .andExpect(jsonPath("$.name").value("Test Group"));

            verify(groupService).getGroup(GROUP_ID, USER_ID);
        }

        @Test
        @DisplayName("Success - principal is null (anonymous user)")
        void getGroup_PrincipalNull() throws Exception {
            SecurityContextHolder.clearContext();

            when(groupService.getGroup(eq(GROUP_ID), isNull())).thenReturn(testGroupResponse);

            mockMvc.perform(get(BASE_URL + "/{groupId}", GROUP_ID))
                    .andExpect(status().isOk());

            verify(groupService).getGroup(eq(GROUP_ID), isNull());
        }

        @Test
        @DisplayName("Group not found - returns 400")
        void getGroup_NotFound() throws Exception {
            when(groupService.getGroup(GROUP_ID, USER_ID))
                    .thenThrow(new ValidationException("Group not found"));

            mockMvc.perform(get(BASE_URL + "/{groupId}", GROUP_ID))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Group not found"));
        }

        @Test
        @DisplayName("Private group access denied - returns 403")
        void getGroup_PrivateGroupAccessDenied() throws Exception {
            when(groupService.getGroup(GROUP_ID, USER_ID))
                    .thenThrow(new AccessDeniedException("Access to private group denied"));

            mockMvc.perform(get(BASE_URL + "/{groupId}", GROUP_ID))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void getGroup_InvalidUuid() throws Exception {
            mockMvc.perform(get(BASE_URL + "/{groupId}", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(groupService);
        }
    }

    // ==================== GET GROUP BY SLUG ====================

    @Nested
    @DisplayName("GET /groups/slug/{slug} - getGroupBySlug")
    class GetGroupBySlugTests {

        @Test
        @DisplayName("Success - authenticated user")
        void getGroupBySlug_SuccessAuthenticated() throws Exception {
            when(groupService.getGroupBySlug("test-group", USER_ID)).thenReturn(testGroupResponse);

            mockMvc.perform(get(BASE_URL + "/slug/{slug}", "test-group"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.slug").value("test-group"));

            verify(groupService).getGroupBySlug("test-group", USER_ID);
        }

        @Test
        @DisplayName("Success - principal is null (anonymous user)")
        void getGroupBySlug_PrincipalNull() throws Exception {
            SecurityContextHolder.clearContext();

            when(groupService.getGroupBySlug(eq("test-group"), isNull())).thenReturn(testGroupResponse);

            mockMvc.perform(get(BASE_URL + "/slug/{slug}", "test-group"))
                    .andExpect(status().isOk());

            verify(groupService).getGroupBySlug(eq("test-group"), isNull());
        }

        @Test
        @DisplayName("Group not found - returns 400")
        void getGroupBySlug_NotFound() throws Exception {
            when(groupService.getGroupBySlug("nonexistent", USER_ID))
                    .thenThrow(new ValidationException("Group not found with slug: nonexistent"));

            mockMvc.perform(get(BASE_URL + "/slug/{slug}", "nonexistent"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Slug too long (>200 chars) - returns 400")
        void getGroupBySlug_SlugTooLong() throws Exception {
            String longSlug = "a".repeat(201);

            mockMvc.perform(get(BASE_URL + "/slug/{slug}", longSlug))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(groupService);
        }

        @Test
        @DisplayName("Slug at max length (200 chars) - returns 200")
        void getGroupBySlug_SlugAtMaxLength() throws Exception {
            String maxSlug = "a".repeat(200);
            when(groupService.getGroupBySlug(maxSlug, USER_ID)).thenReturn(testGroupResponse);

            mockMvc.perform(get(BASE_URL + "/slug/{slug}", maxSlug))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Slug with hyphens - returns 200")
        void getGroupBySlug_WithHyphens() throws Exception {
            when(groupService.getGroupBySlug("my-awesome-group", USER_ID)).thenReturn(testGroupResponse);

            mockMvc.perform(get(BASE_URL + "/slug/{slug}", "my-awesome-group"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Private group access denied - returns 403")
        void getGroupBySlug_PrivateGroupAccessDenied() throws Exception {
            when(groupService.getGroupBySlug("private-group", USER_ID))
                    .thenThrow(new AccessDeniedException("Access to private group denied"));

            mockMvc.perform(get(BASE_URL + "/slug/{slug}", "private-group"))
                    .andExpect(status().isForbidden());
        }
    }

    // ==================== UPDATE GROUP ====================

    @Nested
    @DisplayName("PUT /groups/{groupId} - updateGroup")
    class UpdateGroupTests {

        @Test
        @DisplayName("Success - returns 200")
        void updateGroup_Success() throws Exception {
            UpdateGroupRequest request = UpdateGroupRequest.builder()
                    .name("Updated Group Name")
                    .description("Updated description")
                    .groupType(GroupType.PRIVATE)
                    .build();

            testGroupResponse.setName("Updated Group Name");
            testGroupResponse.setDescription("Updated description");
            testGroupResponse.setGroupType(GroupType.PRIVATE);

            when(groupService.updateGroup(eq(USER_ID), eq(GROUP_ID), any(UpdateGroupRequest.class)))
                    .thenReturn(testGroupResponse);

            mockMvc.perform(put(BASE_URL + "/{groupId}", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Updated Group Name"))
                    .andExpect(jsonPath("$.groupType").value("PRIVATE"));
        }

        @Test
        @DisplayName("Success - empty body (no changes)")
        void updateGroup_EmptyBody() throws Exception {
            UpdateGroupRequest request = UpdateGroupRequest.builder().build();

            when(groupService.updateGroup(eq(USER_ID), eq(GROUP_ID), any(UpdateGroupRequest.class)))
                    .thenReturn(testGroupResponse);

            mockMvc.perform(put(BASE_URL + "/{groupId}", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Group not found - returns 400")
        void updateGroup_NotFound() throws Exception {
            UpdateGroupRequest request = UpdateGroupRequest.builder()
                    .name("Updated Name")
                    .build();

            when(groupService.updateGroup(eq(USER_ID), eq(GROUP_ID), any(UpdateGroupRequest.class)))
                    .thenThrow(new ValidationException("Group not found"));

            mockMvc.perform(put(BASE_URL + "/{groupId}", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Not admin - returns 403")
        void updateGroup_NotAdmin() throws Exception {
            UpdateGroupRequest request = UpdateGroupRequest.builder()
                    .name("Updated Name")
                    .build();

            when(groupService.updateGroup(eq(USER_ID), eq(GROUP_ID), any(UpdateGroupRequest.class)))
                    .thenThrow(new AccessDeniedException("Admin access required"));

            mockMvc.perform(put(BASE_URL + "/{groupId}", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void updateGroup_InvalidUuid() throws Exception {
            UpdateGroupRequest request = UpdateGroupRequest.builder()
                    .name("Updated Name")
                    .build();

            mockMvc.perform(put(BASE_URL + "/{groupId}", "invalid-uuid")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(groupService);
        }

        @Test
        @DisplayName("Missing request body - returns 400")
        void updateGroup_MissingBody() throws Exception {
            mockMvc.perform(put(BASE_URL + "/{groupId}", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(groupService);
        }
    }

    // ==================== DELETE GROUP ====================

    @Nested
    @DisplayName("DELETE /groups/{groupId} - deleteGroup")
    class DeleteGroupTests {

        @Test
        @DisplayName("Success - returns 204")
        void deleteGroup_Success() throws Exception {
            doNothing().when(groupService).deleteGroup(USER_ID, GROUP_ID);

            mockMvc.perform(delete(BASE_URL + "/{groupId}", GROUP_ID))
                    .andExpect(status().isNoContent());

            verify(groupService).deleteGroup(USER_ID, GROUP_ID);
        }

        @Test
        @DisplayName("Group not found - returns 400")
        void deleteGroup_NotFound() throws Exception {
            doThrow(new ValidationException("Group not found"))
                    .when(groupService).deleteGroup(USER_ID, GROUP_ID);

            mockMvc.perform(delete(BASE_URL + "/{groupId}", GROUP_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Not owner - returns 403")
        void deleteGroup_NotOwner() throws Exception {
            doThrow(new AccessDeniedException("Only owner can delete the group"))
                    .when(groupService).deleteGroup(USER_ID, GROUP_ID);

            mockMvc.perform(delete(BASE_URL + "/{groupId}", GROUP_ID))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void deleteGroup_InvalidUuid() throws Exception {
            mockMvc.perform(delete(BASE_URL + "/{groupId}", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(groupService);
        }
    }

    // ==================== SEARCH GROUPS ====================

    @Nested
    @DisplayName("GET /groups/search - searchGroups")
    class SearchGroupsTests {

        @Test
        @DisplayName("Success - returns matching groups")
        void searchGroups_Success() throws Exception {
            PageResponse<GroupSummaryResponse> pageResponse = PageResponse.<GroupSummaryResponse>builder()
                    .content(List.of(testGroupSummaryResponse))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .build();

            when(groupService.searchGroups("music", 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get(BASE_URL + "/search")
                            .param("query", "music"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)));
        }

        @Test
        @DisplayName("Success - empty results")
        void searchGroups_Empty() throws Exception {
            PageResponse<GroupSummaryResponse> pageResponse = PageResponse.<GroupSummaryResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(groupService.searchGroups("xyz", 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get(BASE_URL + "/search")
                            .param("query", "xyz"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        @DisplayName("Custom pagination")
        void searchGroups_CustomPagination() throws Exception {
            PageResponse<GroupSummaryResponse> pageResponse = PageResponse.<GroupSummaryResponse>builder()
                    .content(Collections.emptyList())
                    .page(5)
                    .size(50)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(groupService.searchGroups("music", 5, 50)).thenReturn(pageResponse);

            mockMvc.perform(get(BASE_URL + "/search")
                            .param("query", "music")
                            .param("page", "5")
                            .param("size", "50"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(5))
                    .andExpect(jsonPath("$.size").value(50));
        }

        @Test
        @DisplayName("Missing query - returns 400")
        void searchGroups_MissingQuery() throws Exception {
            mockMvc.perform(get(BASE_URL + "/search"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(groupService);
        }

        @Test
        @DisplayName("Query too long (>100 chars) - returns 400")
        void searchGroups_QueryTooLong() throws Exception {
            String longQuery = "a".repeat(101);

            mockMvc.perform(get(BASE_URL + "/search")
                            .param("query", longQuery))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(groupService);
        }

        @Test
        @DisplayName("Query at max length (100 chars) - returns 200")
        void searchGroups_QueryAtMaxLength() throws Exception {
            String maxQuery = "a".repeat(100);
            PageResponse<GroupSummaryResponse> pageResponse = PageResponse.<GroupSummaryResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(groupService.searchGroups(maxQuery, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get(BASE_URL + "/search")
                            .param("query", maxQuery))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Single character query - returns 200")
        void searchGroups_SingleCharQuery() throws Exception {
            PageResponse<GroupSummaryResponse> pageResponse = PageResponse.<GroupSummaryResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(groupService.searchGroups("m", 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get(BASE_URL + "/search")
                            .param("query", "m"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Page above maximum - returns 400")
        void searchGroups_PageAboveMax() throws Exception {
            mockMvc.perform(get(BASE_URL + "/search")
                            .param("query", "music")
                            .param("page", "1001"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(groupService);
        }

        @Test
        @DisplayName("Size below minimum - returns 400")
        void searchGroups_SizeBelowMin() throws Exception {
            mockMvc.perform(get(BASE_URL + "/search")
                            .param("query", "music")
                            .param("size", "0"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(groupService);
        }
    }

    // ==================== GET POPULAR GROUPS ====================

    @Nested
    @DisplayName("GET /groups/popular - getPopularGroups")
    class GetPopularGroupsTests {

        @Test
        @DisplayName("Success - returns popular groups")
        void getPopularGroups_Success() throws Exception {
            PageResponse<GroupSummaryResponse> pageResponse = PageResponse.<GroupSummaryResponse>builder()
                    .content(List.of(testGroupSummaryResponse))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .build();

            when(groupService.getPopularGroups(0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get(BASE_URL + "/popular"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)));
        }

        @Test
        @DisplayName("Success - empty list")
        void getPopularGroups_Empty() throws Exception {
            PageResponse<GroupSummaryResponse> pageResponse = PageResponse.<GroupSummaryResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(groupService.getPopularGroups(0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get(BASE_URL + "/popular"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        @DisplayName("Custom pagination")
        void getPopularGroups_CustomPagination() throws Exception {
            PageResponse<GroupSummaryResponse> pageResponse = PageResponse.<GroupSummaryResponse>builder()
                    .content(Collections.emptyList())
                    .page(3)
                    .size(50)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(groupService.getPopularGroups(3, 50)).thenReturn(pageResponse);

            mockMvc.perform(get(BASE_URL + "/popular")
                            .param("page", "3")
                            .param("size", "50"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Page below minimum - returns 400")
        void getPopularGroups_PageBelowMin() throws Exception {
            mockMvc.perform(get(BASE_URL + "/popular")
                            .param("page", "-1"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(groupService);
        }

        @Test
        @DisplayName("Size above maximum - returns 400")
        void getPopularGroups_SizeAboveMax() throws Exception {
            mockMvc.perform(get(BASE_URL + "/popular")
                            .param("size", "101"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(groupService);
        }

        @Test
        @DisplayName("Boundary - page at max (1000)")
        void getPopularGroups_PageAtMax() throws Exception {
            PageResponse<GroupSummaryResponse> pageResponse = PageResponse.<GroupSummaryResponse>builder()
                    .content(Collections.emptyList())
                    .page(1000)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(groupService.getPopularGroups(1000, 20)).thenReturn(pageResponse);

            mockMvc.perform(get(BASE_URL + "/popular")
                            .param("page", "1000"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Boundary - size at min (1)")
        void getPopularGroups_SizeAtMin() throws Exception {
            PageResponse<GroupSummaryResponse> pageResponse = PageResponse.<GroupSummaryResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(1)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(groupService.getPopularGroups(0, 1)).thenReturn(pageResponse);

            mockMvc.perform(get(BASE_URL + "/popular")
                            .param("size", "1"))
                    .andExpect(status().isOk());
        }
    }

    // ==================== GET FEATURED GROUPS ====================

    @Nested
    @DisplayName("GET /groups/featured - getFeaturedGroups")
    class GetFeaturedGroupsTests {

        @Test
        @DisplayName("Success - returns featured groups")
        void getFeaturedGroups_Success() throws Exception {
            PageResponse<GroupSummaryResponse> pageResponse = PageResponse.<GroupSummaryResponse>builder()
                    .content(List.of(testGroupSummaryResponse))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .build();

            when(groupService.getFeaturedGroups(0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get(BASE_URL + "/featured"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)));
        }

        @Test
        @DisplayName("Success - empty list")
        void getFeaturedGroups_Empty() throws Exception {
            PageResponse<GroupSummaryResponse> pageResponse = PageResponse.<GroupSummaryResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(groupService.getFeaturedGroups(0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get(BASE_URL + "/featured"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        @DisplayName("Custom pagination")
        void getFeaturedGroups_CustomPagination() throws Exception {
            PageResponse<GroupSummaryResponse> pageResponse = PageResponse.<GroupSummaryResponse>builder()
                    .content(Collections.emptyList())
                    .page(2)
                    .size(10)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(groupService.getFeaturedGroups(2, 10)).thenReturn(pageResponse);

            mockMvc.perform(get(BASE_URL + "/featured")
                            .param("page", "2")
                            .param("size", "10"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Page above maximum - returns 400")
        void getFeaturedGroups_PageAboveMax() throws Exception {
            mockMvc.perform(get(BASE_URL + "/featured")
                            .param("page", "1001"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(groupService);
        }

        @Test
        @DisplayName("Size below minimum - returns 400")
        void getFeaturedGroups_SizeBelowMin() throws Exception {
            mockMvc.perform(get(BASE_URL + "/featured")
                            .param("size", "0"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(groupService);
        }
    }

    // ==================== GET GROUPS BY CATEGORY ====================

    @Nested
    @DisplayName("GET /groups/category/{categoryId} - getGroupsByCategory")
    class GetGroupsByCategoryTests {

        @Test
        @DisplayName("Success - returns groups")
        void getGroupsByCategory_Success() throws Exception {
            PageResponse<GroupSummaryResponse> pageResponse = PageResponse.<GroupSummaryResponse>builder()
                    .content(List.of(testGroupSummaryResponse))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .build();

            when(groupService.getGroupsByCategory(CATEGORY_ID, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get(BASE_URL + "/category/{categoryId}", CATEGORY_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)));
        }

        @Test
        @DisplayName("Success - empty list")
        void getGroupsByCategory_Empty() throws Exception {
            PageResponse<GroupSummaryResponse> pageResponse = PageResponse.<GroupSummaryResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(groupService.getGroupsByCategory(CATEGORY_ID, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get(BASE_URL + "/category/{categoryId}", CATEGORY_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        @DisplayName("Custom pagination")
        void getGroupsByCategory_CustomPagination() throws Exception {
            PageResponse<GroupSummaryResponse> pageResponse = PageResponse.<GroupSummaryResponse>builder()
                    .content(Collections.emptyList())
                    .page(3)
                    .size(25)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(groupService.getGroupsByCategory(CATEGORY_ID, 3, 25)).thenReturn(pageResponse);

            mockMvc.perform(get(BASE_URL + "/category/{categoryId}", CATEGORY_ID)
                            .param("page", "3")
                            .param("size", "25"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Category not found - returns 400")
        void getGroupsByCategory_CategoryNotFound() throws Exception {
            when(groupService.getGroupsByCategory(CATEGORY_ID, 0, 20))
                    .thenThrow(new ValidationException("Category not found"));

            mockMvc.perform(get(BASE_URL + "/category/{categoryId}", CATEGORY_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void getGroupsByCategory_InvalidUuid() throws Exception {
            mockMvc.perform(get(BASE_URL + "/category/{categoryId}", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(groupService);
        }

        @Test
        @DisplayName("Page above maximum - returns 400")
        void getGroupsByCategory_PageAboveMax() throws Exception {
            mockMvc.perform(get(BASE_URL + "/category/{categoryId}", CATEGORY_ID)
                            .param("page", "1001"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(groupService);
        }

        @Test
        @DisplayName("Size above maximum - returns 400")
        void getGroupsByCategory_SizeAboveMax() throws Exception {
            mockMvc.perform(get(BASE_URL + "/category/{categoryId}", CATEGORY_ID)
                            .param("size", "101"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(groupService);
        }
    }

    // ==================== GET MY GROUPS ====================

    @Nested
    @DisplayName("GET /groups/my - getMyGroups")
    class GetMyGroupsTests {

        @Test
        @DisplayName("Success - returns user's groups")
        void getMyGroups_Success() throws Exception {
            MembershipResponse membershipResponse = MembershipResponse.builder()
                    .groupId(GROUP_ID)
                    .groupName("Test Group")
                    .groupSlug("test-group")
                    .groupIconUrl("https://example.com/icon.jpg")
                    .groupType(GroupType.PUBLIC)
                    .role(MemberRole.MEMBER)
                    .status(MemberStatus.APPROVED)
                    .joinedAt(LocalDateTime.now())
                    .notificationsEnabled(true)
                    .build();

            PageResponse<MembershipResponse> pageResponse = PageResponse.<MembershipResponse>builder()
                    .content(List.of(membershipResponse))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .build();

            when(groupService.getUserGroups(USER_ID, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get(BASE_URL + "/my"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].groupId").value(GROUP_ID.toString()));
        }

        @Test
        @DisplayName("Success - empty list (no memberships)")
        void getMyGroups_Empty() throws Exception {
            PageResponse<MembershipResponse> pageResponse = PageResponse.<MembershipResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(groupService.getUserGroups(USER_ID, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get(BASE_URL + "/my"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        @DisplayName("Custom pagination")
        void getMyGroups_CustomPagination() throws Exception {
            PageResponse<MembershipResponse> pageResponse = PageResponse.<MembershipResponse>builder()
                    .content(Collections.emptyList())
                    .page(5)
                    .size(10)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(groupService.getUserGroups(USER_ID, 5, 10)).thenReturn(pageResponse);

            mockMvc.perform(get(BASE_URL + "/my")
                            .param("page", "5")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(5))
                    .andExpect(jsonPath("$.size").value(10));
        }

        @Test
        @DisplayName("Page below minimum - returns 400")
        void getMyGroups_PageBelowMin() throws Exception {
            mockMvc.perform(get(BASE_URL + "/my")
                            .param("page", "-1"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(groupService);
        }

        @Test
        @DisplayName("Page above maximum - returns 400")
        void getMyGroups_PageAboveMax() throws Exception {
            mockMvc.perform(get(BASE_URL + "/my")
                            .param("page", "1001"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(groupService);
        }

        @Test
        @DisplayName("Size below minimum - returns 400")
        void getMyGroups_SizeBelowMin() throws Exception {
            mockMvc.perform(get(BASE_URL + "/my")
                            .param("size", "0"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(groupService);
        }

        @Test
        @DisplayName("Size above maximum - returns 400")
        void getMyGroups_SizeAboveMax() throws Exception {
            mockMvc.perform(get(BASE_URL + "/my")
                            .param("size", "101"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(groupService);
        }

        @Test
        @DisplayName("Boundary - page at max (1000)")
        void getMyGroups_PageAtMax() throws Exception {
            PageResponse<MembershipResponse> pageResponse = PageResponse.<MembershipResponse>builder()
                    .content(Collections.emptyList())
                    .page(1000)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(groupService.getUserGroups(USER_ID, 1000, 20)).thenReturn(pageResponse);

            mockMvc.perform(get(BASE_URL + "/my")
                            .param("page", "1000"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Boundary - size at max (100)")
        void getMyGroups_SizeAtMax() throws Exception {
            PageResponse<MembershipResponse> pageResponse = PageResponse.<MembershipResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(100)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(groupService.getUserGroups(USER_ID, 0, 100)).thenReturn(pageResponse);

            mockMvc.perform(get(BASE_URL + "/my")
                            .param("size", "100"))
                    .andExpect(status().isOk());
        }
    }
}