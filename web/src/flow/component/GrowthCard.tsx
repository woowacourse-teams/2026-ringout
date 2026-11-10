import type { ForestSnapshot } from "../../bridge/protocol";
import { SeedAnimation } from "./SeedAnimation";

export function GrowthCard({
  growth,
  onClick,
}: {
  growth: ForestSnapshot["growth"];
  onClick(): void;
}) {
  if (!growth.hasGrowingSeed)
    return (
      <section className="growth-card forest-complete" aria-label="숲 완성">
        <div>
          <p>함께 키운 나만의 숲</p>
          <h2>숲이 완성되었어요!</h2>
          <p>
            <strong>16그루</strong>의 나무가 모였어요
          </p>
        </div>
      </section>
    );
  return (
    <button
      className="growth-card"
      onClick={onClick}
      aria-haspopup="dialog"
      aria-label={`나무 씨앗 성장 상태 보기, ${growth.remainingDays}일 남음`}
    >
      <SeedAnimation variant="card" />
      <span className="growth-copy">
        <span>현재 키우는 씨앗</span>
        <strong>나무 씨앗</strong>
        <span className="growth-remaining">
          <b>{growth.remainingDays}일</b> 더 도착하면 나무로 성장해요
        </span>
      </span>
    </button>
  );
}
