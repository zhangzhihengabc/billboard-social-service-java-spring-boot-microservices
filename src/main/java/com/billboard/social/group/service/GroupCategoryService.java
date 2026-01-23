package com.billboard.social.group.service;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.common.security.InputValidator;
import com.billboard.social.group.dto.request.GroupCategoryRequests.*;
import com.billboard.social.group.dto.response.GroupCategoryResponses.*;
import com.billboard.social.group.entity.GroupCategory;
import com.billboard.social.group.repository.GroupCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroupCategoryService {

    private final GroupCategoryRepository categoryRepository;
    private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");

    @Transactional
    public GroupCategoryResponse createCategory(CreateGroupCategoryRequest request) {
        // Validate and sanitize name
        String validatedName = InputValidator.validateName(request.getName(), "Category name");

        if (categoryRepository.existsByName(validatedName)) {
            throw new ValidationException("Category with this name already exists");
        }

        String slug = generateSlug(validatedName);

        Integer maxOrder = categoryRepository.findMaxDisplayOrder();
        int displayOrder = request.getDisplayOrder() != null
                ? request.getDisplayOrder()
                : (maxOrder != null ? maxOrder + 1 : 1);

        if (request.getParentId() != null) {
            categoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new ValidationException("Parent category not found with id: " + request.getParentId()));
        }

        // Validate and sanitize other fields
        String validatedDescription = InputValidator.validateText(request.getDescription(), "Description", 500);
        String validatedIcon = InputValidator.validateIcon(request.getIcon());

        GroupCategory category = GroupCategory.builder()
                .name(validatedName)
                .slug(slug)
                .description(validatedDescription)
                .icon(validatedIcon)
                .parentId(request.getParentId())
                .displayOrder(displayOrder)
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        try {
            category = categoryRepository.save(category);
        } catch (DataIntegrityViolationException e) {
            log.warn("Race condition detected for category creation with name {}: {}", validatedName, e.getMessage());
            throw new ValidationException("Category with this name already exists");
        }

        log.info("Category {} created", category.getId());
        return mapToResponse(category);
    }

    @Transactional
    public GroupCategoryResponse updateCategory(UUID categoryId, UpdateGroupCategoryRequest request) {
        GroupCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ValidationException("Category not found with id: " + categoryId));

        if (request.getName() != null) {
            // Validate and sanitize name
            String validatedName = InputValidator.validateName(request.getName(), "Category name");

            if (!validatedName.equals(category.getName())) {
                if (categoryRepository.existsByName(validatedName)) {
                    throw new ValidationException("Category with this name already exists");
                }
                category.setName(validatedName);
                category.setSlug(generateSlug(validatedName));
            }
        }

        if (request.getDescription() != null) {
            String validatedDescription = InputValidator.validateText(request.getDescription(), "Description", 500);
            category.setDescription(validatedDescription);
        }

        if (request.getIcon() != null) {
            String validatedIcon = InputValidator.validateIcon(request.getIcon());
            category.setIcon(validatedIcon);
        }

        if (request.getParentId() != null) {
            if (request.getParentId().equals(categoryId)) {
                throw new ValidationException("Category cannot be its own parent");
            }
            categoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new ValidationException("Parent category not found with id: " + request.getParentId()));
            category.setParentId(request.getParentId());
        }

        if (request.getDisplayOrder() != null) {
            category.setDisplayOrder(request.getDisplayOrder());
        }

        if (request.getIsActive() != null) {
            category.setIsActive(request.getIsActive());
        }

        try {
            category = categoryRepository.save(category);
        } catch (DataIntegrityViolationException e) {
            log.warn("Slug conflict during category update {}: {}", categoryId, e.getMessage());
            throw new ValidationException("Category with this name already exists");
        }

        log.info("Category {} updated", categoryId);
        return mapToResponse(category);
    }

    @Transactional
    public void deleteCategory(UUID categoryId) {
        GroupCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ValidationException("Category not found with id: " + categoryId));

        if (category.getGroupCount() != null && category.getGroupCount() > 0) {
            throw new ValidationException("Cannot delete category with existing groups. Remove all groups first.");
        }

        long childCount = categoryRepository.countByParentId(categoryId);
        if (childCount > 0) {
            throw new ValidationException("Cannot delete category with subcategories. Remove subcategories first.");
        }

        categoryRepository.delete(category);

        log.info("Category {} deleted", categoryId);
    }

    @Transactional(readOnly = true)
    public GroupCategoryResponse getCategory(UUID categoryId) {
        GroupCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ValidationException("Category not found with id: " + categoryId));

        return mapToResponse(category);
    }

    @Transactional(readOnly = true)
    public GroupCategoryResponse getCategoryBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new ValidationException("Slug is required");
        }

        // Basic sanitization for slug
        String sanitizedSlug = slug.replace("\u0000", "").trim();

        GroupCategory category = categoryRepository.findBySlug(sanitizedSlug)
                .orElseThrow(() -> new ValidationException("Category not found with slug: " + sanitizedSlug));

        return mapToResponse(category);
    }

    @Transactional(readOnly = true)
    public PageResponse<GroupCategoryResponse> getAllCategories(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "displayOrder"));
        Page<GroupCategory> categories = categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc(pageRequest);
        return PageResponse.from(categories, this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public List<GroupCategoryResponse> getAllCategoriesList() {
        return categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PageResponse<GroupCategoryResponse> getRootCategories(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "displayOrder"));
        Page<GroupCategory> categories = categoryRepository.findByParentIdIsNullAndIsActiveTrueOrderByDisplayOrderAsc(pageRequest);
        return PageResponse.from(categories, this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<GroupCategoryResponse> getSubcategories(UUID parentId, int page, int size) {
        categoryRepository.findById(parentId)
                .orElseThrow(() -> new ValidationException("Parent category not found with id: " + parentId));

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "displayOrder"));
        Page<GroupCategory> categories = categoryRepository.findByParentIdAndIsActiveTrueOrderByDisplayOrderAsc(parentId, pageRequest);
        return PageResponse.from(categories, this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<GroupCategoryResponse> searchCategories(String query, int page, int size) {
        // Sanitize search query
        String sanitizedQuery = InputValidator.sanitizeSearchQuery(query);

        if (sanitizedQuery.isEmpty()) {
            return PageResponse.empty(page, size, "displayOrder", "ASC");
        }

        // Get all active categories and filter in memory (safest approach)
        List<GroupCategory> allCategories = categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
        String lowerQuery = sanitizedQuery.toLowerCase();

        List<GroupCategory> filtered = allCategories.stream()
                .filter(c -> (c.getName() != null && c.getName().toLowerCase().contains(lowerQuery)) ||
                        (c.getDescription() != null && c.getDescription().toLowerCase().contains(lowerQuery)))
                .toList();

        // Manual pagination
        int start = page * size;
        int end = Math.min(start + size, filtered.size());

        List<GroupCategory> pageContent = start < filtered.size()
                ? filtered.subList(start, end)
                : List.of();

        int totalPages = filtered.isEmpty() ? 0 : (int) Math.ceil((double) filtered.size() / size);

        return PageResponse.<GroupCategoryResponse>builder()
                .content(pageContent.stream().map(this::mapToResponse).toList())
                .page(page)
                .size(size)
                .totalElements((long) filtered.size())
                .totalPages(totalPages)
                .first(page == 0)
                .last(page >= totalPages - 1 || totalPages == 0)
                .empty(pageContent.isEmpty())
                .sort(PageResponse.SortInfo.builder()
                        .empty(false)
                        .sorted(true)
                        .unsorted(false)
                        .sortBy("displayOrder")
                        .direction("ASC")
                        .build())
                .build();
    }

    private String generateSlug(String input) {
        String noWhitespace = WHITESPACE.matcher(input).replaceAll("-");
        String normalized = Normalizer.normalize(noWhitespace, Normalizer.Form.NFD);
        String slug = NONLATIN.matcher(normalized).replaceAll("");
        slug = slug.toLowerCase(Locale.ENGLISH).replaceAll("-+", "-").replaceAll("^-|-$", "");

        // Handle empty slug (if input was all special characters)
        if (slug.isEmpty()) {
            slug = "category-" + System.currentTimeMillis();
        }

        String baseSlug = slug;
        int counter = 1;
        while (categoryRepository.existsBySlug(slug)) {
            slug = baseSlug + "-" + counter++;
        }
        return slug;
    }

    private GroupCategoryResponse mapToResponse(GroupCategory category) {
        return GroupCategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .icon(category.getIcon())
                .parentId(category.getParentId())
                .displayOrder(category.getDisplayOrder())
                .groupCount(category.getGroupCount())
                .isActive(category.getIsActive())
                .createdAt(category.getCreatedAt())
                .build();
    }
}