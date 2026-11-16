import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Group, type Scene } from "three";
import { ForestRenderer } from "../src/forest/ForestRenderer";
import { createFlowSnapshot } from "../src/preview/flowScenarios";
import type { ForestModels } from "../src/forest/assets";

const fake = vi.hoisted(() => ({
  render: vi.fn(),
  load: vi.fn(),
  canvas: null as HTMLCanvasElement | null,
}));
vi.mock("three", async (importOriginal) => {
  const actual = await importOriginal<typeof import("three")>();
  return {
    ...actual,
    WebGLRenderer: class {
      domElement = Object.assign(new EventTarget(), {
        tabIndex: 0,
        setAttribute: vi.fn(),
        remove: vi.fn(),
      }) as unknown as HTMLCanvasElement;
      shadowMap = { enabled: false, type: 0 };
      render = fake.render;
      constructor() { fake.canvas = this.domElement; }
      setPixelRatio() {}
      setClearColor() {}
      setSize() {}
      dispose() {}
      forceContextLoss() {}
    },
  };
});
vi.mock("three/addons/controls/OrbitControls.js", async () => {
  const { Vector3 } = await import("three");
  return {
    OrbitControls: class {
      target = new Vector3();
      update() { return false; }
      addEventListener() {}
      getAzimuthalAngle() { return 0; }
      getPolarAngle() { return 0; }
      dispose() {}
    },
  };
});
vi.mock("../src/forest/assets", () => ({ loadForestModels: fake.load, disposeModels: vi.fn() }));

let frames: Map<number, FrameRequestCallback>;
let nextFrame: number;
let page: EventTarget & { hidden: boolean };
let media: EventTarget & { matches: boolean };
let intersection: IntersectionObserverCallback;
let resize: ResizeObserverCallback;
const renderers: ForestRenderer[] = [];
const tree = createFlowSnapshot("pine").trees[0]!;
const request = { growthCycle: tree.growthCycle, attemptId: 1 };
const models = (): ForestModels => ({ ground: new Group(), pine: new Group(), oak: new Group(), big_tree: new Group() });

beforeEach(() => {
  frames = new Map();
  nextFrame = 0;
  fake.render.mockReset();
  fake.load.mockReset().mockResolvedValue(models());
  page = Object.assign(new EventTarget(), { hidden: false });
  media = Object.assign(new EventTarget(), { matches: false });
  vi.stubGlobal("document", page);
  vi.stubGlobal("window", { devicePixelRatio: 1, matchMedia: () => media });
  vi.stubGlobal("requestAnimationFrame", (callback: FrameRequestCallback) => {
    const id = ++nextFrame;
    frames.set(id, callback);
    return id;
  });
  vi.stubGlobal("cancelAnimationFrame", (id: number) => frames.delete(id));
  vi.stubGlobal("ResizeObserver", class {
    constructor(callback: ResizeObserverCallback) { resize = callback; }
    observe() {}
    disconnect() {}
  });
  vi.stubGlobal("IntersectionObserver", class {
    constructor(callback: IntersectionObserverCallback) { intersection = callback; }
    observe() {}
    disconnect() {}
  });
});
afterEach(() => {
  renderers.splice(0).forEach(renderer => renderer.dispose());
  vi.unstubAllGlobals();
});

function frame(time: number): void {
  const queued = [...frames.values()];
  frames.clear();
  queued.forEach(callback => callback(time));
}
function host() {
  return { dataset: {}, append: vi.fn(), getBoundingClientRect: vi.fn(() => ({ width: 400, height: 400 })) } as unknown as HTMLDivElement;
}
function fixture() {
  const complete = vi.fn();
  const error = vi.fn();
  const status = vi.fn();
  const container = host();
  const renderer = new ForestRenderer(container, status, vi.fn(), complete, error);
  renderers.push(renderer);
  renderer.setTrees([tree]);
  renderer.setRevealRequest(request);
  return { renderer, complete, error, status, container };
}

describe("나무 연출의 실제 프레임 완료", () => {
  it("최종 프레임을 그린 뒤에만 한번 완료하며 동일 요청은 시간을 초기화하지 않는다", async () => {
    const f = fixture();
    await Promise.resolve();
    frame(0);
    frame(325);
    f.renderer.setRevealRequest({ ...request });
    frame(649);
    expect(f.complete).not.toHaveBeenCalled();
    frame(650);
    expect(f.complete).toHaveBeenCalledExactlyOnceWith(request);
    const lastRender = fake.render.mock.calls.at(-1)!;
    expect((lastRender[0] as Scene).getObjectByName(`tree-${tree.growthCycle}`)!.scale.x).toBe(1);
    expect(fake.render.mock.invocationCallOrder.at(-1)).toBeLessThan(f.complete.mock.invocationCallOrder[0]!);
    f.renderer.zoomBy(1.1);
    frame(1_000);
    expect(f.complete).toHaveBeenCalledTimes(1);
  });

  it("앱이 가려진 시간은 누적하지 않고 복귀 후 남은 시간만 연출한다", async () => {
    const f = fixture();
    await Promise.resolve();
    frame(0); frame(250);
    f.renderer.setHostActive(false);
    frame(10_000);
    expect(f.complete).not.toHaveBeenCalled();
    f.renderer.setHostActive(true);
    frame(10_000); frame(10_399);
    expect(f.complete).not.toHaveBeenCalled();
    frame(10_400);
    expect(f.complete).toHaveBeenCalledOnce();
  });

  it("문서 숨김과 화면 밖 상태에서도 연출 시간을 멈춘다", async () => {
    const f = fixture();
    await Promise.resolve();
    frame(0); frame(200);
    page.hidden = true;
    page.dispatchEvent(new Event("visibilitychange"));
    frame(5_000);
    page.hidden = false;
    page.dispatchEvent(new Event("visibilitychange"));
    frame(5_000); frame(5_200);
    intersection([{ isIntersecting: false } as IntersectionObserverEntry], {} as IntersectionObserver);
    frame(20_000);
    intersection([{ isIntersecting: true } as IntersectionObserverEntry], {} as IntersectionObserver);
    frame(20_000); frame(20_249);
    expect(f.complete).not.toHaveBeenCalled();
    frame(20_250);
    expect(f.complete).toHaveBeenCalledOnce();
  });

  it("모델이 늦게 준비되면 준비 이후 처음 보이는 프레임부터 시간을 계산한다", async () => {
    let loaded!: (value: ForestModels) => void;
    fake.load.mockImplementation(() => new Promise<ForestModels>(resolve => { loaded = resolve; }));
    const f = fixture();
    frame(0); frame(5_000);
    expect(f.complete).not.toHaveBeenCalled();
    loaded(models());
    await Promise.resolve();
    frame(5_000); frame(5_649);
    expect(f.complete).not.toHaveBeenCalled();
    frame(5_650);
    expect(f.complete).toHaveBeenCalledOnce();
  });

  it("대상 나무나 표시 크기가 없으면 완료하지 않는다", async () => {
    const f = fixture();
    f.renderer.setTrees([]);
    await Promise.resolve();
    frame(0); frame(5_000);
    expect(f.complete).not.toHaveBeenCalled();
    vi.mocked(f.container.getBoundingClientRect).mockReturnValue({ width: 0, height: 0 } as DOMRect);
    resize([], {} as ResizeObserver);
    f.renderer.setTrees([tree]);
    frame(10_000);
    expect(f.complete).not.toHaveBeenCalled();
    vi.mocked(f.container.getBoundingClientRect).mockReturnValue({ width: 400, height: 400 } as DOMRect);
    resize([], {} as ResizeObserver);
    frame(10_000); frame(10_650);
    expect(f.complete).toHaveBeenCalledOnce();
  });

  it("모션 감소도 활성 화면의 최종 렌더 이후에 완료한다", async () => {
    media.matches = true;
    const f = fixture();
    f.renderer.setHostActive(false);
    await Promise.resolve();
    frame(0);
    expect(f.complete).not.toHaveBeenCalled();
    f.renderer.setHostActive(true);
    frame(1_000);
    expect(f.complete).toHaveBeenCalledExactlyOnceWith(request);
    expect(fake.render.mock.invocationCallOrder.at(-1)).toBeLessThan(f.complete.mock.invocationCallOrder[0]!);
  });

  it("최종 렌더가 실패하면 확인 완료 대신 오류를 한 번 알린다", async () => {
    const f = fixture();
    await Promise.resolve();
    frame(0);
    fake.render.mockImplementationOnce(() => { throw new Error("lost"); });
    frame(650); frame(1_000);
    expect(f.complete).not.toHaveBeenCalled();
    expect(f.error).toHaveBeenCalledExactlyOnceWith(request);
    expect(f.status).toHaveBeenLastCalledWith(expect.objectContaining({ phase: "error" }));
  });

  it("컨텍스트 손실과 폐기 후 늦은 프레임은 완료를 알리지 않는다", async () => {
    const f = fixture();
    await Promise.resolve();
    frame(0);
    const delayed = [...frames.values()];
    fake.canvas!.dispatchEvent(new Event("webglcontextlost", { cancelable: true }));
    expect(f.error).toHaveBeenCalledExactlyOnceWith(request);
    f.renderer.dispose();
    delayed.forEach(callback => callback(650));
    expect(f.complete).not.toHaveBeenCalled();
    expect(f.error).toHaveBeenCalledTimes(1);
  });

  it("모델 로딩 실패는 연출 대기를 실패시키며 완료하지 않는다", async () => {
    fake.load.mockRejectedValue(new Error("offline"));
    const f = fixture();
    await Promise.resolve();
    frame(650);
    expect(f.error).toHaveBeenCalledExactlyOnceWith(request);
    expect(f.complete).not.toHaveBeenCalled();
  });

  it("새 시도가 시작되면 이전 시도의 누적 시간을 사용하지 않는다", async () => {
    const f = fixture();
    await Promise.resolve();
    frame(0); frame(600);
    const retry = { ...request, attemptId: 2 };
    f.renderer.setRevealRequest(retry);
    frame(650); frame(1_299);
    expect(f.complete).not.toHaveBeenCalled();
    frame(1_300);
    expect(f.complete).toHaveBeenCalledExactlyOnceWith(retry);
  });
});
