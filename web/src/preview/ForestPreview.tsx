import { useMemo, useState } from "react";
import { ForestCanvas } from "../forest/ForestCanvas";
import { TREE_KINDS, TREE_LABELS } from "../forest/layout";
import { createSampleForest } from "./fixtures";
import type { SampleKind } from "./fixtures";

export function ForestPreview() {
  const [count, setCount] = useState(8);
  const [kind, setKind] = useState<SampleKind>("mixed");
  const [showGrid, setShowGrid] = useState(false);
  const forest = useMemo(() => createSampleForest(count, kind), [count, kind]);
  const counts = TREE_KINDS.map((value) => ({
    kind: value,
    count: forest.trees.filter((tree) => tree.kind === value).length,
  }));

  return (
    <main className="preview-page">
      <header className="page-header">
        <a
          className="wordmark"
          href="./"
          aria-label="링아웃 숲 미리보기 처음으로"
        >
          RINGOUT
          <span />
        </a>
        <span className="prototype-label">
          숲 키우기 <span> / </span> 3D 미리보기
        </span>
      </header>

      <div className="page-intro">
        <div>
          <p className="eyebrow">FOREST PREVIEW</p>
          <h1>숲 미리보기</h1>
        </div>
        <p className="intro-description">
          나무가 하나씩 모여 완성되는 숲.
          <br />
          직접 돌려 보고, 가까이 살펴보세요.
        </p>
      </div>

      <div className="preview-layout">
        <ForestCanvas trees={forest.trees} showGrid={showGrid} />
        <aside className="preview-panel" aria-label="샘플 숲 설정">
          <div className="panel-heading">
            <h2>어떤 숲을 볼까요?</h2>
            <p>나무 수와 종류를 바꿔 보세요.</p>
          </div>

          <fieldset>
            <legend>나무 수</legend>
            <div className="count-options">
              {[0, 1, 8, 16].map((value) => (
                <button
                  type="button"
                  key={value}
                  aria-pressed={count === value}
                  onClick={() => setCount(value)}
                >
                  {value === 0 ? "빈 땅" : `${value}그루`}
                </button>
              ))}
            </div>
          </fieldset>

          <fieldset>
            <legend>나무 종류</legend>
            <div className="kind-options">
              {(["mixed", ...TREE_KINDS] as const).map((value) => (
                <label
                  key={value}
                  className={
                    kind === value ? "kind-option selected" : "kind-option"
                  }
                >
                  <input
                    type="radio"
                    name="tree-kind"
                    value={value}
                    checked={kind === value}
                    onChange={() => setKind(value)}
                  />
                  <span
                    className={`tree-swatch swatch-${value}`}
                    aria-hidden="true"
                  />
                  <span>
                    {value === "mixed" ? "세 종류 함께" : TREE_LABELS[value]}
                  </span>
                  <svg
                    className="selected-check"
                    viewBox="0 0 20 20"
                    fill="none"
                    aria-hidden="true"
                  >
                    <path d="m4 10 4 4 8-8" />
                  </svg>
                </label>
              ))}
            </div>
          </fieldset>

          <div className="forest-summary" aria-live="polite" aria-atomic="true">
            <span className="summary-label">지금 숲에는</span>
            <p>
              <strong>{count}</strong>그루의 나무가 있어요
            </p>
            <div className="species-counts">
              {counts.map((item) => (
                <span key={item.kind}>
                  {TREE_LABELS[item.kind]} <b>{item.count}</b>
                </span>
              ))}
            </div>
          </div>

          <label className="grid-toggle">
            <input
              type="checkbox"
              checked={showGrid}
              onChange={(event) => setShowGrid(event.target.checked)}
            />
            <span>배치 칸 표시</span>
            <span className="grid-size">4 × 4</span>
          </label>
          <p className="sample-notice">
            샘플 데이터로 보는 시제품입니다.
            <br />
            실제 도착 기록에는 영향을 주지 않아요.
          </p>
        </aside>
      </div>

      <footer className="page-footer">
        <span>목적지에 도착하는 하루가 모여, 나만의 숲으로.</span>
        <span>STEP 02 · 배치와 시점 검토</span>
      </footer>
    </main>
  );
}
