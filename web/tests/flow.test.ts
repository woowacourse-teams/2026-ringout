import { describe, expect, it, vi } from "vitest";
import {
  ForestController,
  pendingTree,
  visibleTrees,
} from "../src/flow/ForestController";
import {
  createDemoCommands,
  createFlowSnapshot,
} from "../src/preview/flowScenarios";
import type { ForestSnapshot } from "../src/bridge/protocol";

async function finishPlant(controller: ForestController): Promise<void> {
  const planting = controller.plant();
  const request = controller.getSnapshot().revealRequest;
  if (request) controller.completeReveal(request);
  await planting;
}


describe("온보딩과 성장 상태", () => {
  it(`처음 입장하면 두 안내를 거치고 저장 성공 후 숲에 들어간다`, async () => {
    const snapshot = createFlowSnapshot("first");
    const commands = createDemoCommands(snapshot, "first", vi.fn());
    const controller = new ForestController(snapshot, commands);
    expect(controller.getSnapshot().page).toBe("start");
    controller.nextGuide();
    expect(controller.getSnapshot().page).toBe("guide");
    expect(
      controller.getSnapshot().snapshot.onboardingCompletedAtEpochMillis,
    ).toBeNull();
    await controller.completeOnboarding();
    expect(controller.getSnapshot().page).toBe("forest");
    expect(
      controller.getSnapshot().snapshot.onboardingCompletedAtEpochMillis,
    ).not.toBeNull();
    expect(
      new ForestController(
        controller.getSnapshot().snapshot,
        commands,
      ).getSnapshot().page,
    ).toBe("forest");
  });
  it(`온보딩 저장에 실패하면 화면을 유지하며 재시도할 수 있다`, async () => {
    const snapshot = createFlowSnapshot("start-error");
    const controller = new ForestController(
      snapshot,
      createDemoCommands(snapshot, "start-error", vi.fn()),
    );
    controller.nextGuide();
    await controller.completeOnboarding();
    expect(controller.getSnapshot().page).toBe("guide");
    expect(controller.getSnapshot().error).not.toBeNull();
    expect(
      controller.getSnapshot().snapshot.onboardingCompletedAtEpochMillis,
    ).toBeNull();
    await controller.completeOnboarding();
    expect(controller.getSnapshot().page).toBe("forest");
    expect(controller.getSnapshot().error).toBeNull();
  });
  it(`안내 이전 또는 저장 대기 중에는 온보딩 완료를 중복 요청하지 않는다`, async () => {
    const snapshot = createFlowSnapshot("first");
    let finish!: (value: ForestSnapshot) => void;
    const completeOnboarding = vi.fn(
      () =>
        new Promise<ForestSnapshot>((resolve) => {
          finish = resolve;
        }),
    );
    const controller = new ForestController(snapshot, {
      ...createDemoCommands(snapshot, "first", vi.fn()),
      completeOnboarding,
    });
    await controller.completeOnboarding();
    expect(completeOnboarding).not.toHaveBeenCalled();
    controller.nextGuide();
    const first = controller.completeOnboarding();
    await controller.completeOnboarding();
    expect(completeOnboarding).toHaveBeenCalledTimes(1);
    expect(controller.getSnapshot().page).toBe("guide");
    finish({ ...snapshot, onboardingCompletedAtEpochMillis: 123 });
    await first;
    expect(controller.getSnapshot().page).toBe("forest");
  });
  it(`온보딩이 미완료이면 미확인 나무가 있어도 안내부터 시작한다`, () => {
    const snapshot = {
      ...createFlowSnapshot("backlog"),
      onboardingCompletedAtEpochMillis: null,
    };
    const controller = new ForestController(
      snapshot,
      createDemoCommands(snapshot, "backlog", vi.fn()),
    );
    expect(controller.getSnapshot().page).toBe("start");
  });
  it(`성장 시트에서 뒤로 가면 시트만 닫고 다음 뒤로가기는 호스트에 전달한다`, () => {
    const back = vi.fn();
    const snapshot = createFlowSnapshot("growing");
    const controller = new ForestController(
      snapshot,
      createDemoCommands(snapshot, "growing", back),
    );
    controller.openSheet();
    expect(controller.getSnapshot().sheetOpen).toBe(true);
    controller.back();
    expect(controller.getSnapshot().sheetOpen).toBe(false);
    expect(back).not.toHaveBeenCalled();
    controller.back();
    expect(back).toHaveBeenCalledTimes(1);
    expect(controller.getSnapshot().snapshot.growth).toMatchObject({
      filledSlots: 4,
      remainingDays: 1,
    });
  });
  it(`온보딩 두 번째 화면에서 뒤로 가면 첫 안내로 돌아간다`, () => {
    const snapshot = createFlowSnapshot("first");
    const back = vi.fn();
    const controller = new ForestController(
      snapshot,
      createDemoCommands(snapshot, "first", back),
    );
    controller.nextGuide();
    controller.back();
    expect(controller.getSnapshot().page).toBe("start");
    expect(back).not.toHaveBeenCalled();
  });
});

describe("나무 완성 안내", () => {
  it(`정렬되지 않은 미확인 나무도 획득 회차순으로 하나씩 확인한다`, async () => {
    const snapshot = createFlowSnapshot("backlog");
    snapshot.trees.reverse();
    const original = structuredClone(snapshot.trees);
    const controller = new ForestController(
      snapshot,
      createDemoCommands(snapshot, "backlog", vi.fn()),
    );
    expect(visibleTrees(controller.getSnapshot())).toHaveLength(0);
    for (const cycle of [1, 2, 3]) {
      expect(pendingTree(controller.getSnapshot().snapshot)?.growthCycle).toBe(
        cycle,
      );
      await finishPlant(controller);
      expect(visibleTrees(controller.getSnapshot())).toHaveLength(cycle);
    }
    expect(pendingTree(controller.getSnapshot().snapshot)).toBeUndefined();
    expect(
      controller
        .getSnapshot()
        .snapshot.trees.map(
          ({ acknowledgedAtEpochMillis: _, ...tree }) => tree,
        ),
    ).toEqual(
      original.map(({ acknowledgedAtEpochMillis: _, ...tree }) => tree),
    );
  });
  it(`심기 연출을 마친 뒤 확인을 저장하며 연타로 중복 저장하지 않는다`, async () => {
    const snapshot = createFlowSnapshot("pine");
    const commands = createDemoCommands(snapshot, "pine", vi.fn());
    const ack = vi.spyOn(commands, "acknowledgeTree");
    const controller = new ForestController(snapshot, commands);
    const planting = controller.plant();
    await controller.plant();
    expect(controller.getSnapshot().plantingCycle).toBe(1);
    expect(visibleTrees(controller.getSnapshot())).toHaveLength(1);
    expect(ack).not.toHaveBeenCalled();
    controller.back();
    expect(commands.back).not.toHaveBeenCalled();
    controller.completeReveal(controller.getSnapshot().revealRequest!);
    await planting;
    expect(ack).toHaveBeenCalledExactlyOnceWith(1);
    expect(controller.getSnapshot().snapshot.growth.remainingDays).toBe(5);
  });
  it(`확인 저장 실패 후에도 같은 나무와 위치로 재시도한다`, async () => {
    const snapshot = createFlowSnapshot("tree-error");
    const controller = new ForestController(
      snapshot,
      createDemoCommands(snapshot, "tree-error", vi.fn()),
    );
    await finishPlant(controller);
    expect(controller.getSnapshot().error).not.toBeNull();
    expect(controller.getSnapshot().snapshot.trees).toEqual(snapshot.trees);
    expect(pendingTree(controller.getSnapshot().snapshot)?.growthCycle).toBe(1);
    await finishPlant(controller);
    expect(controller.getSnapshot().error).toBeNull();
    expect(controller.getSnapshot().snapshot.trees).toHaveLength(1);
    expect(controller.getSnapshot().snapshot.trees[0]).toMatchObject({
      kind: snapshot.trees[0].kind,
      slotIndex: snapshot.trees[0].slotIndex,
    });
  });
  it(`확인 응답에 저장 결과가 없으면 완료로 처리하지 않는다`, async () => {
    const snapshot = createFlowSnapshot("oak");
    const commands = createDemoCommands(snapshot, "oak", vi.fn());
    commands.acknowledgeTree = async () => snapshot;
    const controller = new ForestController(snapshot, commands);
    await finishPlant(controller);
    expect(controller.getSnapshot().error).not.toBeNull();
    expect(pendingTree(controller.getSnapshot().snapshot)).toBeDefined();
  });
  it(`16번째 나무를 확인하면 새 씨앗이나 성장 시트를 표시하지 않는다`, async () => {
    const snapshot = createFlowSnapshot("full");
    const controller = new ForestController(
      snapshot,
      createDemoCommands(snapshot, "full", vi.fn()),
    );
    expect(pendingTree(snapshot)?.growthCycle).toBe(16);
    await finishPlant(controller);
    controller.openSheet();
    expect(visibleTrees(controller.getSnapshot())).toHaveLength(16);
    expect(controller.getSnapshot().snapshot.growth).toMatchObject({
      hasGrowingSeed: false,
      remainingDays: 0,
      isComplete: true,
    });
    expect(controller.getSnapshot().sheetOpen).toBe(false);
  });
  it(`이미 확인한 나무를 다시 심거나 없는 나무를 만들지 않는다`, async () => {
    const snapshot = createFlowSnapshot("last-seed");
    const commands = createDemoCommands(snapshot, "last-seed", vi.fn());
    const ack = vi.spyOn(commands, "acknowledgeTree");
    const controller = new ForestController(snapshot, commands);
    await finishPlant(controller);
    expect(ack).not.toHaveBeenCalled();
    await expect(commands.acknowledgeTree(16)).rejects.toThrow();
  });
});


describe("렌더러 연출 확인 수명", () => {
  it("연출 실패는 확인 요청을 보내지 않고 새 시도로만 재시도한다", async () => {
    const snapshot = createFlowSnapshot("pine");
    const commands = createDemoCommands(snapshot, "pine", vi.fn());
    const ack = vi.spyOn(commands, "acknowledgeTree");
    const controller = new ForestController(snapshot, commands);
    const first = controller.plant();
    const oldRequest = controller.getSnapshot().revealRequest!;
    controller.failReveal(oldRequest);
    await first;
    expect(ack).not.toHaveBeenCalled();
    expect(controller.getSnapshot().busy).toBe(false);
    expect(controller.getSnapshot().error).toContain("연출");
    const retry = controller.plant();
    const current = controller.getSnapshot().revealRequest!;
    expect(current.attemptId).not.toBe(oldRequest.attemptId);
    controller.completeReveal(oldRequest);
    controller.failReveal(oldRequest);
    expect(ack).not.toHaveBeenCalled();
    controller.completeReveal(current);
    controller.completeReveal(current);
    await retry;
    expect(ack).toHaveBeenCalledExactlyOnceWith(1);
  });
  it("페이지가 끝나면 대기 연출을 취소하고 늦은 완료는 저장하지 않는다", async () => {
    const snapshot = createFlowSnapshot("pine");
    const commands = createDemoCommands(snapshot, "pine", vi.fn());
    const ack = vi.spyOn(commands, "acknowledgeTree");
    const controller = new ForestController(snapshot, commands);
    const planting = controller.plant();
    const request = controller.getSnapshot().revealRequest!;
    controller.dispose();
    controller.completeReveal(request);
    await planting;
    await controller.plant();
    expect(ack).not.toHaveBeenCalled();
  });
  it("최종 프레임 직후 페이지가 끝나도 확인 명령을 새로 시작하지 않는다", async () => {
    const snapshot = createFlowSnapshot("pine");
    const commands = createDemoCommands(snapshot, "pine", vi.fn());
    const ack = vi.spyOn(commands, "acknowledgeTree");
    const controller = new ForestController(snapshot, commands);
    const planting = controller.plant();
    controller.completeReveal(controller.getSnapshot().revealRequest!);
    controller.dispose();
    await planting;
    expect(ack).not.toHaveBeenCalled();
  });
  it("다른 회차 완료 신호와 전송 중 중복 완료 신호는 무시한다", async () => {
    const snapshot = createFlowSnapshot("pine");
    const commands = createDemoCommands(snapshot, "pine", vi.fn());
    let respond!: (value: ForestSnapshot) => void;
    const ack = vi.spyOn(commands, "acknowledgeTree").mockImplementation(() => new Promise(resolve => { respond = resolve; }));
    const controller = new ForestController(snapshot, commands);
    const planting = controller.plant();
    const request = controller.getSnapshot().revealRequest!;
    controller.completeReveal({ ...request, growthCycle: 2 });
    expect(ack).not.toHaveBeenCalled();
    controller.completeReveal(request);
    await Promise.resolve();
    controller.completeReveal(request);
    controller.failReveal(request);
    expect(ack).toHaveBeenCalledTimes(1);
    respond({ ...snapshot, trees: snapshot.trees.map(tree => ({ ...tree, acknowledgedAtEpochMillis: 42 })) });
    await planting;
    expect(controller.getSnapshot().error).toBeNull();
  });
  it("앱 활성 상태 변경은 연출 시도를 바꾸거나 확인을 보내지 않는다", async () => {
    const snapshot = createFlowSnapshot("pine");
    const commands = createDemoCommands(snapshot, "pine", vi.fn());
    const ack = vi.spyOn(commands, "acknowledgeTree");
    const controller = new ForestController(snapshot, commands);
    const planting = controller.plant();
    const request = controller.getSnapshot().revealRequest;
    controller.setHostActive(false);
    expect(controller.getSnapshot().hostActive).toBe(false);
    controller.setHostActive(true);
    expect(controller.getSnapshot().revealRequest).toBe(request);
    expect(ack).not.toHaveBeenCalled();
    controller.dispose();
    await planting;
  });
});
