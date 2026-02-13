import { useEffect, useRef } from "react";
import type { CreatureStateView } from "../api/types";

interface CreatureCanvasProps {
  state: CreatureStateView;
  actionHint: "idle" | "feed" | "treat" | "sleep" | "battle";
}

export function CreatureCanvas({ state, actionHint }: CreatureCanvasProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    ctx.imageSmoothingEnabled = false;

    const width = canvas.width;
    const height = canvas.height;

    const draw = (time: number) => {
      ctx.clearRect(0, 0, width, height);

      const bg = ctx.createLinearGradient(0, 0, 0, height);
      bg.addColorStop(0, "#f8f4dd");
      bg.addColorStop(1, "#e4f7ff");
      ctx.fillStyle = bg;
      ctx.fillRect(0, 0, width, height);

      const pulse = Math.sin(time / 300);
      const baseX = width / 2;
      const baseY = height / 2 + 8;
      const bodyW = 54;
      const bodyH = 46;

      const tired = state.health < 30 || state.conditionScore < 30;
      const healthyColor = tired ? "#d98d71" : "#efb36a";

      ctx.fillStyle = healthyColor;
      ctx.fillRect(baseX - bodyW / 2, baseY - bodyH / 2, bodyW, bodyH);

      ctx.fillStyle = "#2a2621";
      if (state.sleeping) {
        ctx.fillRect(baseX - 14, baseY - 6, 8, 2);
        ctx.fillRect(baseX + 6, baseY - 6, 8, 2);
      } else {
        const blink = Math.floor(time / 1900) % 5 === 0;
        const eyeH = blink ? 2 : 7;
        ctx.fillRect(baseX - 14, baseY - 10, 8, eyeH);
        ctx.fillRect(baseX + 6, baseY - 10, 8, eyeH);
      }

      ctx.fillRect(baseX - 2, baseY + 2, 4, 4);

      if (actionHint !== "idle") {
        ctx.strokeStyle = "#2f6f53";
        ctx.lineWidth = 3;
        ctx.strokeRect(
          baseX - bodyW / 2 - 4 + pulse,
          baseY - bodyH / 2 - 4 + pulse,
          bodyW + 8,
          bodyH + 8
        );
      }

      if (actionHint === "battle") {
        ctx.fillStyle = "#ff675b";
        ctx.fillRect(baseX - 2, baseY - bodyH / 2 - 12, 4, 4);
      }

      requestAnimationFrame(draw);
    };

    const raf = requestAnimationFrame(draw);
    return () => cancelAnimationFrame(raf);
  }, [actionHint, state.conditionScore, state.health, state.sleeping]);

  return (
    <canvas
      ref={canvasRef}
      width={280}
      height={220}
      className="pixel-canvas"
      aria-label="크리처 상태 캔버스"
    />
  );
}
