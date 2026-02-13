import { useEffect, useState } from "react";

const SOUND_KEY = "damagochi.setting.sound";
const FX_KEY = "damagochi.setting.fx";

export function SettingsPage() {
  const [sound, setSound] = useState<boolean>(true);
  const [effects, setEffects] = useState<boolean>(true);

  useEffect(() => {
    const savedSound = localStorage.getItem(SOUND_KEY);
    const savedFx = localStorage.getItem(FX_KEY);
    if (savedSound != null) setSound(savedSound === "1");
    if (savedFx != null) setEffects(savedFx === "1");
  }, []);

  useEffect(() => {
    localStorage.setItem(SOUND_KEY, sound ? "1" : "0");
  }, [sound]);

  useEffect(() => {
    localStorage.setItem(FX_KEY, effects ? "1" : "0");
  }, [effects]);

  return (
    <section className="panel form-panel">
      <h2>설정</h2>
      <p>MVP 범위에서는 클라이언트 로컬 설정만 제공합니다.</p>

      <label className="switch-row">
        <span>사운드</span>
        <input type="checkbox" checked={sound} onChange={(e) => setSound(e.target.checked)} />
      </label>

      <label className="switch-row">
        <span>전투 연출 효과</span>
        <input type="checkbox" checked={effects} onChange={(e) => setEffects(e.target.checked)} />
      </label>
    </section>
  );
}
