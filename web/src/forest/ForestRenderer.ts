import {
  ACESFilmicToneMapping,
  BufferGeometry,
  Color,
  DirectionalLight,
  Float32BufferAttribute,
  Group,
  HemisphereLight,
  LineBasicMaterial,
  LineSegments,
  MathUtils,
  OrthographicCamera,
  PCFShadowMap,
  Scene,
  Spherical,
  SRGBColorSpace,
  Vector3,
  WebGLRenderer,
} from "three";
import { OrbitControls } from "three/addons/controls/OrbitControls.js";
import type { ForestTree } from "../bridge/protocol";
import { disposeModels, loadForestModels } from "./assets";
import type { ForestModels } from "./assets";
import { slotPosition, validatePlacements } from "./layout";
import { PLANT_DURATION } from "./motion";

export type SceneStatus = {
  phase: "loading" | "ready" | "error";
  message?: string;
};

export class ForestRenderer {
  private readonly renderer: WebGLRenderer;
  private readonly scene = new Scene();
  private readonly camera = new OrthographicCamera(-4, 4, 4, -4, 0.1, 60);
  private readonly controls: OrbitControls;
  private readonly trees = new Group();
  private readonly grid: LineSegments;
  private readonly resizeObserver: ResizeObserver;
  private readonly intersectionObserver: IntersectionObserver;
  private readonly abortController = new AbortController();
  private readonly reducedMotion = window.matchMedia(
    "(prefers-reduced-motion: reduce)",
  );
  private models: ForestModels | null = null;
  private pendingTrees: readonly ForestTree[] = [];
  private frame = 0;
  private disposed = false;
  private visible = true;
  private failed = false;
  private revealCycle: number | null = null;
  private revealStarted = 0;

  constructor(
    private readonly container: HTMLDivElement,
    private readonly onStatus: (status: SceneStatus) => void,
    private readonly onZoom: (zoom: number) => void,
  ) {
    this.renderer = new WebGLRenderer({
      antialias: true,
      alpha: true,
      powerPreference: "low-power",
    });
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    this.renderer.outputColorSpace = SRGBColorSpace;
    this.renderer.toneMapping = ACESFilmicToneMapping;
    this.renderer.toneMappingExposure = 1.2;
    this.renderer.shadowMap.enabled = true;
    this.renderer.shadowMap.type = PCFShadowMap;
    this.renderer.setClearColor(0x000000, 0);
    const canvas = this.renderer.domElement;
    canvas.tabIndex = 0;
    canvas.setAttribute("role", "img");
    canvas.setAttribute("aria-label", "회전하고 확대할 수 있는 3D 숲");
    canvas.setAttribute("aria-describedby", "forest-gestures");
    canvas.setAttribute(
      "aria-keyshortcuts",
      "ArrowLeft ArrowRight ArrowUp ArrowDown + - Home",
    );
    this.container.append(canvas);

    this.controls = new OrbitControls(this.camera, canvas);
    this.controls.target.set(0, 0.25, 0);
    this.controls.enablePan = false;
    this.controls.enableDamping = !this.reducedMotion.matches;
    this.controls.dampingFactor = 0.12;
    this.controls.rotateSpeed = 0.65;
    this.controls.zoomSpeed = 0.8;
    this.controls.minPolarAngle = MathUtils.degToRad(25);
    this.controls.maxPolarAngle = MathUtils.degToRad(72);
    this.controls.minZoom = 0.75;
    this.controls.maxZoom = 1.6;
    this.controls.addEventListener("change", this.invalidate);
    this.controls.addEventListener("start", this.invalidate);
    this.reducedMotion.addEventListener("change", this.updateMotion);
    canvas.addEventListener("keydown", this.onKeyDown);
    canvas.addEventListener("webglcontextlost", this.onContextLost);
    document.addEventListener("visibilitychange", this.onVisibilityChange);

    this.scene.add(
      new HemisphereLight(new Color("#f3f8ff"), new Color("#b2a07e"), 2.1),
    );
    const sunlight = new DirectionalLight("#fff5dc", 3.2);
    sunlight.position.set(-3, 7, 5);
    sunlight.castShadow = true;
    sunlight.shadow.mapSize.set(2048, 2048);
    Object.assign(sunlight.shadow.camera, {
      left: -4.5,
      right: 4.5,
      top: 4.5,
      bottom: -4.5,
      near: 0.1,
      far: 20,
    });
    sunlight.shadow.bias = -0.0003;
    sunlight.shadow.normalBias = 0.025;
    this.scene.add(sunlight, this.trees);

    const vertices: number[] = [];
    for (let slot = 0; slot < 16; slot++) {
      const { x, z } = slotPosition(slot);
      const corners = [
        [x - 0.46, z - 0.46],
        [x + 0.46, z - 0.46],
        [x + 0.46, z + 0.46],
        [x - 0.46, z + 0.46],
      ];
      corners.forEach((corner, index) => {
        vertices.push(
          corner[0],
          0.012,
          corner[1],
          corners[(index + 1) % 4][0],
          0.012,
          corners[(index + 1) % 4][1],
        );
      });
    }
    const geometry = new BufferGeometry();
    geometry.setAttribute("position", new Float32BufferAttribute(vertices, 3));
    this.grid = new LineSegments(
      geometry,
      new LineBasicMaterial({
        color: "#ff6d2e",
        transparent: true,
        opacity: 0.6,
      }),
    );
    this.grid.visible = false;
    this.scene.add(this.grid);
    this.resizeObserver = new ResizeObserver(this.resize);
    this.resizeObserver.observe(container);
    this.intersectionObserver = new IntersectionObserver(([entry]) => {
      this.visible = entry.isIntersecting;
      if (this.visible) this.invalidate();
      else this.stopFrame();
    });
    this.intersectionObserver.observe(container);
    this.resetView();
    this.resize();
    void this.load();
  }

  private async load(): Promise<void> {
    this.onStatus({ phase: "loading" });
    try {
      const models = await loadForestModels(this.abortController.signal);
      if (this.disposed) {
        disposeModels(Object.values(models));
        return;
      }
      this.models = models;
      this.scene.add(models.ground);
      this.setTrees(this.pendingTrees);
      this.container.dataset.modelCount = "4";
      this.onStatus({ phase: "ready" });
      this.invalidate();
    } catch {
      if (this.disposed) return;
      this.failed = true;
      this.stopFrame();
      this.onStatus({
        phase: "error",
        message:
          "모델을 불러오는 중 문제가 생겼어요. 잠시 후 다시 시도해 주세요.",
      });
    }
  }

  setTrees(trees: readonly ForestTree[]): void {
    validatePlacements(trees);
    this.pendingTrees = trees;
    if (!this.models || this.disposed) return;
    // Clones share immutable geometry and materials; models own and dispose those resources.
    this.trees.clear();
    for (const tree of trees) {
      const placement = new Group();
      placement.name = `tree-${tree.growthCycle}`;
      const { x, z } = slotPosition(tree.slotIndex);
      placement.position.set(x, 0.002, z);
      placement.add(this.models[tree.kind].clone(true));
      this.trees.add(placement);
    }
    this.container.dataset.treeCount = String(trees.length);
    this.renderer.domElement.setAttribute(
      "aria-label",
      `나무 ${trees.length}그루가 심어진 3D 숲. 방향키로 회전하고 더하기와 빼기로 확대할 수 있습니다.`,
    );
    this.invalidate();
  }

  setGrid(visible: boolean): void {
    this.grid.visible = visible;
    this.invalidate();
  }

  setRevealCycle(cycle: number | null): void {
    this.revealCycle = cycle;
    this.revealStarted = performance.now();
    this.invalidate();
  }

  resetView = (): void => {
    // Reset accumulated damping before applying the canonical Figma-inspired isometric view.
    const damping = this.controls.enableDamping;
    this.controls.enableDamping = false;
    this.controls.update();
    this.controls.target.set(0, 0.25, 0);
    this.camera.position.set(7, 6.5, 7);
    this.camera.zoom = 1;
    this.camera.updateProjectionMatrix();
    this.controls.update();
    this.controls.enableDamping = damping;
    this.onZoom(1);
    this.invalidate();
  };

  zoomBy = (factor: number): void => {
    this.camera.zoom = MathUtils.clamp(
      this.camera.zoom * factor,
      this.controls.minZoom,
      this.controls.maxZoom,
    );
    this.camera.updateProjectionMatrix();
    this.controls.update();
    this.onZoom(this.camera.zoom);
    this.invalidate();
  };

  private onKeyDown = (event: KeyboardEvent): void => {
    if (
      ![
        "ArrowLeft",
        "ArrowRight",
        "ArrowUp",
        "ArrowDown",
        "+",
        "=",
        "-",
        "Home",
      ].includes(event.key)
    )
      return;
    event.preventDefault();
    if (event.key === "Home") {
      this.resetView();
      return;
    }
    if (event.key === "+" || event.key === "=") {
      this.zoomBy(1.15);
      return;
    }
    if (event.key === "-") {
      this.zoomBy(1 / 1.15);
      return;
    }
    const offset = this.camera.position.clone().sub(this.controls.target);
    const spherical = new Spherical().setFromVector3(offset);
    if (event.key === "ArrowLeft") spherical.theta -= 0.16;
    if (event.key === "ArrowRight") spherical.theta += 0.16;
    if (event.key === "ArrowUp") spherical.phi -= 0.12;
    if (event.key === "ArrowDown") spherical.phi += 0.12;
    spherical.phi = MathUtils.clamp(
      spherical.phi,
      this.controls.minPolarAngle,
      this.controls.maxPolarAngle,
    );
    this.camera.position.copy(
      new Vector3().setFromSpherical(spherical).add(this.controls.target),
    );
    this.controls.update();
    this.invalidate();
  };

  private resize = (): void => {
    if (this.disposed) return;
    const { width, height } = this.container.getBoundingClientRect();
    if (width <= 0 || height <= 0) return;
    const aspect = width / height;
    const halfHeight = Math.max(3.35, 3.35 / aspect);
    this.camera.left = -halfHeight * aspect;
    this.camera.right = halfHeight * aspect;
    this.camera.top = halfHeight;
    this.camera.bottom = -halfHeight;
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(width, height);
    this.invalidate();
  };

  private updateMotion = (): void => {
    this.controls.enableDamping = !this.reducedMotion.matches;
    this.invalidate();
  };

  private onVisibilityChange = (): void => {
    if (document.hidden) this.stopFrame();
    else this.invalidate();
  };

  private onContextLost = (event: Event): void => {
    event.preventDefault();
    this.failed = true;
    this.stopFrame();
    this.onStatus({
      phase: "error",
      message: "3D 화면 연결이 중단됐어요. 다시 불러와 주세요.",
    });
  };

  private invalidate = (): void => {
    if (
      this.disposed ||
      this.failed ||
      document.hidden ||
      !this.visible ||
      this.frame
    )
      return;
    this.frame = requestAnimationFrame(this.draw);
  };

  private draw = (): void => {
    this.frame = 0;
    if (this.disposed || this.failed || document.hidden || !this.visible)
      return;
    const changed = this.controls.update();
    const revealing =
      this.revealCycle === null
        ? undefined
        : this.trees.getObjectByName(`tree-${this.revealCycle}`);
    const progress = this.reducedMotion.matches
      ? 1
      : Math.min(1, (performance.now() - this.revealStarted) / PLANT_DURATION);
    if (revealing)
      revealing.scale.setScalar(Math.max(0.02, 1 - Math.pow(1 - progress, 3)));
    this.renderer.render(this.scene, this.camera);
    this.onZoom(this.camera.zoom);
    this.container.dataset.camera = `${this.controls.getAzimuthalAngle().toFixed(3)},${this.controls.getPolarAngle().toFixed(3)}`;
    // Stop drawing when the camera settles. No perpetual loop for an idle static forest.
    if ((changed && this.controls.enableDamping) || (revealing && progress < 1))
      this.invalidate();
  };

  private stopFrame(): void {
    cancelAnimationFrame(this.frame);
    this.frame = 0;
  }

  dispose(): void {
    if (this.disposed) return;
    this.disposed = true;
    this.abortController.abort();
    this.stopFrame();
    this.resizeObserver.disconnect();
    this.intersectionObserver.disconnect();
    document.removeEventListener("visibilitychange", this.onVisibilityChange);
    this.reducedMotion.removeEventListener("change", this.updateMotion);
    this.renderer.domElement.removeEventListener("keydown", this.onKeyDown);
    this.renderer.domElement.removeEventListener(
      "webglcontextlost",
      this.onContextLost,
    );
    this.controls.dispose();
    if (this.models) disposeModels(Object.values(this.models));
    this.grid.geometry.dispose();
    (this.grid.material as LineBasicMaterial).dispose();
    this.scene.traverse((object) => {
      if (object instanceof DirectionalLight) object.shadow.dispose();
    });
    this.scene.clear();
    this.renderer.dispose();
    this.renderer.forceContextLoss();
    this.renderer.domElement.remove();
  }
}
