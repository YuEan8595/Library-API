package com.library.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * A stable, hand-rolled pagination envelope. Serialising Spring's {@code Page}
 * directly leaks internal structure and is unstable across Spring versions.
 */
@Schema(description = "A page of results")
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last) {

    public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }
}
