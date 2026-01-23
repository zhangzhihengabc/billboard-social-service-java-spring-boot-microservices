package com.billboard.social.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Paginated response")
public class PageResponse<T> {

    @Schema(description = "List of items in current page")
    private List<T> content;

    @Schema(description = "Current page number (0-indexed)", example = "0")
    private int page;

    @Schema(description = "Page size", example = "20")
    private int size;

    @Schema(description = "Total number of elements", example = "100")
    private long totalElements;

    @Schema(description = "Total number of pages", example = "5")
    private int totalPages;

    @Schema(description = "Whether this is the first page", example = "true")
    private boolean first;

    @Schema(description = "Whether this is the last page", example = "false")
    private boolean last;

    @Schema(description = "Whether the page is empty", example = "false")
    private boolean empty;

    @Schema(description = "Sort information")
    private SortInfo sort;

    /**
     * Sort information object - matches OpenAPI expected schema
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Sort information")
    public static class SortInfo {

        @Schema(description = "Whether sort is empty (no sorting applied)", example = "false")
        private boolean empty;

        @Schema(description = "Whether results are sorted", example = "true")
        private boolean sorted;

        @Schema(description = "Whether results are unsorted", example = "false")
        private boolean unsorted;

        @Schema(description = "Field used for sorting", example = "createdAt")
        private String sortBy;

        @Schema(description = "Sort direction", example = "DESC")
        private String direction;

        /**
         * Create SortInfo from Spring's Sort
         */
        public static SortInfo from(Sort sort) {
            SortInfoBuilder builder = SortInfo.builder()
                    .empty(!sort.isSorted())
                    .sorted(sort.isSorted())
                    .unsorted(!sort.isSorted());

            if (sort.isSorted()) {
                Sort.Order order = sort.iterator().next();
                builder.sortBy(order.getProperty());
                builder.direction(order.getDirection().name());
            }

            return builder.build();
        }
    }

    /**
     * Create PageResponse from Spring's Page
     */
    public static <T> PageResponse<T> from(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .empty(page.isEmpty())
                .sort(SortInfo.from(page.getSort()))
                .build();
    }

    /**
     * Create PageResponse from Spring's Page with mapping function
     */
    public static <T, R> PageResponse<R> from(Page<T> page, Function<T, R> mapper) {
        List<R> content = page.getContent().stream()
                .map(mapper)
                .collect(Collectors.toList());

        return PageResponse.<R>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .empty(page.isEmpty())
                .sort(SortInfo.from(page.getSort()))
                .build();
    }

    /**
     * Creates an empty PageResponse with sort information.
     * Use this when returning empty results for filtered/searched data.
     *
     * @param page      Current page number
     * @param size      Page size
     * @param sortBy    Sort field name
     * @param direction Sort direction (ASC/DESC)
     * @return Empty PageResponse with proper sort info
     */
    public static <T> PageResponse<T> empty(int page, int size, String sortBy, String direction) {
        SortInfo sortInfo = SortInfo.builder()
                .empty(sortBy == null)
                .sorted(sortBy != null)
                .unsorted(sortBy == null)
                .sortBy(sortBy)
                .direction(direction)
                .build();

        return PageResponse.<T>builder()
                .content(List.of())
                .page(page)
                .size(size)
                .totalElements(0L)
                .totalPages(0)
                .first(true)
                .last(true)
                .empty(true)
                .sort(sortInfo)
                .build();
    }

    /**
     * Creates an empty PageResponse without sort information.
     *
     * @param page Current page number
     * @param size Page size
     * @return Empty PageResponse
     */
    public static <T> PageResponse<T> empty(int page, int size) {
        return empty(page, size, null, null);
    }
}