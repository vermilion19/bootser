import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { login } from "../api/authApi";
import { ApiError } from "../api/http";
import { useAuth } from "../store/auth";

export function LoginPage() {
  const navigate = useNavigate();
  const { login: storeLogin } = useAuth();
  const [form, setForm] = useState({ loginId: "", password: "" });
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (loading) return;

    setLoading(true);
    setMessage("");
    try {
      const user = await login(form);
      storeLogin(user);
      navigate("/app", { replace: true });
    } catch (error) {
      const text =
        error instanceof ApiError
          ? error.payload?.message ?? "로그인에 실패했습니다."
          : "로그인에 실패했습니다.";
      setMessage(text);
    } finally {
      setLoading(false);
    }
  };

  return (
    <section className="auth-wrap">
      <form className="auth-card" onSubmit={onSubmit}>
        <h1>Damagochi</h1>
        <p>로그인 후 나만의 크리처를 키워보세요.</p>

        <label>
          아이디
          <input
            value={form.loginId}
            onChange={(e) => setForm((prev) => ({ ...prev, loginId: e.target.value }))}
            required
          />
        </label>

        <label>
          비밀번호
          <input
            type="password"
            value={form.password}
            onChange={(e) => setForm((prev) => ({ ...prev, password: e.target.value }))}
            required
          />
        </label>

        <button className="primary-btn" disabled={loading}>
          {loading ? "로그인 중..." : "로그인"}
        </button>

        <p className="auth-foot">
          계정이 없나요? <Link to="/signup">회원가입</Link>
        </p>

        {message && <p className="message-line">{message}</p>}
      </form>
    </section>
  );
}
