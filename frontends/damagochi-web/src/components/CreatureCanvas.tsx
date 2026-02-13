import { useEffect, useMemo, useRef, useState } from "react";
import type { CreatureStateView } from "../api/types";
import { loadCreatureAssets, type LoadedCreatureAsset } from "./creatureAssets";

interface CreatureCanvasProps {
  state: CreatureStateView;
  species: string;
  actionHint: "idle" | "feed" | "treat" | "sleep" | "battle";
}

type Phase = "idle" | "sleep";

export function CreatureCanvas({ state, species, actionHint }: CreatureCanvasProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [assets, setAssets] = useState<LoadedCreatureAsset[]>([]);

  useEffect(() => {
    let active = true;
    void loadCreatureAssets()
      .then((loaded) => {
        if (active) {
          setAssets(loaded);
        }
      })
      .catch(() => {
        if (active) {
          setAssets([]);
        }
      });
    return () => {
      active = false;
    };
  }, []);

  const asset = useMemo(
    () => assets.find((item) => item.template.species === species) ?? assets[0] ?? null,
    [assets, species]
  );

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    ctx.imageSmoothingEnabled = false;
    const width = canvas.width;
    const height = canvas.height;

    const fallbackDraw = (time: number) => {
      ctx.clearRect(0, 0, width, height);
      const bg = ctx.createLinearGradient(0, 0, 0, height);
      bg.addColorStop(0, "#f8f4dd");
      bg.addColorStop(1, "#e4f7ff");
      ctx.fillStyle = bg;
      ctx.fillRect(0, 0, width, height);

      const pulse = Math.sin(time / 300);
      const x = width / 2 - 28;
      const y = height / 2 - 30;
      ctx.fillStyle = state.sleeping ? "#c6a9ef" : "#efb36a";
      ctx.fillRect(x, y, 56, 56);

      if (actionHint !== "idle") {
        ctx.strokeStyle = "#2f6f53";
        ctx.lineWidth = 3;
        ctx.strokeRect(x - 4 + pulse, y - 4 + pulse, 64, 64);
      }
    };

    if (!asset) {
      let raf = 0;
      const draw = (time: number) => {
        fallbackDraw(time);
        raf = requestAnimationFrame(draw);
      };
      raf = requestAnimationFrame(draw);
      return () => cancelAnimationFrame(raf);
    }

    const phase: Phase = state.sleeping ? "sleep" : "idle";
    const clip = phase === "sleep" ? asset.meta.animations.sleep : asset.meta.animations.idle;

    if (!clip) {
      let raf = 0;
      const draw = (time: number) => {
        fallbackDraw(time);
        raf = requestAnimationFrame(draw);
      };
      raf = requestAnimationFrame(draw);
      return () => cancelAnimationFrame(raf);
    }

    const sheet = new Image();
    sheet.src = `/sheets/${clip.sheet}`;

    let rafId = 0;
    let loaded = false;

    const drawFrame = (time: number) => {
      ctx.clearRect(0, 0, width, height);

      const bg = ctx.createLinearGradient(0, 0, 0, height);
      bg.addColorStop(0, "#f8f4dd");
      bg.addColorStop(1, "#e4f7ff");
      ctx.fillStyle = bg;
      ctx.fillRect(0, 0, width, height);

      if (loaded) {
        const frameCount = clip.frames.length || 1;
        const frameDuration = Math.max(clip.frame_duration_ms, 80);
        const frameIndex = Math.floor(time / frameDuration) % frameCount;
        const frameX = clip.frames[frameIndex] * asset.meta.frame_size.w;

        const scale = 4;
        const drawW = asset.meta.frame_size.w * scale;
        const drawH = asset.meta.frame_size.h * scale;
        const drawX = Math.floor((width - drawW) / 2);
        const drawY = Math.floor((height - drawH) / 2 + 8);

        ctx.drawImage(
          sheet,
          frameX,
          0,
          asset.meta.frame_size.w,
          asset.meta.frame_size.h,
          drawX,
          drawY,
          drawW,
          drawH
        );
      }

      if (actionHint !== "idle") {
        const pulse = Math.sin(time / 250);
        ctx.strokeStyle = "#2f6f53";
        ctx.lineWidth = 3;
        ctx.strokeRect(width / 2 - 68 + pulse, height / 2 - 60 + pulse, 136, 136);
      }

      if (actionHint === "battle") {
        ctx.fillStyle = "#ff675b";
        ctx.fillRect(width / 2 - 2, height / 2 - 76, 4, 4);
      }

      rafId = requestAnimationFrame(drawFrame);
    };

    sheet.onload = () => {
      loaded = true;
    };

    rafId = requestAnimationFrame(drawFrame);
    return () => cancelAnimationFrame(rafId);
  }, [actionHint, asset, state.sleeping]);

  return (
    <canvas
      ref={canvasRef}
      width={280}
      height={220}
      className="pixel-canvas"
      aria-label="creature canvas"
    />
  );
}
