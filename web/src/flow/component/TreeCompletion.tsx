import { useEffect, useRef, useState } from "react";
import type { ForestTree } from "../../bridge/protocol";
import { TREE_LABELS } from "../../forest/layout";
import { forestImages } from "./assets";

interface Props {
  tree: ForestTree;
  error: string | null;
  onPlant(): void;
  onBack(): void;
}
export function TreeCompletion({ tree, error, onPlant, onBack }: Props) {
  const dialog = useRef<HTMLDialogElement>(null);
  const artwork = useRef<HTMLImageElement>(null);
  const [imageStatus, setImageStatus] = useState<"loading" | "ready" | "error">(
    "loading",
  );
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    const node = dialog.current!;
    node.showModal();
    if (artwork.current?.complete && artwork.current.naturalWidth > 0)
      setImageStatus("ready");
    return () => {
      node.close();
      previous?.focus();
    };
  }, []);
  return (
    <dialog
      ref={dialog}
      className="tree-completion"
      aria-labelledby="completion-title"
      onCancel={(event) => {
        event.preventDefault();
        onBack();
      }}
    >
      <div className="completion-content">
        <div className="completion-artwork">
          <img
            ref={artwork}
            className={`completion-tree tree-${tree.kind}`}
            src={`${forestImages.trees[tree.kind]}${attempt ? `?retry=${attempt}` : ""}`}
            alt={TREE_LABELS[tree.kind]}
            onLoad={() => setImageStatus("ready")}
            onError={() => setImageStatus("error")}
          />
          {imageStatus === "loading" && (
            <p className="artwork-status" role="status">
              나무를 불러오고 있어요
            </p>
          )}
          {imageStatus === "error" && (
            <div className="artwork-status" role="alert">
              <p>나무 이미지를 불러오지 못했어요.</p>
              <button
                className="forest-cta"
                onClick={() => {
                  setImageStatus("loading");
                  setAttempt((value) => value + 1);
                }}
              >
                이미지 다시 불러오기
              </button>
            </div>
          )}
        </div>
        <h2 id="completion-title">
          씨앗이 모두 자라서
          <br />
          나무가 되었어요!
        </h2>
        <button
          className="forest-cta"
          disabled={imageStatus !== "ready"}
          onClick={onPlant}
        >
          {error ? "다시 시도" : "나무 심기!"}
        </button>
        {error && (
          <p role="alert" className="forest-error">
            {error}
          </p>
        )}
      </div>
    </dialog>
  );
}
