import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { ApiError } from "../api/http";
import { activateCreature, getCreatures } from "../api/creatureApi";
import type { CreatureSummary } from "../api/types";
import { useAuth } from "../store/auth";

export function CreaturesPage() {
  const { user } = useAuth();
  const [creatures, setCreatures] = useState<CreatureSummary[]>([]);
  const [loadingId, setLoadingId] = useState<number | null>(null);
  const [message, setMessage] = useState("");

  const activeId = useMemo(
    () => creatures.find((c) => c.active)?.id ?? null,
    [creatures]
  );

  const refresh = async () => {
    if (!user) return;
    try {
      setCreatures(await getCreatures(user.userId));
    } catch (error) {
      const text =
        error instanceof ApiError
          ? error.payload?.message ?? "목록을 불러오지 못했습니다."
          : "목록을 불러오지 못했습니다.";
      setMessage(text);
    }
  };

  useEffect(() => {
    void refresh();
  }, [user]);

  const onActivate = async (creatureId: number) => {
    if (!user) return;
    setLoadingId(creatureId);
    setMessage("");
    try {
      const activated = await activateCreature(user.userId, creatureId);
      setCreatures((prev) =>
        prev.map((item) => {
          if (item.id === activated.id) return activated;
          if (item.active) return { ...item, active: false };
          return item;
        })
      );
      setMessage(`${activated.name}이(가) Active로 전환되었습니다.`);
    } catch (error) {
      const text =
        error instanceof ApiError
          ? error.payload?.message ?? "전환에 실패했습니다."
          : "전환에 실패했습니다.";
      setMessage(text);
    } finally {
      setLoadingId(null);
    }
  };

  return (
    <section className="stack">
      <div className="panel heading-row">
        <div>
          <h2>크리처 관리</h2>
          <p>최대 3마리까지 보유할 수 있고 Active는 1마리만 유지됩니다.</p>
        </div>
        <Link to="/app/creatures/new" className="primary-btn">
          새 크리처 분양
        </Link>
      </div>

      <div className="card-grid">
        {creatures.map((creature) => (
          <article key={creature.id} className="panel creature-card">
            <header>
              <h3>{creature.name}</h3>
              <span className={creature.active ? "badge active" : "badge"}>
                {creature.active ? "ACTIVE" : "BACKUP"}
              </span>
            </header>
            <p>
              종: {creature.species} / 단계: {creature.stage}
            </p>
            <p>
              체력 {creature.state.health} · 배고픔 {creature.state.hunger} · 컨디션 {creature.state.conditionScore}
            </p>

            <button
              className="secondary-btn"
              disabled={creature.id === activeId || loadingId !== null}
              onClick={() => void onActivate(creature.id)}
            >
              {loadingId === creature.id ? "전환 중..." : creature.id === activeId ? "현재 Active" : "Active 전환"}
            </button>
          </article>
        ))}
      </div>

      {message && <p className="message-line">{message}</p>}
    </section>
  );
}
