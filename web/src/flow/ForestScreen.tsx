import { useEffect, useRef, useSyncExternalStore } from "react";
import { ForestCanvas } from "../forest/ForestCanvas";
import {
  ForestController,
  pendingTree,
  visibleTrees,
} from "./ForestController";
import { Onboarding } from "./component/Onboarding";
import { GrowthCard } from "./component/GrowthCard";
import { GrowthSheet } from "./component/GrowthSheet";
import { TreeCompletion } from "./component/TreeCompletion";
import { forestImages } from "./component/assets";
import "./forest-screen.css";

export function ForestScreen({ controller }: { controller: ForestController }) {
  const state = useSyncExternalStore(
    controller.subscribe,
    controller.getSnapshot,
  );
  const content = useRef<HTMLDivElement>(null);
  const pending = pendingTree(state.snapshot);
  useEffect(() => {
    content.current?.querySelector<HTMLElement>("h1")?.focus();
  }, [state.page]);
  return (
    <main
      className="forest-app"
      data-page={state.page}
      aria-label="숲 키우기"
      aria-busy={state.busy}
    >
      <div className="forest-app-content" ref={content}>
        <header className="forest-app-bar">
          <button
            aria-label="뒤로가기"
            onClick={controller.back}
            disabled={state.busy}
          >
            <img src={forestImages.back} alt="" width="44" height="44" />
          </button>
        </header>
        {state.page !== "forest" ? (
          <Onboarding
            page={state.page}
            busy={state.busy}
            error={state.error}
            onNext={controller.nextGuide}
            onStart={controller.completeOnboarding}
          />
        ) : (
          <>
            <h1 className="sr-only" tabIndex={-1}>
              나만의 숲
            </h1>
            <div className="forest-main-scene">
              <ForestCanvas
                trees={visibleTrees(state)}
                showGrid={false}
                variant="app"
                revealCycle={state.plantingCycle}
              />
            </div>
            <GrowthCard
              growth={state.snapshot.growth}
              onClick={controller.openSheet}
            />
          </>
        )}
      </div>
      {state.busy && state.plantingCycle !== null && (
        <div className="planting-status" role="status">
          나무를 심고 있어요
        </div>
      )}
      <GrowthSheet
        growth={state.snapshot.growth}
        isOpen={state.sheetOpen}
        onClose={controller.closeSheet}
      />
      {state.page === "forest" && pending && state.plantingCycle === null && (
        <TreeCompletion
          key={pending.growthCycle}
          tree={pending}
          error={state.error}
          onPlant={controller.plant}
          onBack={controller.back}
        />
      )}
    </main>
  );
}
