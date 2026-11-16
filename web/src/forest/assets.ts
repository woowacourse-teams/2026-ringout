import { Box3, Group, Mesh, Vector3 } from "three";
import type { Object3D } from "three";
import { GLTFLoader } from "three/addons/loaders/GLTFLoader.js";
import type { TreeKind } from "../bridge/protocol";
import { GROUND_WIDTH } from "./layout";

export const MODEL_URLS = {
  ground: `${import.meta.env.BASE_URL}models/ground.glb`,
  pine: `${import.meta.env.BASE_URL}models/pine.glb`,
  oak: `${import.meta.env.BASE_URL}models/oak.glb`,
  big_tree: `${import.meta.env.BASE_URL}models/big-tree.glb`,
} as const;

export type ForestModels = Record<keyof typeof MODEL_URLS, Group>;

/** Shared geometry/materials are disposed once, including templates not used in the current sample. */
export function disposeModels(roots: readonly Object3D[]): void {
  const geometries = new Set<Mesh["geometry"]>();
  const materials = new Set<import("three").Material>();
  for (const root of roots)
    root.traverse((object) => {
      if (object instanceof Mesh) {
        geometries.add(object.geometry);
        for (const material of Array.isArray(object.material)
          ? object.material
          : [object.material])
          materials.add(material);
      }
    });
  geometries.forEach((geometry) => geometry.dispose());
  materials.forEach((material) => material.dispose());
}

export async function loadForestModels(
  signal: AbortSignal,
): Promise<ForestModels> {
  const loader = new GLTFLoader();
  const entries = Object.entries(MODEL_URLS) as [keyof ForestModels, string][];
  const results = await Promise.allSettled(
    entries.map(async ([key, url]) => {
      const response = await fetch(url, { signal });
      if (!response.ok)
        throw new Error(
          `${key} 모델을 불러오지 못했습니다 (${response.status}).`,
        );
      const { scene } = await loader.parseAsync(
        await response.arrayBuffer(),
        "",
      );
      return [key, scene] as const;
    }),
  );
  const loaded = results.flatMap((result) =>
    result.status === "fulfilled" ? [result.value] : [],
  );
  const failure = results.find((result) => result.status === "rejected");
  if (signal.aborted || failure) {
    disposeModels(loaded.map(([, model]) => model));
    if (failure?.status === "rejected") throw failure.reason;
    throw new DOMException("모델 로딩이 취소되었습니다.", "AbortError");
  }
  const models = Object.fromEntries(loaded) as ForestModels;
  const groundBounds = new Box3().setFromObject(models.ground, true);
  const groundSize = groundBounds.getSize(new Vector3());
  const scale = GROUND_WIDTH / Math.max(groundSize.x, groundSize.z);
  models.ground.scale.setScalar(scale);
  const scaled = new Box3().setFromObject(models.ground, true);
  const center = scaled.getCenter(new Vector3());
  models.ground.position.set(-center.x, -scaled.max.y, -center.z);

  const dimensions: Record<TreeKind, { height: number; width: number }> = {
    pine: { height: 1.8, width: 1.08 },
    oak: { height: 1.45, width: 1.28 },
    big_tree: { height: 2.0, width: 1.0 },
  };
  for (const kind of ["pine", "oak", "big_tree"] as const) {
    const model = models[kind];
    const box = new Box3().setFromObject(model, true);
    const size = box.getSize(new Vector3());
    const target = dimensions[kind];
    model.scale.setScalar(
      Math.min(target.height / size.y, target.width / Math.max(size.x, size.z)),
    );
    const scaledBox = new Box3().setFromObject(model, true);
    const scaledCenter = scaledBox.getCenter(new Vector3());
    model.position.set(-scaledCenter.x, -scaledBox.min.y, -scaledCenter.z);
  }
  for (const [key, model] of Object.entries(models))
    model.traverse((object) => {
      if (object instanceof Mesh) {
        object.castShadow = key !== "ground";
        object.receiveShadow = true;
      }
    });
  return models;
}
