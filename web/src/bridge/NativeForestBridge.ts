import type { ForestSnapshot } from "./protocol";
import type { ForestCommands } from "../flow/ForestController";

export interface ForestTransport { postMessage(message: string): void }
interface Pending {
  resolve(snapshot: ForestSnapshot): void;
  reject(error: Error): void;
  timer: ReturnType<typeof setTimeout>;
}

function isSnapshot(value: unknown): value is ForestSnapshot {
  if (!value || typeof value !== "object") return false;
  const s = value as ForestSnapshot;
  const g = s.growth;
  const time = (v: unknown) => v === null || (typeof v === "number" && Number.isSafeInteger(v) && v >= 0);
  return !!g && [g.successfulDayCount, g.growthDayCount, g.completedTreeCount, g.filledSlots, g.remainingDays].every(v => Number.isSafeInteger(v) && v >= 0) &&
    typeof g.isComplete === "boolean" && typeof g.hasGrowingSeed === "boolean" &&
    time(s.onboardingCompletedAtEpochMillis) && Array.isArray(s.trees) && s.trees.length <= 16 &&
    s.trees.every(t => Number.isInteger(t.growthCycle) && t.growthCycle >= 1 && t.growthCycle <= 16 &&
      Number.isInteger(t.slotIndex) && t.slotIndex >= 0 && t.slotIndex < 16 &&
      ["pine", "oak", "big_tree"].includes(t.kind) && typeof t.plantedAtEpochMillis === "number" &&
      time(t.plantedAtEpochMillis) && time(t.acknowledgedAtEpochMillis));
}

/** Native Room is the sole source of progress, tree kind, slot, and acknowledgment timestamps. */
export class NativeForestBridge implements ForestCommands {
  private readonly pending = new Map<string, Pending>();
  private readonly listeners = new Set<(snapshot: ForestSnapshot) => void>();
  private readonly session = globalThis.crypto.randomUUID();
  private nextId = 0;
  private revision = -1;
  private latest: ForestSnapshot | null = null;
  private closed = false;
  onBack: (() => void) | null = null;

  constructor(private readonly transport: ForestTransport, private readonly timeoutMs = 10_000) {}
  subscribe(listener: (snapshot: ForestSnapshot) => void): () => void {
    this.listeners.add(listener);
    return () => { this.listeners.delete(listener); };
  }
  ready = () => this.request("forest.ready");
  completeOnboarding = () => this.request("forest.completeOnboarding");
  acknowledgeTree = (growthCycle: number) => this.request("forest.acknowledgeTree", { growthCycle });
  back = (): void => {
    if (!this.closed) this.transport.postMessage(JSON.stringify({ version: 1, type: "forest.back", requestId: this.id() }));
  };
  private id(): string { return `${this.session}:${++this.nextId}`; }
  private request(type: string, payload = {}): Promise<ForestSnapshot> {
    if (this.closed) return Promise.reject(new Error("Bridge closed"));
    const requestId = this.id();
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(requestId);
        reject(new Error("Native request timed out"));
      }, this.timeoutMs);
      this.pending.set(requestId, { resolve, reject, timer });
      try { this.transport.postMessage(JSON.stringify({ version: 1, type, requestId, ...payload })); }
      catch (error) {
        clearTimeout(timer);
        this.pending.delete(requestId);
        reject(error);
      }
    });
  }
  receive = (input: unknown): void => {
    if (this.closed || !input || typeof input !== "object") return;
    const m = input as Record<string, unknown>;
    if (m.version !== 1 || typeof m.requestId !== "string") return;
    if (m.type === "forest.navigateBack") { this.onBack?.(); return; }
    if (m.type !== "forest.result" && m.type !== "forest.snapshot") return;
    const pending = this.pending.get(m.requestId);
    if (!pending && m.requestId !== "update") return;
    if (m.type === "forest.result" && m.success === false) {
      if (pending) { clearTimeout(pending.timer); this.pending.delete(m.requestId); pending.reject(new Error(String(m.error))); }
      return;
    }
    if (!isSnapshot(m.payload) || typeof m.revision !== "number" || !Number.isSafeInteger(m.revision)) return;
    if (m.revision > this.revision) {
      this.revision = m.revision;
      this.latest = m.payload;
      this.listeners.forEach(listener => listener(m.payload as ForestSnapshot));
    }
    if (pending && this.latest) {
      clearTimeout(pending.timer);
      this.pending.delete(m.requestId);
      pending.resolve(this.latest);
    }
  };
  dispose(): void {
    this.closed = true;
    this.pending.forEach(p => { clearTimeout(p.timer); p.reject(new Error("Bridge closed")); });
    this.pending.clear();
    this.listeners.clear();
    this.onBack = null;
  }
}

declare global {
  interface Window {
    RingoutForestNative?: ForestTransport;
    webkit?: { messageHandlers?: { RingoutForestNative?: ForestTransport } };
    RingoutForestBridge?: { receive(message: unknown): void };
  }
}
export function nativeTransport(): ForestTransport | null {
  return window.RingoutForestNative ?? window.webkit?.messageHandlers?.RingoutForestNative ?? null;
}
