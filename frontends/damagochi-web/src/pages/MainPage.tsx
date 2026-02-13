import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { ApiError } from "../api/http";
import { feedCreature, getCreatures, sleepCreature, treatCreature } from "../api/creatureApi";
import type { CreatureSummary } from "../api/types";
import { CreatureCanvas } from "../components/CreatureCanvas";
import { useAuth } from "../store/auth";

const ALERTS = {
  health: "체력이 낮습니다. 치료를 먼저 해주세요.",
  hunger: "배고픔 수치가 낮습니다. 먹이를 주세요.",
  condition: "컨디션이 낮습니다. 치료 또는 휴식이 필요합니다.",
};

export function MainPage() {
  const { user } = useAuth();
  const [creatures, setCreatures] = useState<CreatureSummary[]>([]);
  const [busyAction, setBusyAction] = useState<string | null>(null);
  const [message, setMessage] = useState<string>("");
  const [actionHint, setActionHint] = useState<"idle" | "feed" | "treat" | "sleep" | "battle">("idle");

  const activeCreature = useMemo(
    () => creatures.find((c) => c.active) ?? null,
    [creatures]
  );

  const refresh = async () => {
    if (!user) return;
    try {
      setCreatures(await getCreatures(user.userId));
    } catch (error) {
      const text =
        error instanceof ApiError
          ? error.payload?.message ?? "크리처 정보를 불러오지 못했습니다."
          : "크리처 정보를 불러오지 못했습니다.";
      setMessage(text);
    }
  };

  useEffect(() => {
    void refresh();
    const id = window.setInterval(() => void refresh(), 15000);
    return () => window.clearInterval(id);
  }, [user]);

  useEffect(() => {
    if (!activeCreature) {
      setActionHint("idle");
      return;
    }
    const timeout = window.setTimeout(() => setActionHint("idle"), 1100);
    return () => window.clearTimeout(timeout);
  }, [actionHint, activeCreature]);

  const runAction = async (kind: "feed" | "treat" | "sleep") => {
    if (!user || !activeCreature) return;
    setBusyAction(kind);
    setMessage("");

    try {
      let updated: CreatureSummary;
      if (kind === "feed") {
        updated = await feedCreature(user.userId, activeCreature.id);
        setActionHint("feed");
        setMessage("먹이를 주었습니다.");
      } else if (kind === "treat") {
        updated = await treatCreature(user.userId, activeCreature.id);
        setActionHint("treat");
        setMessage("치료를 완료했습니다.");
      } else {
        updated = await sleepCreature(user.userId, activeCreature.id, {
          sleeping: !activeCreature.state.sleeping,
        });
        setActionHint("sleep");
        setMessage(updated.state.sleeping ? "크리처가 잠들었습니다." : "크리처가 깨어났습니다.");
      }

      setCreatures((prev) => prev.map((item) => (item.id === updated.id ? updated : item)));
    } catch (error) {
      const text =
        error instanceof ApiError
          ? error.payload?.message ?? "행동 처리 중 오류가 발생했습니다."
          : "행동 처리 중 오류가 발생했습니다.";
      setMessage(text);
    } finally {
      setBusyAction(null);
    }
  };

  if (!activeCreature) {
    return (
      <section className="panel">
        <h2>활성 크리처가 없습니다</h2>
        <p>먼저 크리처를 생성하거나 활성화해주세요.</p>
        <div className="button-row">
          <Link to="/app/creatures/new" className="primary-btn">
            크리처 생성
          </Link>
          <Link to="/app/creatures" className="ghost-btn">
            크리처 관리
          </Link>
        </div>
      </section>
    );
  }

  const { health, hunger, conditionScore, winRate, sleeping } = activeCreature.state;
  const alerts: string[] = [];
  if (health < 35) alerts.push(ALERTS.health);
  if (hunger < 35) alerts.push(ALERTS.hunger);
  if (conditionScore < 35) alerts.push(ALERTS.condition);

  return (
    <section className="stack">
      <div className="panel hud-panel">
        <div className="hud-item">
          <strong>크리처</strong>
          <span>{activeCreature.name}</span>
        </div>
        <div className="hud-item">
          <strong>종</strong>
          <span>{activeCreature.species}</span>
        </div>
        <div className="hud-item">
          <strong>승률</strong>
          <span>{winRate}%</span>
        </div>
      </div>

      <div className="panel main-panel">
        <CreatureCanvas
          state={activeCreature.state}
          species={activeCreature.species}
          actionHint={actionHint}
        />

        <div className="meter-grid">
          <Meter label="체력" value={health} />
          <Meter label="배고픔" value={hunger} />
          <Meter label="컨디션" value={conditionScore} />
        </div>

        <div className="button-row">
          <button
            className="primary-btn"
            onClick={() => void runAction("feed")}
            disabled={busyAction !== null}
          >
            {busyAction === "feed" ? "먹이 주는 중..." : "먹이"}
          </button>
          <button
            className="secondary-btn"
            onClick={() => void runAction("treat")}
            disabled={busyAction !== null}
          >
            {busyAction === "treat" ? "치료 중..." : "치료"}
          </button>
          <button
            className="ghost-btn"
            onClick={() => void runAction("sleep")}
            disabled={busyAction !== null}
          >
            {sleeping ? "깨우기" : "재우기"}
          </button>
          <Link to="/app/battle" className="danger-btn">
            배틀 이동
          </Link>
        </div>

        {alerts.length > 0 && (
          <ul className="alert-list">
            {alerts.map((alert) => (
              <li key={alert}>{alert}</li>
            ))}
          </ul>
        )}

        {message && <p className="message-line">{message}</p>}
      </div>
    </section>
  );
}

function Meter({ label, value }: { label: string; value: number }) {
  return (
    <div className="meter-item">
      <div className="meter-head">
        <span>{label}</span>
        <strong>{value}</strong>
      </div>
      <div className="meter-track">
        <div className="meter-fill" style={{ width: `${Math.max(0, value)}%` }} />
      </div>
    </div>
  );
}
