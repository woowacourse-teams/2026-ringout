/** Protocol draft for stage 4. This prototype never accesses native data or persists progress. */
export const FOREST_PROTOCOL_VERSION = 1 as const;
export type TreeKind = "pine" | "oak" | "big_tree";

export interface ForestTree {
  growthCycle: number;
  kind: TreeKind;
  slotIndex: number;
  plantedAtEpochMillis: number;
  acknowledgedAtEpochMillis: number | null;
}

export interface ForestSnapshot {
  growth: {
    successfulDayCount: number;
    growthDayCount: number;
    completedTreeCount: number;
    isComplete: boolean;
    hasGrowingSeed: boolean;
    filledSlots: number;
    remainingDays: number;
  };
  trees: ForestTree[];
  onboardingCompletedAtEpochMillis: number | null;
}

interface Envelope {
  version: typeof FOREST_PROTOCOL_VERSION;
  requestId: string;
}
export type WebToAppMessage = Envelope &
  (
    | { type: "forest.ready" }
    | { type: "forest.completeOnboarding" }
    | { type: "forest.acknowledgeTree"; growthCycle: number }
    | { type: "forest.back" }
  );
export type AppToWebMessage = Envelope &
  (
    | { type: "forest.snapshot"; payload: ForestSnapshot }
    | { type: "forest.result"; success: true }
    | {
        type: "forest.result";
        success: false;
        error: "storage" | "unsupportedVersion" | "invalidRequest";
      }
  );
