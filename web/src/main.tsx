import { NativeForestApp } from "./flow/NativeForestApp";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { ForestPreview } from "./preview/ForestPreview";
import { ForestScreen } from "./flow/ForestScreen";
import { ForestController } from "./flow/ForestController";
import { PLANT_DURATION } from "./forest/motion";
import { FlowReview } from "./preview/FlowReview";
import {
  createDemoCommands,
  createFlowSnapshot,
  FLOW_SCENARIOS,
} from "./preview/flowScenarios";
import "./styles.css";

const query = new URLSearchParams(window.location.search);
const scenario =
  FLOW_SCENARIOS.find((item) => item.id === query.get("scenario"))?.id ??
  "first";
const snapshot = createFlowSnapshot(scenario);
const controller = new ForestController(
  snapshot,
  createDemoCommands(snapshot, scenario, () =>
    window.location.assign(import.meta.env.BASE_URL),
  ),
  () =>
    new Promise((resolve) =>
      window.setTimeout(
        resolve,
        window.matchMedia("(prefers-reduced-motion: reduce)").matches
          ? 0
          : PLANT_DURATION,
      ),
    ),
);

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    {query.get("view") === "app" ? (
      <NativeForestApp />
    ) : query.get("view") === "preview" ? (
      <ForestPreview />
    ) : query.get("view") === "forest" ? (
      <ForestScreen controller={controller} />
    ) : (
      <FlowReview />
    )}
  </StrictMode>,
);
