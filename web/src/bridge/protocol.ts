/** Room bridge protocol. Development fixtures never participate in the native entry point. */
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
    | { type: "forest.snapshot"; revision: number; payload: ForestSnapshot }
    | { type: "forest.result"; success: true; revision: number; payload: ForestSnapshot }
    | { type: "forest.navigateBack" }
    | {
        type: "forest.result";
        success: false;
        error: "storage" | "unsupportedVersion" | "invalidRequest";
      }
  );
