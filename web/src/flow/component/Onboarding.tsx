import { ForestCanvas } from "../../forest/ForestCanvas";
import type { ForestTree } from "../../bridge/protocol";
import { forestImages } from "./assets";

// Illustration only: these trees never enter the user snapshot.
const guideTrees: ForestTree[] = [10, 4, 7, 13, 1].map((slotIndex, index) => ({
  growthCycle: index + 1,
  kind: "pine",
  slotIndex,
  plantedAtEpochMillis: 0,
  acknowledgedAtEpochMillis: 0,
}));
interface Props {
  page: "guide" | "start";
  busy: boolean;
  error: string | null;
  onNext(): void;
  onStart(): void;
}
export function Onboarding({ page, busy, error, onNext, onStart }: Props) {
  return (
    <section
      className={`forest-onboarding onboarding-${page}`}
      aria-labelledby="onboarding-title"
    >
      {page === "guide" ? (
        <div className="onboarding-art">
          <img
            src={forestImages.seed}
            alt="웃고 있는 나무 씨앗"
            width="338"
            height="338"
          />
        </div>
      ) : (
        <div className="onboarding-garden">
          <ForestCanvas trees={guideTrees} showGrid={false} variant="app" />
        </div>
      )}
      <div className="onboarding-copy">
        {page === "start" && <p className="forest-badge">나만의 숲 만들기</p>}
        <h1 id="onboarding-title" tabIndex={-1}>
          {page === "guide" ? (
            <>
              알람이 울리고 목적지에 도착하면
              <br />
              나무 씨앗이 성장해요
            </>
          ) : (
            <>
              링아웃과 하루를 시작하고
              <br />
              나만의 숲을 키워보세요!
            </>
          )}
        </h1>
        <button
          className="forest-cta"
          disabled={busy}
          onClick={page === "start" ? onNext : onStart}
        >
          {busy
            ? "저장 중…"
            : error
              ? "다시 시도"
              : page === "guide"
                ? "확인"
                : "시작하기"}
        </button>
        {error && (
          <p className="forest-error" role="alert">
            {error}
          </p>
        )}
      </div>
    </section>
  );
}
