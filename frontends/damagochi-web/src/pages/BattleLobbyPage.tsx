import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import {
  createBattleRoom,
  queueRandomBattle,
  getMyBattleRooms,
  joinBattleRoom,
} from "../api/battleApi";
import { getCreatures } from "../api/creatureApi";
import { ApiError } from "../api/http";
import type { CreatureSummary, RoomSummary } from "../api/types";
import { useAuth } from "../store/auth";

export function BattleLobbyPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [creatures, setCreatures] = useState<CreatureSummary[]>([]);
  const [selectedCreatureId, setSelectedCreatureId] = useState<number | null>(null);
  const [rooms, setRooms] = useState<RoomSummary[]>([]);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");

  const activeCreature = useMemo(
    () => creatures.find((item) => item.active) ?? null,
    [creatures]
  );

  useEffect(() => {
    if (!user) return;

    const bootstrap = async () => {
      try {
        const [list, myRooms] = await Promise.all([
          getCreatures(user.userId),
          getMyBattleRooms(user.userId),
        ]);
        setCreatures(list);
        setRooms(myRooms.rooms);

        if (list.some((item) => item.active)) {
          setSelectedCreatureId(list.find((item) => item.active)?.id ?? null);
        }
      } catch (error) {
        const text =
          error instanceof ApiError
            ? error.payload?.message ?? "배틀 정보를 불러오지 못했습니다."
            : "배틀 정보를 불러오지 못했습니다.";
        setMessage(text);
      }
    };

    void bootstrap();
  }, [user]);

  const onQueueRandom = async () => {
    if (!user || !selectedCreatureId) return;
    setBusy(true);
    setMessage("");
    try {
      const result = await queueRandomBattle(user.userId, selectedCreatureId);
      if (result.status === "MATCHED" && result.battleId != null) {
        navigate(`/app/battle/${result.battleId}`);
      } else {
        setMessage(`랜덤 매칭 대기 중입니다. 티켓 #${result.ticket}`);
      }
    } catch (error) {
      const text =
        error instanceof ApiError
          ? error.payload?.message ?? "랜덤 매칭 요청에 실패했습니다."
          : "랜덤 매칭 요청에 실패했습니다.";
      setMessage(text);
    } finally {
      setBusy(false);
    }
  };

  const onCreateRoom = async () => {
    if (!user || !selectedCreatureId) return;
    setBusy(true);
    setMessage("");
    try {
      const room = await createBattleRoom(user.userId, selectedCreatureId);
      setMessage(`방 생성 완료: ${room.code}`);
      navigate("/app/battle/room");
    } catch (error) {
      const text =
        error instanceof ApiError
          ? error.payload?.message ?? "방 생성에 실패했습니다."
          : "방 생성에 실패했습니다.";
      setMessage(text);
    } finally {
      setBusy(false);
    }
  };

  const onQuickJoin = async (code: string) => {
    if (!user || !selectedCreatureId) return;
    setBusy(true);
    setMessage("");
    try {
      const joined = await joinBattleRoom(user.userId, code, selectedCreatureId);
      if (joined.battleId != null) {
        navigate(`/app/battle/${joined.battleId}`);
      }
    } catch (error) {
      const text =
        error instanceof ApiError
          ? error.payload?.message ?? "방 참가에 실패했습니다."
          : "방 참가에 실패했습니다.";
      setMessage(text);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="stack">
      <div className="panel heading-row">
        <div>
          <h2>배틀 입장</h2>
          <p>랜덤 매칭 또는 방 코드를 통한 배틀을 시작합니다.</p>
        </div>
        <Link to="/app/battle/room" className="ghost-btn">
          방 상세 관리
        </Link>
      </div>

      <div className="panel form-panel">
        <label>
          참가할 크리처
          <select
            value={selectedCreatureId ?? ""}
            onChange={(e) => setSelectedCreatureId(Number(e.target.value))}
            disabled={busy || !activeCreature}
          >
            {activeCreature ? (
              <option value={activeCreature.id}>{activeCreature.name} (Active)</option>
            ) : (
              <option value="">Active 크리처가 필요합니다</option>
            )}
          </select>
        </label>

        <div className="button-row">
          <button className="primary-btn" disabled={busy || !selectedCreatureId} onClick={() => void onQueueRandom()}>
            랜덤 배틀 시작
          </button>
          <button className="secondary-btn" disabled={busy || !selectedCreatureId} onClick={() => void onCreateRoom()}>
            내 방 만들기
          </button>
        </div>
      </div>

      <div className="panel">
        <h3>현재 내 오픈 방</h3>
        {rooms.length === 0 && <p>열린 방이 없습니다.</p>}
        {rooms.map((room) => (
          <div className="inline-item" key={room.code}>
            <span>
              {room.code} · 남은 시간 {room.remainingSeconds}s
            </span>
            <button className="ghost-btn" onClick={() => void onQuickJoin(room.code)} disabled={busy || !selectedCreatureId}>
              이 코드로 참가 테스트
            </button>
          </div>
        ))}
      </div>

      {message && <p className="message-line">{message}</p>}
    </section>
  );
}
