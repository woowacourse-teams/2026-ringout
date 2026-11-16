import { useEffect, useState } from "react";
import { NativeForestBridge, nativeTransport } from "../bridge/NativeForestBridge";
import { ForestController } from "./ForestController";
import { ForestScreen } from "./ForestScreen";
import { PLANT_DURATION } from "../forest/motion";
import "./forest-screen.css";

export function NativeForestApp() {
  const [controller, setController] = useState<ForestController | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    // Embedded Android WebViews can report a zero dvh after first Compose layout.
    // innerHeight follows the actual host bounds, including native safe-area padding.
    const root = document.documentElement;
    const resize = () => root.style.setProperty("--forest-host-height", `${window.innerHeight}px`);
    root.dataset.nativeForest = "true";
    resize();
    window.addEventListener("resize", resize);
    window.visualViewport?.addEventListener("resize", resize);
    return () => {
      window.removeEventListener("resize", resize);
      window.visualViewport?.removeEventListener("resize", resize);
      delete root.dataset.nativeForest;
      root.style.removeProperty("--forest-host-height");
    };
  }, []);
  useEffect(() => {
    let disposed = false;
    setController(null);
    setError(null);
    const transport = nativeTransport();
    if (!transport) { setError("링아웃 앱에서 숲 키우기를 열어 주세요."); return; }
    const bridge = new NativeForestBridge(transport);
    const receiver = { receive: bridge.receive };
    window.RingoutForestBridge = receiver;
    let unsubscribe = () => {};
    bridge.ready().then(snapshot => {
      if (disposed) return;
      const next = new ForestController(snapshot, bridge, () => new Promise(resolve => {
        setTimeout(resolve, window.matchMedia("(prefers-reduced-motion: reduce)").matches ? 0 : PLANT_DURATION);
      }));
      bridge.onBack = next.back;
      unsubscribe = bridge.subscribe(next.receiveSnapshot);
      setController(next);
    }).catch(() => {
      if (!disposed) setError("숲 기록을 불러오지 못했어요. 다시 시도해 주세요.");
    });
    return () => {
      disposed = true;
      unsubscribe();
      bridge.dispose();
      if (window.RingoutForestBridge === receiver) delete window.RingoutForestBridge;
    };
  }, [attempt]);
  if (controller) return <ForestScreen controller={controller} />;
  return <main className="forest-app forest-connection" aria-live="polite">
    <p>{error ?? "숲을 불러오고 있어요."}</p>
    {error && nativeTransport() && <button type="button" onClick={() => setAttempt(n => n + 1)}>다시 시도</button>}
  </main>;
}
