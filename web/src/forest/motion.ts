export const PLANT_DURATION = 650;

/** One attempt to reveal an already acquired tree; retries receive a new ID. */
export interface ForestRevealRequest {
  growthCycle: number;
  attemptId: number;
}
