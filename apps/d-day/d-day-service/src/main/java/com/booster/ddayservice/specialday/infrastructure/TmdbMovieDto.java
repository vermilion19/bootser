package com.booster.ddayservice.specialday.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record TmdbMovieDto(
        long id,
        String title,
        @JsonProperty("original_title") String originalTitle,
        String overview,
        @JsonProperty("release_date") String releaseDate,
        @JsonProperty("poster_path") String posterPath
) {

    public record TmdbResponse(
            int page,
            List<TmdbMovieDto> results,
            @JsonProperty("total_pages") int totalPages,
            @JsonProperty("total_results") int totalResults
    ) {}
}
