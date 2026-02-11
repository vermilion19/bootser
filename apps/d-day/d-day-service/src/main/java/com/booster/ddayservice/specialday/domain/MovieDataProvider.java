package com.booster.ddayservice.specialday.domain;

import java.util.List;

public interface MovieDataProvider {

    record MovieData(String title, String overview, String releaseDate) {}

    List<MovieData> getUpcomingMovies(String region);
}
