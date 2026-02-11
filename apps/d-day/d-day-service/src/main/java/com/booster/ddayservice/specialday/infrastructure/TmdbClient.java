package com.booster.ddayservice.specialday.infrastructure;

import com.booster.ddayservice.specialday.domain.MovieDataProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class TmdbClient implements MovieDataProvider {

    private static final String BASE_URL = "https://api.themoviedb.org/3";
    private static final int MAX_PAGES = 5;

    private final RestClient restClient;

    public TmdbClient(RestClient.Builder restClientBuilder,
                      @Value("${app.tmdb.api-key}") String apiKey) {
        this.restClient = restClientBuilder
                .baseUrl(BASE_URL)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    @Override
    public List<MovieData> getUpcomingMovies(String region) {
        List<TmdbMovieDto> allMovies = new ArrayList<>();
        int page = 1;

        while (page <= MAX_PAGES) {
            log.info("TMDB upcoming movies 조회: region={}, page={}", region, page);

            int finalPage = page;
            TmdbMovieDto.TmdbResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/movie/upcoming")
                            .queryParam("region", region)
                            .queryParam("language", "ko-KR")
                            .queryParam("page", finalPage)
                            .build())
                    .retrieve()
                    .body(TmdbMovieDto.TmdbResponse.class);

            if (response == null || response.results().isEmpty()) {
                break;
            }

            allMovies.addAll(response.results());

            if (page >= response.totalPages()) {
                break;
            }
            page++;
        }

        log.info("TMDB upcoming movies 총 {}건 조회", allMovies.size());
        return allMovies.stream()
                .map(dto -> new MovieData(dto.title(), dto.overview(), dto.releaseDate()))
                .toList();
    }
}
