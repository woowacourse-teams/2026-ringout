import { useEffect, useRef, useState } from "react";
import type { ForestTree } from "../bridge/protocol";
import { ForestRenderer } from "./ForestRenderer";
import type { SceneStatus } from "./ForestRenderer";

interface Props {
  trees: ForestTree[];
  showGrid: boolean;
  variant?: "preview" | "app";
  revealCycle?: number | null;
}

export function ForestCanvas({
  trees,
  showGrid,
  variant = "preview",
  revealCycle = null,
}: Props) {
  const container = useRef<HTMLDivElement>(null);
  const renderer = useRef<ForestRenderer | null>(null);
  const [status, setStatus] = useState<SceneStatus>({ phase: "loading" });
  const [attempt, setAttempt] = useState(0);
  const [zoom, setZoom] = useState(1);

  useEffect(() => {
    if (!container.current) return;
    setStatus({ phase: "loading" });
    let active = true;
    try {
      const scene = new ForestRenderer(
        container.current,
        (next) => {
          if (active) setStatus(next);
        },
        (next) => {
          if (active) setZoom(next);
        },
      );
      renderer.current = scene;
      return () => {
        active = false;
        renderer.current = null;
        scene.dispose();
      };
    } catch {
      setStatus({
        phase: "error",
        message:
          "이 환경에서 3D 화면을 시작하지 못했어요. WebGL을 지원하는 브라우저에서 다시 시도해 주세요.",
      });
      return () => {
        active = false;
      };
    }
  }, [attempt]);

  useEffect(() => {
    renderer.current?.setTrees(trees);
  }, [trees, attempt]);
  useEffect(() => {
    renderer.current?.setGrid(showGrid);
  }, [showGrid, attempt]);
  useEffect(() => {
    renderer.current?.setRevealCycle(revealCycle);
  }, [revealCycle, attempt]);
  const ready = status.phase === "ready";

  return (
    <section
      className={`forest-viewport ${variant === "app" ? "forest-viewport-app" : ""}`}
      aria-label="3D 숲"
      data-phase={status.phase}
    >
      {variant === "preview" && (
        <div className="scene-caption" aria-hidden="true">
          <span className="scene-label">나의 작은 숲</span>
          <span className="scene-count">
            <strong>{trees.length}</strong>
            <span>/ 16 그루</span>
          </span>
        </div>
      )}
      <div
        className="canvas-container"
        ref={container}
        data-testid="forest-canvas"
      />
      {status.phase === "loading" && (
        <div className="scene-message" role="status">
          <span className="loading-dot" />
          <p>숲을 불러오고 있어요</p>
        </div>
      )}
      {status.phase === "error" && (
        <div className="scene-message scene-error" role="alert">
          <strong>숲을 불러오지 못했어요</strong>
          <p>{status.message}</p>
          <button
            className="primary-button"
            onClick={() => setAttempt((value) => value + 1)}
          >
            다시 불러오기
          </button>
        </div>
      )}
      <div
        className={
          variant === "app"
            ? "scene-footer scene-accessible-controls"
            : "scene-footer"
        }
      >
        <p id="forest-gestures">
          드래그로 회전 · 스크롤 또는 두 손가락으로 확대
        </p>
        <div className="camera-controls" aria-label="카메라 조작">
          <button
            type="button"
            title="시점 초기화"
            aria-label="시점 초기화"
            disabled={!ready}
            onClick={() => renderer.current?.resetView()}
          >
            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M4 9a8 8 0 1 1 .4 7M4 4v5h5" />
            </svg>
          </button>
          <span className="control-divider" />
          <button
            type="button"
            aria-label="축소"
            disabled={!ready || zoom <= 0.75}
            onClick={() => renderer.current?.zoomBy(1 / 1.15)}
          >
            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M6 12h12" />
            </svg>
          </button>
          <output aria-label="확대 비율">{Math.round(zoom * 100)}%</output>
          <button
            type="button"
            aria-label="확대"
            disabled={!ready || zoom >= 1.6}
            onClick={() => renderer.current?.zoomBy(1.15)}
          >
            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M6 12h12M12 6v12" />
            </svg>
          </button>
        </div>
      </div>
    </section>
  );
}
