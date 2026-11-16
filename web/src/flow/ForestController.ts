import type { ForestSnapshot, ForestTree } from "../bridge/protocol";
import type { ForestRevealRequest } from "../forest/motion";

/** Presentation never chooses a kind or slot; native commands persist confirmation state. */
export interface ForestCommands {
  completeOnboarding(): Promise<ForestSnapshot>;
  acknowledgeTree(growthCycle: number): Promise<ForestSnapshot>;
  back(): void;
}

export interface ForestUiState {
  snapshot: ForestSnapshot;
  page: "start" | "guide" | "forest";
  sheetOpen: boolean;
  busy: boolean;
  plantingCycle: number | null;
  revealRequest: ForestRevealRequest | null;
  hostActive: boolean;
  error: string | null;
}

export function pendingTree(snapshot: ForestSnapshot): ForestTree | undefined {
  return snapshot.trees
    .filter((tree) => tree.acknowledgedAtEpochMillis === null)
    .sort((a, b) => a.growthCycle - b.growthCycle)[0];
}

export function visibleTrees(state: ForestUiState): ForestTree[] {
  // Reveal only changes presentation. All trees already exist in the source snapshot.
  return state.snapshot.trees.filter(
    (tree) =>
      tree.acknowledgedAtEpochMillis !== null ||
      tree.growthCycle === state.plantingCycle,
  );
}

export class ForestController {
  private state: ForestUiState;
  private deferredSnapshot: ForestSnapshot | null = null;
  private readonly listeners = new Set<() => void>();
  private disposed = false;
  private nextAttempt = 0;
  private pendingReveal: {
    request: ForestRevealRequest;
    resolve: () => void;
    reject: (error: Error) => void;
  } | null = null;
  constructor(
    snapshot: ForestSnapshot,
    private readonly commands: ForestCommands,
  ) {
    this.state = {
      snapshot,
      page:
        snapshot.onboardingCompletedAtEpochMillis === null ? "start" : "forest",
      sheetOpen: false,
      busy: false,
      plantingCycle: null,
      revealRequest: null,
      hostActive: true,
      error: null,
    };
  }
  getSnapshot = (): ForestUiState => this.state;
  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };
  receiveSnapshot = (snapshot: ForestSnapshot): void => {
    if (this.disposed) return;
    if (this.state.busy) { this.deferredSnapshot = snapshot; return; }
    this.update({ snapshot, ...(snapshot.onboardingCompletedAtEpochMillis !== null ? { page: "forest" as const } : {}) });
    if (pendingTree(snapshot) || !snapshot.growth.hasGrowingSeed) this.closeSheet();
  };
  private update(patch: Partial<ForestUiState>): void {
    if (this.disposed) return;
    if (patch.busy === false && this.deferredSnapshot) {
      patch.snapshot = this.deferredSnapshot;
      this.deferredSnapshot = null;
    }
    this.state = { ...this.state, ...patch };
    this.listeners.forEach((listener) => listener());
  }
  setHostActive = (active: boolean): void => {
    if (this.state.hostActive !== active) this.update({ hostActive: active });
  };
  completeReveal = (request: ForestRevealRequest): void => {
    if (!this.isCurrentReveal(request) || !this.pendingReveal) return;
    const pending = this.pendingReveal;
    this.pendingReveal = null;
    pending.resolve();
  };
  failReveal = (request: ForestRevealRequest): void => {
    if (!this.isCurrentReveal(request) || !this.pendingReveal) return;
    const pending = this.pendingReveal;
    this.pendingReveal = null;
    pending.reject(new Error("Tree reveal interrupted"));
  };
  private isCurrentReveal(request: ForestRevealRequest): boolean {
    return !this.disposed && this.state.revealRequest?.attemptId === request.attemptId &&
      this.state.revealRequest.growthCycle === request.growthCycle;
  }
  dispose = (): void => {
    if (this.disposed) return;
    this.disposed = true;
    this.pendingReveal?.reject(new Error("Forest disposed"));
    this.pendingReveal = null;
    this.deferredSnapshot = null;
    this.listeners.clear();
  };
  nextGuide = (): void => {
    if (this.state.page === "start") this.update({ page: "guide" });
  };
  openSheet = (): void => {
    if (
      this.state.page === "forest" &&
      !pendingTree(this.state.snapshot) &&
      this.state.snapshot.growth.hasGrowingSeed
    )
      this.update({ sheetOpen: true });
  };
  closeSheet = (): void => {
    this.update({ sheetOpen: false });
  };
  back = (): void => {
    if (this.disposed || this.state.busy) return;
    if (this.state.sheetOpen) {
      this.closeSheet();
      return;
    }
    if (this.state.page === "guide") {
      this.update({ page: "start", error: null });
      return;
    }
    this.commands.back();
  };
  completeOnboarding = async (): Promise<void> => {
    if (this.disposed || this.state.busy || this.state.page !== "guide") return;
    this.update({ busy: true, error: null });
    try {
      const snapshot = await this.commands.completeOnboarding();
      if (snapshot.onboardingCompletedAtEpochMillis === null)
        throw new Error("Unconfirmed onboarding");
      this.update({ snapshot, page: "forest", busy: false });
    } catch {
      this.update({
        busy: false,
        error: "시작 상태를 저장하지 못했어요. 다시 시도해 주세요.",
      });
    }
  };
  plant = async (): Promise<void> => {
    const tree = pendingTree(this.state.snapshot);
    if (this.disposed || this.state.busy || this.state.page !== "forest" || !tree) return;
    const request = { growthCycle: tree.growthCycle, attemptId: ++this.nextAttempt };
    const rendered = new Promise<void>((resolve, reject) => {
      this.pendingReveal = { request, resolve, reject };
    });
    this.update({
      busy: true,
      plantingCycle: tree.growthCycle,
      revealRequest: request,
      error: null,
      sheetOpen: false,
    });
    let revealed = false;
    try {
      await rendered;
      if (!this.isCurrentReveal(request)) return;
      revealed = true;
      const snapshot = await this.commands.acknowledgeTree(tree.growthCycle);
      if (!this.isCurrentReveal(request)) return;
      const confirmed = snapshot.trees.find(
        (value) => value.growthCycle === tree.growthCycle,
      );
      if (confirmed?.acknowledgedAtEpochMillis == null)
        throw new Error("Unconfirmed tree");
      this.update({ snapshot, busy: false, plantingCycle: null, revealRequest: null });
    } catch {
      if (!this.isCurrentReveal(request)) return;
      this.update({
        busy: false,
        plantingCycle: null,
        revealRequest: null,
        error: revealed
          ? "확인 상태를 저장하지 못했어요. 나무는 유지돼요. 다시 시도해 주세요."
          : "나무 연출을 마치지 못했어요. 다시 시도해 주세요.",
      });
    }
  };
}
