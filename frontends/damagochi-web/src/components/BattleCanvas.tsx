import { useEffect, useRef } from "react";

interface BattleCanvasProps {
  leftName: string;
  rightName: string;
  winner: "LEFT" | "RIGHT" | "DRAW";
}

export function BattleCanvas({ leftName, rightName, winner }: BattleCanvasProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    const width = canvas.width;
    const height = canvas.height;
    let frame = 0;
    let rafId = 0;

    const draw = () => {
      frame += 1;
      ctx.clearRect(0, 0, width, height);

      const bg = ctx.createLinearGradient(0, 0, width, height);
      bg.addColorStop(0, "#15243f");
      bg.addColorStop(1, "#203a2f");
      ctx.fillStyle = bg;
      ctx.fillRect(0, 0, width, height);

      const leftX = 90 + Math.sin(frame / 10) * 3;
      const rightX = width - 150 - Math.sin(frame / 10) * 3;
      const y = height / 2 - 40;

      ctx.fillStyle = winner === "LEFT" ? "#ffd35c" : "#6f95ff";
      ctx.fillRect(leftX, y, 60, 60);

      ctx.fillStyle = winner === "RIGHT" ? "#ffd35c" : "#ff857f";
      ctx.fillRect(rightX, y, 60, 60);

      if (winner !== "DRAW" && frame % 20 < 10) {
        ctx.fillStyle = "rgba(255, 255, 255, 0.55)";
        const flashX = winner === "LEFT" ? rightX : leftX;
        ctx.fillRect(flashX - 8, y - 8, 76, 76);
      }

      ctx.fillStyle = "#ffffff";
      ctx.font = "bold 14px Space Grotesk";
      ctx.fillText(leftName, leftX - 8, y + 84);
      ctx.fillText(rightName, rightX - 8, y + 84);

      rafId = requestAnimationFrame(draw);
    };

    rafId = requestAnimationFrame(draw);
    return () => cancelAnimationFrame(rafId);
  }, [leftName, rightName, winner]);

  return (
    <canvas
      ref={canvasRef}
      width={560}
      height={280}
      className="pixel-canvas battle-canvas"
      aria-label="배틀 연출 캔버스"
    />
  );
}
