package com.booster.dday.release.domain;

import com.booster.common.SnowflakeGenerator;
import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 팀 (D-2).
 *
 * <h2>{@code isStub} — FK 를 살리면서 창 누적을 안 끊는 장치</h2>
 *
 * <p>{@code sport_event → team} 에 FK 가 있다 (SCHEMA §1.4). 원천이 <b>모르는 팀 id</b>
 * 를 실은 경기를 주면 그 회차가 통째로 막히는데, 창 누적은 10분마다 도는 일이라
 * 한 번 막히면 그 경기를 영영 못 담는다.
 *
 * <p>그래서 모르는 팀을 만나면 <b>스텁 행을 먼저 넣는다.</b> FK 는 살고 수집은
 * 안 끊긴다. <b>스텁이 0 이 아니면 그것이 신호다</b> — 팀 목록을 받는 쪽이 놓친 것이
 * 있다는 뜻이고, SPEC §12.3 이 발견한 것과 같은 종류다.
 *
 * <h2>팀 목록을 id 로 받지 않는다</h2>
 *
 * <p>SPEC §12.3 이 실측했다. 무료 키에서 {@code lookup_all_teams.php?id=} 는
 * <b>id 를 무시하고 언제나 잉글랜드 3부 24팀</b>을 돌려준다. 야구 리그를 물었는데
 * 축구팀이 오고 <b>HTTP 200 이고 모양도 정상</b>이다. 이름으로 찾는 길만 쓴다.
 */
@Entity
@Table(
        name = "team",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_team_source", columnNames = {"source", "external_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Team extends BaseEntity {

    @Id
    private Long id;

    @Column(name = "league_id", nullable = false)
    private Long leagueId;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(name = "external_id", nullable = false, length = 40)
    private String externalId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "name_ko", length = 120)
    private String nameKo;

    /** 모르는 팀 id 를 만나 급히 넣은 행. <b>0 이 아니면 신호다</b> */
    @Column(name = "is_stub", nullable = false)
    private boolean stub;

    public static Team of(Long leagueId, String source, String externalId, String name) {
        return create(leagueId, source, externalId, name, false);
    }

    /** 원천이 모르는 팀을 실어 줬다. 이름을 모르니 id 를 이름 자리에 둔다 */
    public static Team stub(Long leagueId, String source, String externalId) {
        return create(leagueId, source, externalId, "(" + externalId + ")", true);
    }

    private static Team create(Long leagueId, String source, String externalId,
                               String name, boolean stub) {
        Team team = new Team();
        team.id = SnowflakeGenerator.nextId();
        team.leagueId = leagueId;
        team.source = source;
        team.externalId = externalId;
        team.name = name;
        team.stub = stub;
        return team;
    }

    /**
     * 이름을 알게 됐다. <b>스텁이었으면 스텁이 아니게 된다.</b>
     *
     * <p>스텁으로 남겨 두면 «신호» 가 영영 켜져 있게 되고, 그러면 아무도 그 신호를
     * 안 본다.
     */
    public void refresh(Long leagueId, String name, String nameKo) {
        this.leagueId = leagueId;
        this.name = name;
        this.nameKo = nameKo;
        this.stub = false;
    }
}
