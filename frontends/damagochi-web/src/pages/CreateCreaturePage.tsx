import { useEffect, useMemo, useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ApiError } from "../api/http";
import { createCreature } from "../api/creatureApi";
import { useAuth } from "../store/auth";
import { loadCreatureAssets, type LoadedCreatureAsset } from "../components/creatureAssets";

export function CreateCreaturePage() {
  const { user } = useAuth();
  const navigate = useNavigate();

  const [name, setName] = useState("");
  const [templates, setTemplates] = useState<LoadedCreatureAsset[]>([]);
  const [selectedKey, setSelectedKey] = useState<string>("");
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");

  useEffect(() => {
    let active = true;
    void loadCreatureAssets()
      .then((loaded) => {
        if (!active) return;
        setTemplates(loaded);
        if (loaded.length > 0) {
          setSelectedKey(loaded[0].template.key);
        }
      })
      .catch(() => {
        if (!active) return;
        setMessage("크리처 메타 파일을 읽지 못했습니다.");
      });

    return () => {
      active = false;
    };
  }, []);

  const selectedTemplate = useMemo(
    () => templates.find((item) => item.template.key === selectedKey) ?? templates[0] ?? null,
    [selectedKey, templates]
  );

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!user || loading || !selectedTemplate) return;

    setLoading(true);
    setMessage("");
    try {
      await createCreature(user.userId, {
        name,
        species: selectedTemplate.template.species,
      });
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
      <p>public/meta의 애니메이션 메타를 기반으로 종을 선택할 수 있습니다.</p>

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
          크리처 타입
          <select
            value={selectedTemplate?.template.key ?? ""}
            onChange={(e) => setSelectedKey(e.target.value)}
            disabled={templates.length === 0}
          >
            {templates.map((item) => (
              <option key={item.template.key} value={item.template.key}>
                {item.template.label} ({item.template.species})
              </option>
            ))}
          </select>
        </label>

        {selectedTemplate?.template.previewGifPath && (
          <div className="preview-box">
            <img
              src={selectedTemplate.template.previewGifPath}
              alt={`${selectedTemplate.template.label} preview`}
              className="preview-gif"
            />
            <small>
              idle preview · frame {selectedTemplate.meta.frame_size.w}x{selectedTemplate.meta.frame_size.h}
            </small>
          </div>
        )}

        <div className="button-row">
          <button className="primary-btn" type="submit" disabled={loading || !selectedTemplate}>
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
