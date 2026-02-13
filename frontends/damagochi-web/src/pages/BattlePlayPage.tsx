import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { getBattle } from "../api/battleApi";
import { ApiError } from "../api/http";
import type { BattleDetailView, BattleParticipantView } from "../api/types";
import { BattleCanvas } from "../components/BattleCanvas";
import { useAuth } from "../store/auth";

function parseSnapshot(raw: string): Record<string, number> {
  try {
    return JSON.parse(raw) as Record<string, number>;
  } catch {
    return {};
  }
}

function winnerSide(participants: BattleParticipantView[]): "LEFT" | "RIGHT" | "DRAW" {
  if (participants.length < 2) return "DRAW";
  if (participants[0].outcome === "WIN") return "LEFT";
  if (participants[1].outcome === "WIN") return "RIGHT";
  return "DRAW";
}

export function BattlePlayPage() {
  const { user } = useAuth();
  const params = useParams<{ battleId: string }>();
  const battleId = Number(params.battleId ?? 0);

  const [battle, setBattle] = useState<BattleDetailView | null>(null);
  const [message, setMessage] = useState("배틀 정보를 불러오는 중...");

  useEffect(() => {
    if (!user || Number.isNaN(battleId) || battleId <= 0) {
      setMessage("잘못된 배틀 ID입니다.");
      return;
    }

    const load = async () => {
      try {
        const detail = await getBattle(user.userId, battleId);
        setBattle(detail);
        setMessage("");
      } catch (error) {
        const text =
          error instanceof ApiError
            ? error.payload?.message ?? "배틀 정보를 가져오지 못했습니다."
            : "배틀 정보를 가져오지 못했습니다.";
        setMessage(text);
      }
    };

    void load();
  }, [battleId, user]);

  const participants = battle?.participants ?? [];
  const left = participants[0] ?? null;
  const right = participants[1] ?? null;

  const winner = useMemo(() => winnerSide(participants), [participants]);

  if (!battle || !left || !right) {
    return (
      <section className="panel">
        <h2>배틀 진행</h2>
        <p>{message}</p>
        <Link to="/app/battle" className="ghost-btn">
          로비로 돌아가기
        </Link>
      </section>
    );
  }

  const leftSnapshot = parseSnapshot(left.snapshotJson);
  const rightSnapshot = parseSnapshot(right.snapshotJson);

  return (
    <section className="stack">
      <div className="panel heading-row">
        <div>
          <h2>배틀 #{battle.battleId}</h2>
          <p>
            타입 {battle.type} · 내 결과 {battle.myOutcome}
          </p>
        </div>
        <Link to="/app" className="primary-btn">
          메인으로
        </Link>
      </div>

      <div className="panel battle-stage">
        <BattleCanvas
          leftName={`P1(${left.creatureId})`}
          rightName={`P2(${right.creatureId})`}
          winner={winner}
        />
      </div>

      <div className="card-grid">
        <article className="panel">
          <h3>왼쪽 참가자</h3>
          <p>유저: {left.userId}</p>
          <p>크리처: {left.creatureId}</p>
          <p>결과: {left.outcome}</p>
          <p>스코어: {leftSnapshot.score ?? "-"}</p>
        </article>

        <article className="panel">
          <h3>오른쪽 참가자</h3>
          <p>유저: {right.userId}</p>
          <p>크리처: {right.creatureId}</p>
          <p>결과: {right.outcome}</p>
          <p>스코어: {rightSnapshot.score ?? "-"}</p>
        </article>
      </div>
    </section>
  );
}
