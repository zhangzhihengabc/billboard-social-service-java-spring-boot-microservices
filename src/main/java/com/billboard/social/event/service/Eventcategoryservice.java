package com.billboard.social.event.service;

import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.common.security.InputValidator;
import com.billboard.social.event.dto.request.EventRequests.CreateCategoryRequest;
import com.billboard.social.event.dto.request.EventRequests.UpdateCategoryRequest;
import com.billboard.social.event.dto.response.EventResponses.CategoryResponse;
import com.billboard.social.event.entity.EventCategory;
import com.billboard.social.event.repository.EventCategoryRepository;
import com.billboard.social.event.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class Eventcategoryservice {

    private final EventCategoryRepository categoryRepository;
    private final EventRepository eventRepository;

    private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");

    // ==================== READ OPERATIONS ====================

    @Transactional(readOnly = true)
    @Cacheable(value = "eventCategories", key = "'active'")
    public List<CategoryResponse> getAllActiveCategories() {
        log.debug("Fetching all active event categories");
        return categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        log.debug("Fetching all event categories (admin)");
        return categoryRepository.findAll()
                .stream()
                .sorted((a, b) -> {
                    int orderCompare = a.getDisplayOrder().compareTo(b.getDisplayOrder());
                    if (orderCompare != 0) return orderCompare;
                    return a.getName().compareTo(b.getName());
                })
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "eventCategories", key = "#id")
    public CategoryResponse getCategoryById(UUID id) {
        if (id == null) {
            throw new ValidationException("Category ID is required");
        }
        EventCategory category = findCategoryById(id);
        return mapToResponse(category);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "eventCategories", key = "'slug:' + #slug")
    public CategoryResponse getCategoryBySlug(String slug) {
        // Validate and sanitize slug input
        if (slug == null || slug.isBlank()) {
            throw new ValidationException("Slug is required");
        }
        String sanitizedSlug = InputValidator.sanitizeSearchQuery(slug);
        if (sanitizedSlug.isBlank()) {
            throw new ValidationException("Invalid slug format");
        }

        EventCategory category = categoryRepository.findBySlug(sanitizedSlug)
                .orElseThrow(() -> new ValidationException("Event category not found with slug: " + sanitizedSlug));
        return mapToResponse(category);
    }

    // ==================== WRITE OPERATIONS (Admin only) ====================

    @Transactional
    @CacheEvict(value = "eventCategories", allEntries = true)
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        if (request == null) {
            throw new ValidationException("Request body is required");
        }

        // Validate input
        String name = InputValidator.validateName(request.getName(), "Category name");

        // Generate slug
        String slug = request.getSlug() != null && !request.getSlug().isBlank()
                ? generateSlug(request.getSlug())
                : generateSlug(name);

        // Check for duplicate slug
        if (categoryRepository.existsBySlug(slug)) {
            throw new ValidationException("Category with slug '" + slug + "' already exists");
        }

        // Validate optional fields
        String description = null;
        if (request.getDescription() != null && !request.getDescription().isBlank()) {
            description = InputValidator.validateText(request.getDescription(), "Description", 500);
        }

        String icon = null;
        if (request.getIcon() != null && !request.getIcon().isBlank()) {
            icon = InputValidator.validateText(request.getIcon(), "Icon", 50);
        }

        String color = null;
        if (request.getColor() != null && !request.getColor().isBlank()) {
            color = validateColor(request.getColor());
        }

        // Get next display order if not provided
        int displayOrder = request.getDisplayOrder() != null
                ? request.getDisplayOrder()
                : getNextDisplayOrder();

        EventCategory category = EventCategory.builder()
                .name(name)
                .slug(slug)
                .description(description)
                .icon(icon)
                .color(color)
                .displayOrder(displayOrder)
                .isActive(true)
                .eventCount(0)
                .build();

        category = categoryRepository.save(category);
        log.info("Created event category: {} ({})", category.getName(), category.getId());

        return mapToResponse(category);
    }

    @Transactional
    @CacheEvict(value = "eventCategories", allEntries = true)
    public CategoryResponse updateCategory(UUID id, UpdateCategoryRequest request) {
        if (request == null) {
            throw new ValidationException("Request body is required");
        }

        EventCategory category = findCategoryById(id);

        // Update name if provided
        if (request.getName() != null && !request.getName().isBlank()) {
            category.setName(InputValidator.validateName(request.getName(), "Category name"));
        }

        // Update slug if provided
        if (request.getSlug() != null && !request.getSlug().isBlank()) {
            String newSlug = generateSlug(request.getSlug());
            if (!newSlug.equals(category.getSlug()) && categoryRepository.existsBySlug(newSlug)) {
                throw new ValidationException("Category with slug '" + newSlug + "' already exists");
            }
            category.setSlug(newSlug);
        }

        // Update description if provided
        if (request.getDescription() != null) {
            category.setDescription(request.getDescription().isBlank()
                    ? null
                    : InputValidator.validateText(request.getDescription(), "Description", 500));
        }

        // Update icon if provided
        if (request.getIcon() != null) {
            category.setIcon(request.getIcon().isBlank()
                    ? null
                    : InputValidator.validateText(request.getIcon(), "Icon", 50));
        }

        // Update color if provided
        if (request.getColor() != null) {
            category.setColor(request.getColor().isBlank()
                    ? null
                    : validateColor(request.getColor()));
        }

        // Update display order if provided
        if (request.getDisplayOrder() != null) {
            category.setDisplayOrder(request.getDisplayOrder());
        }

        // Update active status if provided
        if (request.getIsActive() != null) {
            category.setIsActive(request.getIsActive());
        }

        category = categoryRepository.save(category);
        log.info("Updated event category: {} ({})", category.getName(), category.getId());

        return mapToResponse(category);
    }

    @Transactional
    @CacheEvict(value = "eventCategories", allEntries = true)
    public void deleteCategory(UUID id) {
        if (id == null) {
            throw new ValidationException("Category ID is required");
        }

        EventCategory category = findCategoryById(id);

        // Check if category has events
        if (category.getEventCount() != null && category.getEventCount() > 0) {
            throw new ValidationException("Cannot delete category with existing events. Deactivate it instead.");
        }

        // Check database for any events using this category
        long eventCount = eventRepository.countByCategoryId(id);
        if (eventCount > 0) {
            throw new ValidationException("Cannot delete category with " + eventCount + " existing events. Deactivate it instead.");
        }

        categoryRepository.delete(category);
        log.info("Deleted event category: {} ({})", category.getName(), id);
    }

    @Transactional
    @CacheEvict(value = "eventCategories", allEntries = true)
    public CategoryResponse toggleActive(UUID id) {
        if (id == null) {
            throw new ValidationException("Category ID is required");
        }

        EventCategory category = findCategoryById(id);
        boolean newStatus = !Boolean.TRUE.equals(category.getIsActive());
        category.setIsActive(newStatus);
        category = categoryRepository.save(category);

        log.info("Toggled event category active status: {} ({}) -> {}",
                category.getName(), id, category.getIsActive());

        return mapToResponse(category);
    }

    @Transactional
    @CacheEvict(value = "eventCategories", allEntries = true)
    public CategoryResponse updateDisplayOrder(UUID id, Integer displayOrder) {
        if (id == null) {
            throw new ValidationException("Category ID is required");
        }
        if (displayOrder == null) {
            throw new ValidationException("Display order is required");
        }
        if (displayOrder < 0 || displayOrder > 1000) {
            throw new ValidationException("Display order must be between 0 and 1000");
        }

        EventCategory category = findCategoryById(id);
        category.setDisplayOrder(displayOrder);
        category = categoryRepository.save(category);

        log.info("Updated display order for category {} ({}) to {}",
                category.getName(), id, displayOrder);

        return mapToResponse(category);
    }

    // ==================== HELPER METHODS ====================

    private EventCategory findCategoryById(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ValidationException("Event category not found with id: " + id));
    }

    private String generateSlug(String input) {
        String normalized = Normalizer.normalize(input.toLowerCase(), Normalizer.Form.NFD);
        String slug = WHITESPACE.matcher(normalized).replaceAll("-");
        slug = NONLATIN.matcher(slug).replaceAll("");
        return slug.replaceAll("-+", "-").replaceAll("^-|-$", "");
    }

    private String validateColor(String color) {
        if (color == null || color.isBlank()) {
            return null;
        }
        // Validate hex color format
        if (!color.matches("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$")) {
            throw new ValidationException("Invalid color format. Use hex format like #FF5733");
        }
        return color.toUpperCase();
    }

    private int getNextDisplayOrder() {
        return categoryRepository.findAll()
                .stream()
                .mapToInt(EventCategory::getDisplayOrder)
                .max()
                .orElse(0) + 1;
    }

    private CategoryResponse mapToResponse(EventCategory category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .icon(category.getIcon())
                .color(category.getColor())
                .displayOrder(category.getDisplayOrder())
                .eventCount(category.getEventCount())
                .isActive(category.getIsActive())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }
}
