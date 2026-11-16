import { afterEach, describe, expect, it, vi } from "vitest";
import { NativeForestBridge } from "../src/bridge/NativeForestBridge";
import { ForestController } from "../src/flow/ForestController";
import { createFlowSnapshot } from "../src/preview/flowScenarios";

function fixture() {
  const requests: Array<Record<string, unknown>> = [];
  const bridge = new NativeForestBridge({ postMessage: raw => requests.push(JSON.parse(raw)) }, 100);
  const reply = (snapshot = createFlowSnapshot("first"), revision = 1, request = requests.at(-1)!) =>
    bridge.receive({ version: 1, type: request.type === "forest.ready" ? "forest.snapshot" : "forest.result", success: true, requestId: request.requestId, payload: snapshot, revision });
  return { bridge, requests, reply };
}
afterEach(() => vi.useRealTimers());
describe("네이티브 숲 브리지", () => {
  it("Room 준비 응답을 기다리고 저장 확인 스냅샷으로만 완료한다", async () => {
    const f = fixture();
    const first = f.bridge.ready();
    f.reply();
    expect((await first).onboardingCompletedAtEpochMillis).toBeNull();
    const saving = f.bridge.completeOnboarding();
    const confirmed = { ...createFlowSnapshot("first"), onboardingCompletedAtEpochMillis: 1234 };
    f.reply(confirmed, 2);
    expect(await saving).toEqual(confirmed);
    expect(f.requests[1]?.type).toBe("forest.completeOnboarding");
    f.bridge.dispose();
  });
  it("늦은 응답이 더 최신 Room 구독 상태를 되돌리지 않는다", async () => {
    const f = fixture();
    const ready = f.bridge.ready(); f.reply(); await ready;
    const saving = f.bridge.completeOnboarding();
    const fresh = { ...createFlowSnapshot("first"), onboardingCompletedAtEpochMillis: 12 };
    f.bridge.receive({ version: 1, type: "forest.snapshot", requestId: "update", revision: 3, payload: fresh });
    f.reply(createFlowSnapshot("first"), 2);
    expect(await saving).toEqual(fresh);
    f.bridge.dispose();
  });
  it("저장 실패와 시간 초과를 성공으로 처리하지 않는다", async () => {
    vi.useFakeTimers();
    const f = fixture();
    const failed = f.bridge.completeOnboarding();
    const failure = expect(failed).rejects.toThrow("storage");
    f.bridge.receive({ version: 1, type: "forest.result", requestId: f.requests[0]!.requestId, success: false, error: "storage" });
    await failure;
    const timed = f.bridge.ready();
    const timeout = expect(timed).rejects.toThrow("timed out");
    await vi.advanceTimersByTimeAsync(101); await timeout;
    f.bridge.dispose();
  });
  it("다른 요청과 버전의 응답 및 잘못된 스냅샷은 무시한다", async () => {
    vi.useFakeTimers();
    const f = fixture();
    const first = f.bridge.ready();
    const timeout = expect(first).rejects.toThrow("timed out");
    f.bridge.receive({ version: 1, type: "forest.snapshot", requestId: "other", revision: 1, payload: createFlowSnapshot("first") });
    f.bridge.receive({ version: 2, type: "forest.snapshot", requestId: f.requests[0]!.requestId, revision: 1, payload: createFlowSnapshot("first") });
    f.bridge.receive({ version: 1, type: "forest.snapshot", requestId: f.requests[0]!.requestId, revision: 1, payload: {} });
    await vi.advanceTimersByTimeAsync(101); await timeout;
    f.bridge.dispose();
  });
  it("해제하면 대기 요청을 취소하고 늦은 업데이트를 전달하지 않는다", async () => {
    const f = fixture(); const listener = vi.fn(); f.bridge.subscribe(listener);
    const waiting = f.bridge.ready(); const cancelled = expect(waiting).rejects.toThrow("closed");
    f.bridge.dispose(); await cancelled;
    f.reply(); expect(listener).not.toHaveBeenCalled();
  });
  it("시스템 뒤로 가기는 바텀 시트를 먼저 닫고 이후 홈 이동을 요청한다", async () => {
    const f = fixture();
    const snapshot = { ...createFlowSnapshot("first"), onboardingCompletedAtEpochMillis: 1 };
    const controller = new ForestController(snapshot, f.bridge);
    f.bridge.onBack = controller.back;
    controller.openSheet();
    const back = { version: 1, type: "forest.navigateBack", requestId: "native-back" };
    f.bridge.receive(back);
    expect(controller.getSnapshot().sheetOpen).toBe(false);
    expect(f.requests).toHaveLength(0);
    f.bridge.receive(back);
    expect(f.requests[0]?.type).toBe("forest.back");
    f.bridge.dispose();
  });
  it("저장 중 구독 변경은 완료 후 반영해서 화면을 중간에 바꾸지 않는다", async () => {
    const f = fixture();
    const controller = new ForestController(createFlowSnapshot("first"), f.bridge);
    f.bridge.subscribe(controller.receiveSnapshot);
    controller.nextGuide();
    const saving = controller.completeOnboarding();
    const confirmed = { ...createFlowSnapshot("first"), onboardingCompletedAtEpochMillis: 1 };
    f.bridge.receive({ version: 1, type: "forest.snapshot", requestId: "update", revision: 2, payload: confirmed });
    expect(controller.getSnapshot().page).toBe("guide");
    expect(controller.getSnapshot().busy).toBe(true);
    f.reply(confirmed, 3);
    await saving;
    expect(controller.getSnapshot().page).toBe("forest");
    expect(controller.getSnapshot().snapshot).toEqual(confirmed);
    f.bridge.dispose();
  });
});
