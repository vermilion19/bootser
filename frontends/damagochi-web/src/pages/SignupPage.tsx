import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { signUp } from "../api/authApi";
import { ApiError } from "../api/http";
import { useAuth } from "../store/auth";

export function SignupPage() {
  const navigate = useNavigate();
  const { login } = useAuth();
  const [form, setForm] = useState({
    loginId: "",
    nickname: "",
    password: "",
    confirmPassword: "",
  });
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (loading) return;

    if (form.password !== form.confirmPassword) {
      setMessage("비밀번호 확인 값이 일치하지 않습니다.");
      return;
    }

    setLoading(true);
    setMessage("");

    try {
      const user = await signUp({
        loginId: form.loginId,
        password: form.password,
        nickname: form.nickname,
      });
      login(user);
      navigate("/app", { replace: true });
    } catch (error) {
      const text =
        error instanceof ApiError
          ? error.payload?.message ?? "회원가입에 실패했습니다."
          : "회원가입에 실패했습니다.";
      setMessage(text);
    } finally {
      setLoading(false);
    }
  };

  return (
    <section className="auth-wrap">
      <form className="auth-card" onSubmit={onSubmit}>
        <h1>회원가입</h1>
        <p>아이디, 비밀번호, 닉네임으로 바로 시작할 수 있어요.</p>

        <label>
          아이디
          <input
            value={form.loginId}
            onChange={(e) => setForm((prev) => ({ ...prev, loginId: e.target.value }))}
            maxLength={50}
            required
          />
        </label>

        <label>
          닉네임
          <input
            value={form.nickname}
            onChange={(e) => setForm((prev) => ({ ...prev, nickname: e.target.value }))}
            maxLength={30}
            required
          />
        </label>

        <label>
          비밀번호
          <input
            type="password"
            value={form.password}
            onChange={(e) => setForm((prev) => ({ ...prev, password: e.target.value }))}
            minLength={4}
            required
          />
        </label>

        <label>
          비밀번호 확인
          <input
            type="password"
            value={form.confirmPassword}
            onChange={(e) =>
              setForm((prev) => ({ ...prev, confirmPassword: e.target.value }))
            }
            required
          />
        </label>

        <button className="primary-btn" disabled={loading}>
          {loading ? "가입 중..." : "가입 완료"}
        </button>

        <p className="auth-foot">
          이미 계정이 있나요? <Link to="/login">로그인</Link>
        </p>

        {message && <p className="message-line">{message}</p>}
      </form>
    </section>
  );
}
