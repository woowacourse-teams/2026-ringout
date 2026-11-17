import type { ForestSnapshot, TreeKind } from "../bridge/protocol";
import type { ForestCommands } from "../flow/ForestController";
import { createSampleForest } from "./fixtures";

export const FLOW_SCENARIOS = [
  { id: "first", title: "첫 입장", detail: "온보딩 2장 → 빈 숲 · 0/5일" },
  { id: "growing", title: "성장 중", detail: "4/5일 · 성장 카드와 바텀 시트" },
  { id: "pine", title: "소나무 완성", detail: "완성 안내 → 심기 → 다음 씨앗" },
  { id: "oak", title: "참나무 완성", detail: "저장된 참나무 표시와 심기" },
  { id: "big_tree", title: "빅트리 완성", detail: "저장된 빅트리 표시와 심기" },
  {
    id: "backlog",
    title: "미확인 나무 3그루",
    detail: "소나무 → 참나무 → 빅트리 순서로 안내",
  },
  {
    id: "last-seed",
    title: "마지막 씨앗",
    detail: "15그루 · 79일 · 마지막 1일",
  },
  {
    id: "full",
    title: "숲 완성",
    detail: "마지막 나무 안내 → 16그루 · 새 씨앗 없음",
  },
  {
    id: "start-error",
    title: "시작 저장 실패",
    detail: "첫 저장 실패 → 재시도 성공",
  },
  {
    id: "tree-error",
    title: "나무 확인 저장 실패",
    detail: "나무 유지 · 확인만 재시도",
  },
] as const;
export type FlowScenario = (typeof FLOW_SCENARIOS)[number]["id"];

export function createFlowSnapshot(scenario: FlowScenario): ForestSnapshot {
  const first = scenario === "first" || scenario === "start-error";
  const days = first
    ? 0
    : scenario === "growing"
      ? 4
      : scenario === "backlog"
        ? 15
        : scenario === "last-seed"
          ? 79
          : scenario === "full"
            ? 80
            : 5;
  const kind: TreeKind | "mixed" = ["pine", "oak", "big_tree"].includes(
    scenario,
  )
    ? (scenario as TreeKind)
    : "mixed";
  const snapshot = createSampleForest(Math.floor(days / 5), kind);
  snapshot.growth = {
    ...snapshot.growth,
    successfulDayCount: days,
    growthDayCount: days,
    filledSlots: days === 80 ? 0 : days % 5,
    remainingDays: days === 80 ? 0 : 5 - (days % 5),
  };
  snapshot.onboardingCompletedAtEpochMillis = first ? null : 1_794_096_000_000;
  snapshot.trees = snapshot.trees.map((tree) => ({
    ...tree,
    acknowledgedAtEpochMillis:
      scenario === "last-seed" || (scenario === "full" && tree.growthCycle < 16)
        ? tree.plantedAtEpochMillis
        : null,
  }));
  return snapshot;
}

/** Demo-only memory store; reload resets it. Native Room will replace this adapter in stage 4. */
export function createDemoCommands(
  initial: ForestSnapshot,
  scenario: FlowScenario,
  back: () => void,
): ForestCommands {
  let snapshot = structuredClone(initial);
  let fail = scenario === "start-error" || scenario === "tree-error";
  function rejectOnce(): void {
    if (fail) {
      fail = false;
      throw new Error("Demo storage failure");
    }
  }
  return {
    async completeOnboarding() {
      rejectOnce();
      snapshot = {
        ...snapshot,
        onboardingCompletedAtEpochMillis:
          snapshot.onboardingCompletedAtEpochMillis ?? Date.now(),
      };
      return structuredClone(snapshot);
    },
    async acknowledgeTree(growthCycle) {
      rejectOnce();
      if (!snapshot.trees.some((tree) => tree.growthCycle === growthCycle))
        throw new Error("Missing tree");
      snapshot = {
        ...snapshot,
        trees: snapshot.trees.map((tree) =>
          tree.growthCycle === growthCycle
            ? {
                ...tree,
                acknowledgedAtEpochMillis:
                  tree.acknowledgedAtEpochMillis ?? Date.now(),
              }
            : tree,
        ),
      };
      return structuredClone(snapshot);
    },
    back,
  };
}
