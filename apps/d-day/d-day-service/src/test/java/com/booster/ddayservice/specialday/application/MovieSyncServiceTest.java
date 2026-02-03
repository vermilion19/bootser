package com.booster.ddayservice.specialday.application;

import com.booster.ddayservice.specialday.domain.CountryCode;
import com.booster.ddayservice.specialday.domain.SpecialDay;
import com.booster.ddayservice.specialday.domain.SpecialDayCategory;
import com.booster.ddayservice.specialday.domain.SpecialDayRepository;
import com.booster.ddayservice.specialday.infrastructure.TmdbClient;
import com.booster.ddayservice.specialday.infrastructure.TmdbMovieDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MovieSyncServiceTest {

    @InjectMocks
    private MovieSyncService movieSyncService;

    @Mock
    private TmdbClient tmdbClient;

    @Mock
    private SpecialDayRepository specialDayRepository;

    @Test
    @DisplayName("TMDB 영화 데이터를 동기화한다")
    void should_syncMovies_when_newMoviesExist() {
        // given
        List<TmdbMovieDto> movies = List.of(
                new TmdbMovieDto(1L, "영화A", "Movie A", "설명A", "2026-07-01", "/poster1.jpg"),
                new TmdbMovieDto(2L, "영화B", "Movie B", "설명B", "2026-08-01", "/poster2.jpg")
        );

        given(tmdbClient.getUpcomingMovies("KR")).willReturn(movies);
        given(specialDayRepository.existsByCountryCodeAndDateAndName(any(), any(), any())).willReturn(false);
        given(specialDayRepository.save(any(SpecialDay.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        MovieSyncService.MovieSyncResult result = movieSyncService.syncUpcomingMovies("KR");

        // then
        assertThat(result.saved()).isEqualTo(2);
        assertThat(result.skipped()).isEqualTo(0);
        assertThat(result.total()).isEqualTo(2);
        verify(specialDayRepository, times(2)).save(any(SpecialDay.class));
    }

    @Test
    @DisplayName("중복 영화는 스킵한다")
    void should_skipDuplicates_when_movieAlreadyExists() {
        // given
        List<TmdbMovieDto> movies = List.of(
                new TmdbMovieDto(1L, "영화A", "Movie A", "설명A", "2026-07-01", "/poster1.jpg"),
                new TmdbMovieDto(2L, "영화B", "Movie B", "설명B", "2026-08-01", "/poster2.jpg")
        );

        given(tmdbClient.getUpcomingMovies("KR")).willReturn(movies);
        given(specialDayRepository.existsByCountryCodeAndDateAndName(
                eq(CountryCode.KR), eq(LocalDate.of(2026, 7, 1)), eq("영화A"))).willReturn(true);
        given(specialDayRepository.existsByCountryCodeAndDateAndName(
                eq(CountryCode.KR), eq(LocalDate.of(2026, 8, 1)), eq("영화B"))).willReturn(false);
        given(specialDayRepository.save(any(SpecialDay.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        MovieSyncService.MovieSyncResult result = movieSyncService.syncUpcomingMovies("KR");

        // then
        assertThat(result.saved()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        verify(specialDayRepository, times(1)).save(any(SpecialDay.class));
    }

    @Test
    @DisplayName("개봉일이 없는 영화는 스킵한다")
    void should_skipMovies_when_releaseDateIsNull() {
        // given
        List<TmdbMovieDto> movies = List.of(
                new TmdbMovieDto(1L, "영화A", "Movie A", "설명A", null, "/poster1.jpg"),
                new TmdbMovieDto(2L, "영화B", "Movie B", "설명B", "", "/poster2.jpg")
        );

        given(tmdbClient.getUpcomingMovies("KR")).willReturn(movies);

        // when
        MovieSyncService.MovieSyncResult result = movieSyncService.syncUpcomingMovies("KR");

        // then
        assertThat(result.saved()).isEqualTo(0);
        assertThat(result.skipped()).isEqualTo(2);
        verify(specialDayRepository, never()).save(any());
    }

    @Test
    @DisplayName("TMDB에서 빈 결과를 반환하면 0건 저장한다")
    void should_returnZero_when_noMoviesFromTmdb() {
        // given
        given(tmdbClient.getUpcomingMovies("KR")).willReturn(List.of());

        // when
        MovieSyncService.MovieSyncResult result = movieSyncService.syncUpcomingMovies("KR");

        // then
        assertThat(result.saved()).isEqualTo(0);
        assertThat(result.total()).isEqualTo(0);
    }
}
