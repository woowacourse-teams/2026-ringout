import { useEffect, useRef, useState } from "react";
import atlasUrl from "../../assets/forest/seed-animation.webp";
import { forestImages } from "./assets";

/** Reuses the home screen's 7 × 6 atlas: 41 frames at 24 fps. */
export function SeedAnimation({ variant = "sheet" }: { variant?: "sheet" | "card" }) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const [ready, setReady] = useState(false);
  useEffect(() => {
    const node = canvas.current;
    const context = node?.getContext("2d");
    if (!node || !context) return;
    const motion = window.matchMedia("(prefers-reduced-motion: reduce)");
    const atlas = new Image();
    let disposed = false;
    let request = 0;
    let elapsed = 0;
    let previous = 0;
    let lastFrame = -1;
    let loaded = false;
    const draw = (time: number) => {
      if (previous) elapsed += time - previous;
      previous = time;
      const frame = Math.floor(elapsed * 24 / 1000) % 41;
      if (frame !== lastFrame) {
        context.clearRect(0, 0, 384, 384);
        context.drawImage(atlas, frame % 7 * 384, Math.floor(frame / 7) * 384,
          384, 384, 0, 0, 384, 384);
        lastFrame = frame;
      }
      request = requestAnimationFrame(draw);
    };
    const sync = () => {
      cancelAnimationFrame(request);
      previous = 0;
      setReady(loaded && !motion.matches);
      if (loaded && !motion.matches && !document.hidden) request = requestAnimationFrame(draw);
    };
    atlas.onload = () => {
      if (disposed || atlas.width !== 2688 || atlas.height !== 2304) return;
      loaded = true;
      sync();
    };
    atlas.src = atlasUrl;
    motion.addEventListener("change", sync);
    document.addEventListener("visibilitychange", sync);
    return () => {
      disposed = true;
      cancelAnimationFrame(request);
      atlas.onload = null;
      motion.removeEventListener("change", sync);
      document.removeEventListener("visibilitychange", sync);
    };
  }, []);
  return (
    <span className={`${variant === "sheet" ? "sheet-seed" : "card-seed"} seed-animation`} role="img" aria-label="웃고 있는 나무 씨앗">
      <img src={forestImages.seed} alt="" width={variant === "sheet" ? 273 : 85} height={variant === "sheet" ? 273 : 85} style={{ visibility: ready ? "hidden" : "visible" }} />
      <canvas ref={canvas} width="384" height="384" aria-hidden="true" style={{ visibility: ready ? "visible" : "hidden" }} />
    </span>
  );
}
