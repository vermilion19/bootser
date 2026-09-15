package com.booster.dday.release.application;

import com.booster.core.web.exception.CoreException;
import com.booster.dday.release.domain.SubjectType;
import com.booster.dday.release.domain.Watch;
import com.booster.dday.release.domain.WatchRepository;
import com.booster.dday.shared.web.DDayErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 관심 등록 (D-3) 과 <b>펼칠 사람 찾기</b>.
 *
 * <p>두 일이 한 클래스에 있는 까닭은 둘 다 {@code watch} 표 하나만 본다는 것이고,
 * 나누면 <b>같은 표에 대한 규칙이 두 곳에 생긴다.</b>
 */
@Service
@RequiredArgsConstructor
public class WatchService {

    private final WatchRepository watches;

    /**
     * 관심을 건다. <b>이미 걸려 있으면 그것을 돌려준다.</b>
     *
     * <p>{@code uk_watch} 가 있으니 두 번 넣으면 터진다. 터뜨리는 대신 있는 것을
     * 돌려주는 까닭은 «관심 켜기» 가 <b>토글이 아니라 상태 지정</b>이기 때문이다 —
     * 버튼을 두 번 눌러도 결과가 같아야 한다.
     */
    @Transactional
    public Watch add(Long memberId, SubjectType subjectType, Long subjectId) {
        return watches.findByMemberIdAndSubjectTypeAndSubjectId(memberId, subjectType, subjectId)
                .orElseGet(() -> watches.save(Watch.of(memberId, subjectType, subjectId)));
    }

    /**
     * 관심을 뗀다.
     *
     * <p>남의 것을 지우려 하면 404 다 — 403 이 아닌 것은 <b>있다는 사실조차 알려
     * 주지 않기 위해서</b>다. 기념일과 같은 규칙이다.
     */
    @Transactional
    public void remove(Long memberId, Long watchId) {
        Watch watch = watches.findByIdAndMemberId(watchId, memberId)
                .orElseThrow(() -> new CoreException(DDayErrorCode.WATCH_NOT_FOUND,
                        "그 관심이 없다: " + watchId));
        watches.delete(watch);
    }

    @Transactional(readOnly = true)
    public List<Watch> listOf(Long memberId) {
        return watches.findAllByMemberIdOrderByIdDesc(memberId);
    }

    /**
     * 이 변경을 알려야 할 사람들 — <b>2단 발행이 부르는 자리.</b>
     *
     * <h2>경기와 팀을 모두 본다</h2>
     *
     * <p>{@code ck_watch_subject} 가 {@code SPORT_EVENT} 와 {@code TEAM} 을 모두
     * 허용한다. 경기에 걸린 관심만 보면 <b>«한화» 를 등록해 둔 팬에게 아무것도 안
     * 간다</b> — 그 팬은 경기 하나를 고른 적이 없다.
     *
     * <h2>회원 번호로 접는다</h2>
     *
     * <p>경기에도 걸고 두 팀 모두에 걸어 둔 사람이 있을 수 있다. 접지 않으면 같은
     * 소식을 세 번 받는다. {@link LinkedHashSet} 이라 <b>순서도 안정</b>하다 —
     * 순서가 흔들리면 로그를 견주기 어렵고, 재시도 때 무엇이 이미 나갔는지
     * 짐작할 수도 없다.
     *
     * @param relatedTeamIds 이 경기에 나온 팀들. 없으면 빈 목록
     */
    @Transactional(readOnly = true)
    public List<Long> receiversOf(SubjectType subjectType, Long subjectId,
                                  List<Long> relatedTeamIds) {

        Set<Long> memberIds = new LinkedHashSet<>(
                watches.findMemberIdsWatching(subjectType, subjectId));

        if (relatedTeamIds != null) {
            for (Long teamId : relatedTeamIds) {
                memberIds.addAll(watches.findMemberIdsWatching(SubjectType.TEAM, teamId));
            }
        }
        return new ArrayList<>(memberIds);
    }
}
