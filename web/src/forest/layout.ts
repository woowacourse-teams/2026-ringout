import type { TreeKind } from "../bridge/protocol";

export const GRID_SIZE = 4;
export const TREE_CAPACITY = GRID_SIZE * GRID_SIZE;
export const GROUND_WIDTH = 4;
export const TREE_KINDS: readonly TreeKind[] = ["pine", "oak", "big_tree"];
export const TREE_LABELS: Record<TreeKind, string> = {
  pine: "소나무",
  oak: "참나무",
  big_tree: "빅트리",
};

/** The source ground has row 0 on +Z, column 0 on -X. Preserve the native slot index. */
export function slotPosition(slotIndex: number): { x: number; z: number } {
  if (
    !Number.isInteger(slotIndex) ||
    slotIndex < 0 ||
    slotIndex >= TREE_CAPACITY
  ) {
    throw new Error("나무의 칸 번호는 0~15여야 합니다.");
  }
  return {
    x: (slotIndex % GRID_SIZE) - (GRID_SIZE - 1) / 2,
    z: (GRID_SIZE - 1) / 2 - Math.floor(slotIndex / GRID_SIZE),
  };
}

export function validatePlacements(
  trees: readonly { growthCycle: number; kind: TreeKind; slotIndex: number }[],
): void {
  if (trees.length > TREE_CAPACITY)
    throw new Error("한 숲에는 최대 16그루를 표시할 수 있습니다.");
  const cycles = new Set<number>();
  const slots = new Set<number>();
  for (const tree of trees) {
    slotPosition(tree.slotIndex);
    if (
      !Number.isInteger(tree.growthCycle) ||
      tree.growthCycle < 1 ||
      tree.growthCycle > TREE_CAPACITY
    ) {
      throw new Error("나무 획득 회차가 올바르지 않습니다.");
    }
    if (!TREE_KINDS.includes(tree.kind))
      throw new Error("지원하지 않는 나무 종류입니다.");
    if (cycles.has(tree.growthCycle) || slots.has(tree.slotIndex))
      throw new Error("나무의 회차 또는 위치가 겹칩니다.");
    cycles.add(tree.growthCycle);
    slots.add(tree.slotIndex);
  }
}
