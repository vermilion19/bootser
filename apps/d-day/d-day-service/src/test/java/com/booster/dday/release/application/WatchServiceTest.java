package com.booster.dday.release.application;

import com.booster.core.web.exception.CoreException;
import com.booster.dday.release.domain.SubjectType;
import com.booster.dday.release.domain.Watch;
import com.booster.dday.release.domain.WatchRepository;
import com.booster.storage.db.config.JpaConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 관심 등록과 <b>펼칠 사람 찾기</b> (D-3 · ARCHITECTURE §3.4).
 *
 * <p>여기서 무는 것 둘.
 *
 * <ol>
 *   <li><b>팀에 걸린 관심을 빠뜨리지 않는가.</b> 한화 팬은 경기 하나를 고른 적이
 *       없다 — 경기 관심만 보면 그 팬에게 아무것도 안 간다</li>
 *   <li><b>한 사람에게 두 번 보내지 않는가.</b> 경기에도 걸고 두 팀 모두에 걸어 둔
 *       사람이 같은 소식을 세 번 받으면 알림을 끄게 된다</li>
 * </ol>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:dday-watch;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({WatchService.class, JpaConfig.class})
class WatchServiceTest {

    private static final long ME = 1L;
    private static final long SOMEONE_ELSE = 2L;
    private static final long EVENT_ID = 100L;
    private static final long HANWHA = 10L;
    private static final long NC = 20L;

    @Autowired
    private WatchService service;

    @Autowired
    private WatchRepository watches;

    @Autowired
    private EntityManager em;

    private void flush() {
        em.flush();
        em.clear();
    }

    @Nested
    @DisplayName("걸고 떼기")
    class Toggling {

        /**
         * «관심 켜기» 는 토글이 아니라 <b>상태 지정</b>이다. 두 번째에 터지면
         * 화면이 «이미 켜져 있다» 를 에러로 다뤄야 한다.
         */
        @Test
        @DisplayName("두 번 걸어도 하나이고 같은 것이 돌아온다")
        void addIsIdempotent() {
            Watch first = service.add(ME, SubjectType.TEAM, HANWHA);
            flush();
            Watch second = service.add(ME, SubjectType.TEAM, HANWHA);
            flush();

            assertThat(second.getId()).isEqualTo(first.getId());
            assertThat(watches.findAll()).hasSize(1);
        }

        @Test
        @DisplayName("다른 사람이 같은 팀에 걸면 그것은 따로다")
        void differentMembersAreSeparate() {
            service.add(ME, SubjectType.TEAM, HANWHA);
            service.add(SOMEONE_ELSE, SubjectType.TEAM, HANWHA);
            flush();

            assertThat(watches.findAll()).hasSize(2);
            assertThat(service.listOf(ME)).hasSize(1);
        }

        /** 403 이 아닌 것은 <b>있다는 사실조차 알려 주지 않기 위해서</b>다 */
        @Test
        @DisplayName("남의 관심을 지우려 하면 404 다")
        void removingOthersIsNotFound() {
            Watch theirs = service.add(SOMEONE_ELSE, SubjectType.TEAM, HANWHA);
            flush();

            assertThatThrownBy(() -> service.remove(ME, theirs.getId()))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining(String.valueOf(theirs.getId()));

            assertThat(watches.findAll()).hasSize(1);
        }

        @Test
        @DisplayName("내 것은 지워진다")
        void removesMine() {
            Watch mine = service.add(ME, SubjectType.TEAM, HANWHA);
            flush();

            service.remove(ME, mine.getId());
            flush();

            assertThat(watches.findAll()).isEmpty();
        }

        /**
         * {@code ck_watch_subject} 가 허용하지 않는다. DB 제약이 막을 것을 도메인이
         * 먼저 막는다 — H2 {@code create-drop} 은 CHECK 를 안 만들므로
         * <b>테스트에서만 통과하는 길</b>이 생긴다 (기념일의 CASCADE 와 같은 함정).
         */
        @Test
        @DisplayName("개봉 회차에는 관심을 걸 수 없다")
        void movieReleaseIsRejected() {
            assertThatThrownBy(() -> service.add(ME, SubjectType.MOVIE_RELEASE, 1L))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("펼칠 사람 (2단 발행)")
    class Receivers {

        @Test
        @DisplayName("경기에 걸린 관심을 찾는다")
        void findsEventWatchers() {
            service.add(ME, SubjectType.SPORT_EVENT, EVENT_ID);
            flush();

            assertThat(service.receiversOf(SubjectType.SPORT_EVENT, EVENT_ID, List.of()))
                    .containsExactly(ME);
        }

        /**
         * <b>이 테스트가 이 파일에서 제일 중요하다.</b> 한화 팬은
         * «2026-09-11 한화 vs NC» 를 등록한 것이 아니라 «한화» 를 등록해 두었다.
         */
        @Test
        @DisplayName("팀에 걸린 관심도 찾는다 — 그 팬은 경기를 고른 적이 없다")
        void findsTeamWatchers() {
            service.add(ME, SubjectType.TEAM, HANWHA);
            flush();

            assertThat(service.receiversOf(SubjectType.SPORT_EVENT, EVENT_ID,
                    List.of(HANWHA, NC))).containsExactly(ME);
        }

        @Test
        @DisplayName("겹치면 한 번만 보낸다")
        void deduplicates() {
            service.add(ME, SubjectType.SPORT_EVENT, EVENT_ID);
            service.add(ME, SubjectType.TEAM, HANWHA);
            service.add(ME, SubjectType.TEAM, NC);
            flush();

            assertThat(service.receiversOf(SubjectType.SPORT_EVENT, EVENT_ID,
                    List.of(HANWHA, NC))).containsExactly(ME);
        }

        @Test
        @DisplayName("두 팀 팬이 다 나온다")
        void findsBothSides() {
            service.add(ME, SubjectType.TEAM, HANWHA);
            service.add(SOMEONE_ELSE, SubjectType.TEAM, NC);
            flush();

            assertThat(service.receiversOf(SubjectType.SPORT_EVENT, EVENT_ID,
                    List.of(HANWHA, NC))).containsExactlyInAnyOrder(ME, SOMEONE_ELSE);
        }

        @Test
        @DisplayName("아무도 안 걸어 두면 빈 목록이다")
        void emptyWhenNobodyWatches() {
            assertThat(service.receiversOf(SubjectType.SPORT_EVENT, EVENT_ID,
                    List.of(HANWHA, NC))).isEmpty();
        }

        @Test
        @DisplayName("팀 목록이 null 이어도 터지지 않는다")
        void tolerateNullTeams() {
            service.add(ME, SubjectType.SPORT_EVENT, EVENT_ID);
            flush();

            assertThat(service.receiversOf(SubjectType.SPORT_EVENT, EVENT_ID, null))
                    .containsExactly(ME);
        }
    }
}
