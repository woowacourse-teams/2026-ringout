import type { ForestSnapshot, TreeKind } from "../bridge/protocol";
import { TREE_CAPACITY, TREE_KINDS } from "../forest/layout";

// A fixed, scattered sample: changing the count retains the existing sample positions.
export const SAMPLE_SLOTS = [
  10, 4, 7, 13, 1, 15, 8, 3, 5, 11, 0, 14, 6, 9, 2, 12,
] as const;
export type SampleKind = TreeKind | "mixed";

export function createSampleForest(
  count: number,
  kind: SampleKind = "mixed",
): ForestSnapshot {
  if (!Number.isInteger(count) || count < 0 || count > TREE_CAPACITY)
    throw new Error("샘플 나무 수가 올바르지 않습니다.");
  const complete = count === TREE_CAPACITY;
  return {
    growth: {
      successfulDayCount: count * 5,
      growthDayCount: count * 5,
      completedTreeCount: count,
      isComplete: complete,
      hasGrowingSeed: !complete,
      filledSlots: 0,
      remainingDays: complete ? 0 : 5,
    },
    trees: SAMPLE_SLOTS.slice(0, count).map((slotIndex, index) => ({
      growthCycle: index + 1,
      kind: kind === "mixed" ? TREE_KINDS[index % TREE_KINDS.length] : kind,
      slotIndex,
      plantedAtEpochMillis: 1_794_096_000_000 + index * 5 * 86_400_000,
      acknowledgedAtEpochMillis: null,
    })),
    onboardingCompletedAtEpochMillis: null,
  };
}
