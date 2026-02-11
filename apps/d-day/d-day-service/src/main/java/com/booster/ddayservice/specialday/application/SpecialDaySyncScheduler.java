package com.booster.ddayservice.specialday.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class SpecialDaySyncScheduler {

    private final SpecialDaySyncService specialDaySyncService;
    private final MovieSyncService movieSyncService;
    private final SportsSyncService sportsSyncService;

    @Value("${app.sync.movies-region:KR}")
    private String moviesRegion;

    @Value("${app.sync.sports-days:7}")
    private int sportsDays;

    @Scheduled(cron = "${app.sync.cron.holidays}")
    @SchedulerLock(name = "syncHolidays", lockAtMostFor = "10m", lockAtLeastFor = "5m")
    public void syncHolidays() {
        int year = LocalDate.now().getYear();
        log.info("[Scheduler] 공휴일 자동 동기화 시작: year={}", year);

        try {
            SpecialDaySyncService.SyncAllResult result = specialDaySyncService.syncAll(year);
            log.info("[Scheduler] 공휴일 자동 동기화 완료: 총 저장={}, 성공 국가={}, 실패 국가={}",
                    result.totalSaved(), result.successCount(), result.failedCount());
        } catch (Exception e) {
            log.error("[Scheduler] 공휴일 자동 동기화 실패", e);
        }
    }

    @Scheduled(cron = "${app.sync.cron.movies}")
    @SchedulerLock(name = "syncMovies", lockAtMostFor = "5m", lockAtLeastFor = "2m")
    public void syncMovies() {
        log.info("[Scheduler] 영화 자동 동기화 시작: region={}", moviesRegion);

        try {
            MovieSyncService.MovieSyncResult result = movieSyncService.syncUpcomingMovies(moviesRegion);
            log.info("[Scheduler] 영화 자동 동기화 완료: saved={}, skipped={}, total={}",
                    result.saved(), result.skipped(), result.total());
        } catch (Exception e) {
            log.error("[Scheduler] 영화 자동 동기화 실패", e);
        }
    }

    @Scheduled(cron = "${app.sync.cron.sports}")
    @SchedulerLock(name = "syncSports", lockAtMostFor = "5m", lockAtLeastFor = "2m")
    public void syncSports() {
        log.info("[Scheduler] 스포츠 자동 동기화 시작: days={}", sportsDays);

        try {
            SportsSyncService.SportsSyncResult result = sportsSyncService.syncUpcomingEvents(sportsDays);
            log.info("[Scheduler] 스포츠 자동 동기화 완료: saved={}, skipped={}, total={}",
                    result.saved(), result.skipped(), result.total());
        } catch (Exception e) {
            log.error("[Scheduler] 스포츠 자동 동기화 실패", e);
        }
    }
}
