package com.example.backend.shared.infrastructure.web;

import com.example.backend.shared.domain.PageResult;
import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <S, T> PageResponse<T> from(PageResult<S> result, Function<? super S, ? extends T> mapper) {
        PageResult<T> mapped = result.map(mapper);
        return new PageResponse<>(mapped.content(), mapped.page(), mapped.size(), mapped.totalElements(), mapped.totalPages());
    }
}
