import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ApiError } from "../api/http";
import { createCreature } from "../api/creatureApi";
import { useAuth } from "../store/auth";

export function CreateCreaturePage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [name, setName] = useState("");
  const [species, setSpecies] = useState("fox");
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!user || loading) return;

    setLoading(true);
    setMessage("");
    try {
      await createCreature(user.userId, { name, species });
      navigate("/app/creatures", { replace: true });
    } catch (error) {
      const text =
        error instanceof ApiError
          ? error.payload?.message ?? "생성에 실패했습니다."
          : "생성에 실패했습니다.";
      setMessage(text);
    } finally {
      setLoading(false);
    }
  };

  return (
    <section className="panel form-panel">
      <h2>크리처 분양</h2>
      <p>새로운 친구를 만들어 Active/Backup 슬롯을 구성해보세요.</p>

      <form onSubmit={onSubmit} className="form-grid">
        <label>
          이름
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="예: 몽실"
            required
          />
        </label>

        <label>
          종
          <select value={species} onChange={(e) => setSpecies(e.target.value)}>
            <option value="fox">Fox</option>
            <option value="cat">Cat</option>
            <option value="owl">Owl</option>
            <option value="dog">Dog</option>
          </select>
        </label>

        <div className="button-row">
          <button className="primary-btn" type="submit" disabled={loading}>
            {loading ? "생성 중..." : "분양 완료"}
          </button>
          <Link to="/app/creatures" className="ghost-btn">
            취소
          </Link>
        </div>
      </form>

      {message && <p className="message-line">{message}</p>}
    </section>
  );
}
