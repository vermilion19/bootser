package com.booster.dday.release.exception;

/**
 * 물어본 리그가 아닌 것이 왔다 — <b>SPEC §12.3 이 실측한 함정.</b>
 *
 * <p>무료 키에서 {@code lookup_all_teams.php?id=} 는 id 를 무시하고 언제나 잉글랜드
 * 3부 24팀을 준다. <b>HTTP 200 이고 모양도 정상이다.</b> 그 길은 아예 안 쓰지만,
 * 쓰는 길({@code search_all_teams.php?l=})도 같은 식으로 어긋날 수 있다.
 *
 * <p>그래서 받은 것이 우리가 물어본 종목인지 확인하고, 아니면 <b>쓰지 않고 터진다.</b>
 * 조용히 담으면 야구 리그에 축구팀이 들어앉고, 그 뒤로는 무엇이 틀렸는지 찾을
 * 단서가 없다.
 */
public class WrongLeagueDataException extends RuntimeException {

    public WrongLeagueDataException(String message) {
        super(message);
    }
}
