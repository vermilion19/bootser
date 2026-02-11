package com.booster.ddayservice.specialday.application;

import com.booster.ddayservice.specialday.domain.*;
import com.booster.ddayservice.specialday.domain.MovieDataProvider.MovieData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MovieSyncService {

    private final MovieDataProvider movieDataProvider;
    private final SpecialDayRepository specialDayRepository;

    @CacheEvict(value = "entertainment", allEntries = true)
    public MovieSyncResult syncUpcomingMovies(String region) {
        log.info("영화 개봉일 동기화 시작: region={}", region);

        CountryCode countryCode;
        try {
            countryCode = CountryCode.valueOf(region.toUpperCase());
        } catch (IllegalArgumentException e) {
            countryCode = CountryCode.KR;
        }

        List<MovieData> movies = movieDataProvider.getUpcomingMovies(region);

        int savedCount = 0;
        int skippedCount = 0;

        for (MovieData movie : movies) {
            if (movie.releaseDate() == null || movie.releaseDate().isBlank()) {
                skippedCount++;
                continue;
            }

            LocalDate releaseDate;
            try {
                releaseDate = LocalDate.parse(movie.releaseDate());
            } catch (Exception e) {
                log.warn("영화 날짜 파싱 실패: title={}, date={}", movie.title(), movie.releaseDate());
                skippedCount++;
                continue;
            }

            if (specialDayRepository.existsByCountryCodeAndDateAndName(countryCode, releaseDate, movie.title())) {
                skippedCount++;
                continue;
            }

            String description = movie.overview() != null && movie.overview().length() > 500
                    ? movie.overview().substring(0, 500)
                    : movie.overview();

            SpecialDay specialDay = SpecialDay.of(
                    movie.title(),
                    SpecialDayCategory.MOVIE,
                    releaseDate,
                    null,
                    countryCode.getDefaultTimezone(),
                    countryCode,
                    description
            );

            specialDayRepository.save(specialDay);
            savedCount++;
        }

        log.info("영화 개봉일 동기화 완료: saved={}, skipped={}, total={}", savedCount, skippedCount, movies.size());
        return new MovieSyncResult(savedCount, skippedCount, movies.size());
    }

    public record MovieSyncResult(int saved, int skipped, int total) {}
}
