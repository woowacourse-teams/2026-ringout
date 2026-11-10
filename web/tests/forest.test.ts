import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Box3, BufferGeometry, Mesh, MeshBasicMaterial, Vector3 } from "three";
import type { TreeKind } from "../src/bridge/protocol";
import { disposeModels, loadForestModels } from "../src/forest/assets";
import { slotPosition, validatePlacements } from "../src/forest/layout";
import { createSampleForest } from "../src/preview/fixtures";

afterEach(() => vi.unstubAllGlobals());

describe("숲 배치", () => {
  it(`16칸을 중복 없이 원본 땅의 행과 열 방향에 맞춘다`, () => {
    const positions = Array.from({ length: 16 }, (_, index) =>
      slotPosition(index),
    );
    expect(new Set(positions.map(({ x, z }) => `${x},${z}`)).size).toBe(16);
    expect(positions[0]).toEqual({ x: -1.5, z: 1.5 });
    expect(positions[3]).toEqual({ x: 1.5, z: 1.5 });
    expect(positions[12]).toEqual({ x: -1.5, z: -1.5 });
    expect(positions[15]).toEqual({ x: 1.5, z: -1.5 });
  });

  it.each([-1, 16, 0.5, NaN])(`유효하지 않은 칸 번호 %s를 거부한다`, (slot) => {
    expect(() => slotPosition(slot)).toThrow();
  });

  it(`칸 또는 획득 회차가 겹치거나 종류가 잘못된 배치를 거부한다`, () => {
    const trees = createSampleForest(2).trees;
    expect(() =>
      validatePlacements([
        trees[0],
        { ...trees[1], slotIndex: trees[0].slotIndex },
      ]),
    ).toThrow();
    expect(() =>
      validatePlacements([trees[0], { ...trees[1], growthCycle: 1 }]),
    ).toThrow();
    expect(() =>
      validatePlacements([{ ...trees[0], growthCycle: 0 }]),
    ).toThrow();
    expect(() =>
      validatePlacements([{ ...trees[0], kind: "sunleaf" as TreeKind }]),
    ).toThrow();
    expect(() =>
      validatePlacements(Array.from({ length: 17 }, () => trees[0])),
    ).toThrow();
  });

  it.each([0, 1, 8, 16])(
    `%s그루 샘플이 유효하며 기존 나무 위치를 유지한다`,
    (count) => {
      const sample = createSampleForest(count);
      expect(sample.trees).toHaveLength(count);
      expect(sample.trees).toEqual(
        createSampleForest(16).trees.slice(0, count),
      );
      expect(() => validatePlacements(sample.trees)).not.toThrow();
      expect(sample.growth.isComplete).toBe(count === 16);
      expect(sample.growth.hasGrowingSeed).toBe(count !== 16);
    },
  );

  it(`빅트리 단독 샘플은 모든 칸에 빅트리를 사용한다`, () => {
    expect(
      createSampleForest(16, "big_tree").trees.every(
        (tree) => tree.kind === "big_tree",
      ),
    ).toBe(true);
  });
});

async function modelResponse(url: string): Promise<Response> {
  const file = resolve("public", url.replace(/^\.\//, "").replace(/^\//, ""));
  const buffer = await readFile(file);
  return new Response(new Uint8Array(buffer));
}

describe("실제 GLB 모델", () => {
  it(`땅의 윗면과 나무 밑면을 맞추고 원본 비율을 유지한다`, async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string) => modelResponse(url)),
    );
    const models = await loadForestModels(new AbortController().signal);
    try {
      const ground = new Box3().setFromObject(models.ground, true);
      expect(ground.max.y).toBeCloseTo(0);
      expect(ground.getSize(new Vector3()).x).toBeCloseTo(4);
      expect(ground.getSize(new Vector3()).z).toBeCloseTo(4);
      for (const kind of ["pine", "oak", "big_tree"] as const) {
        const model = models[kind];
        const bounds = new Box3().setFromObject(model, true);
        expect(bounds.min.y).toBeCloseTo(0);
        expect(bounds.getCenter(new Vector3()).x).toBeCloseTo(0);
        expect(bounds.getCenter(new Vector3()).z).toBeCloseTo(0);
        expect(bounds.getSize(new Vector3()).y).toBeGreaterThan(1);
        expect(bounds.getSize(new Vector3()).y).toBeLessThanOrEqual(2.001);
        expect(model.scale.x).toBe(model.scale.y);
        expect(model.scale.y).toBe(model.scale.z);
      }
    } finally {
      disposeModels(Object.values(models));
    }
  });

  it(`모델 하나가 누락되면 성공으로 처리하지 않는다`, async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string) =>
        url.includes("oak")
          ? Promise.resolve(new Response(null, { status: 404 }))
          : modelResponse(url),
      ),
    );
    await expect(
      loadForestModels(new AbortController().signal),
    ).rejects.toThrow("oak");
  });

  it(`로딩 도중 취소되면 결과를 화면에 넘기지 않는다`, async () => {
    const controller = new AbortController();
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) => {
        controller.abort();
        return modelResponse(url);
      }),
    );
    await expect(loadForestModels(controller.signal)).rejects.toMatchObject({
      name: "AbortError",
    });
  });

  it(`복제한 나무가 공유하는 GPU 자원을 한 번만 해제한다`, () => {
    const geometry = new BufferGeometry();
    const material = new MeshBasicMaterial();
    const tree = new Mesh(geometry, material);
    const geometryDispose = vi.spyOn(geometry, "dispose");
    const materialDispose = vi.spyOn(material, "dispose");
    disposeModels([tree, tree.clone(), tree.clone()]);
    expect(geometryDispose).toHaveBeenCalledTimes(1);
    expect(materialDispose).toHaveBeenCalledTimes(1);
  });
});
