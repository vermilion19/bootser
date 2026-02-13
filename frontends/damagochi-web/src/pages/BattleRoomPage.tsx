import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  cancelBattleRoom,
  createBattleRoom,
  getMyBattleRooms,
  joinBattleRoom,
} from "../api/battleApi";
import { getCreatures } from "../api/creatureApi";
import { ApiError } from "../api/http";
import type { CreatureSummary, RoomSummary } from "../api/types";
import { useAuth } from "../store/auth";

export function BattleRoomPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [creatures, setCreatures] = useState<CreatureSummary[]>([]);
  const [rooms, setRooms] = useState<RoomSummary[]>([]);
  const [joinCode, setJoinCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");

  const activeCreature = creatures.find((item) => item.active) ?? null;

  const refresh = async () => {
    if (!user) return;
    const [creatureList, roomList] = await Promise.all([
      getCreatures(user.userId),
      getMyBattleRooms(user.userId),
    ]);
    setCreatures(creatureList);
    setRooms(roomList.rooms);
  };

  useEffect(() => {
    if (!user) return;
    void refresh().catch((error: unknown) => {
      const text =
        error instanceof ApiError
          ? error.payload?.message ?? "방 정보를 불러오지 못했습니다."
          : "방 정보를 불러오지 못했습니다.";
      setMessage(text);
    });
  }, [user]);

  const onCreate = async () => {
    if (!user || !activeCreature) return;
    setBusy(true);
    setMessage("");
    try {
      const room = await createBattleRoom(user.userId, activeCreature.id);
      setMessage(`방 코드: ${room.code}`);
      await refresh();
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

  const onCancel = async (code: string) => {
    if (!user) return;
    setBusy(true);
    try {
      await cancelBattleRoom(user.userId, code);
      await refresh();
    } catch (error) {
      const text =
        error instanceof ApiError
          ? error.payload?.message ?? "방 취소에 실패했습니다."
          : "방 취소에 실패했습니다.";
      setMessage(text);
    } finally {
      setBusy(false);
    }
  };

  const onJoin = async () => {
    if (!user || !activeCreature) return;
    setBusy(true);
    setMessage("");
    try {
      const joined = await joinBattleRoom(user.userId, joinCode.trim().toUpperCase(), activeCreature.id);
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
          <h2>방 기반 배틀</h2>
          <p>코드로 친구와 바로 매칭하거나 내 방을 관리합니다.</p>
        </div>
      </div>

      <div className="panel form-panel">
        <div className="button-row">
          <button className="secondary-btn" onClick={() => void onCreate()} disabled={busy || !activeCreature}>
            방 생성
          </button>
          <button className="ghost-btn" onClick={() => void refresh()} disabled={busy}>
            새로고침
          </button>
        </div>

        <label>
          코드로 참가
          <div className="inline-input">
            <input
              value={joinCode}
              onChange={(e) => setJoinCode(e.target.value)}
              placeholder="예: A12BCD"
            />
            <button className="primary-btn" onClick={() => void onJoin()} disabled={busy || !activeCreature || joinCode.trim().length < 4}>
              참가
            </button>
          </div>
        </label>
      </div>

      <div className="panel">
        <h3>내 오픈 방</h3>
        {rooms.length === 0 && <p>현재 오픈된 방이 없습니다.</p>}
        {rooms.map((room) => (
          <div className="inline-item" key={room.code}>
            <span>
              코드 {room.code} · 남은 시간 {room.remainingSeconds}s
            </span>
            <button className="ghost-btn" onClick={() => void onCancel(room.code)} disabled={busy}>
              취소
            </button>
          </div>
        ))}
      </div>

      {message && <p className="message-line">{message}</p>}
    </section>
  );
}
