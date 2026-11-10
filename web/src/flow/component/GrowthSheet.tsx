import { useEffect, useLayoutEffect, useRef, useState } from "react";
import type { ForestSnapshot } from "../../bridge/protocol";
import { forestImages } from "./assets";
import { SeedAnimation } from "./SeedAnimation";

export function GrowthSheet({
  growth,
  isOpen,
  onClose,
}: {
  growth: ForestSnapshot["growth"];
  isOpen: boolean;
  onClose(): void;
}) {
  const [visible, setVisible] = useState(isOpen);
  const dialog = useRef<HTMLDialogElement>(null);
  const dragStart = useRef<number | null>(null);
  useLayoutEffect(() => {
    const node = dialog.current;
    if (!node || isOpen) return;
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      setVisible(false);
      return;
    }
    const card = node.closest(".forest-app")?.querySelector(".growth-card");
    // Match the real collapsed card, including responsive sizing and safe-area padding.
    const cardHeight = card?.getBoundingClientRect().height ?? 0;
    node.style.setProperty("--growth-collapsed-height", `${cardHeight}px`);

  }, [isOpen, visible]);
  useEffect(() => {
    if (isOpen) {
      setVisible(true);
    } else if (visible) {
      // Keep the dialog mounted until the exit animation finishes, with a fallback.
      const timeout = window.setTimeout(() => setVisible(false), 300);
      return () => window.clearTimeout(timeout);
    }
  }, [isOpen, visible]);
  useEffect(() => {
    if (!visible) return;
    const previous = document.activeElement as HTMLElement | null;
    const node = dialog.current!;
    node.showModal();
    return () => {
      node.close();
      previous?.focus();
    };
  }, [visible]);
  if (!visible) return null;
  return (
    <dialog
      ref={dialog}
      className={`growth-sheet${isOpen ? "" : " growth-sheet-closing"}`}
      aria-labelledby="growth-sheet-title"
      onAnimationEnd={(event) => {
        if (event.target === event.currentTarget && event.animationName === "sheet-drop" && !isOpen) {
          setVisible(false);
        }
      }}
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      onClick={(event) => {
        if (event.target !== event.currentTarget) return;
        const bounds = event.currentTarget.getBoundingClientRect();
        if (
          event.clientY < bounds.top ||
          event.clientX < bounds.left ||
          event.clientX > bounds.right
        )
          onClose();
      }}
    >
      <button
        className="sheet-handle"
        aria-label="씨앗 성장 상태 닫기"
        onClick={onClose}
        onPointerDown={(event) => {
          dragStart.current = event.clientY;
          event.currentTarget.setPointerCapture(event.pointerId);
        }}
        onPointerUp={(event) => {
          if (
            dragStart.current !== null &&
            event.clientY - dragStart.current > 40
          )
            onClose();
          dragStart.current = null;
        }}
      >
        <span />
      </button>
      <div className="growth-sheet-content">
        <SeedAnimation />
        <h2 id="growth-sheet-title">
          <em>{growth.remainingDays}일</em> 더 도착하면
          <br />
          나무로 자라요
        </h2>
        <div
          className="growth-stamps"
          role="img"
          aria-label={`5일 중 ${growth.filledSlots}일 성장`}
        >
          {Array.from({ length: 5 }, (_, index) => (
            <span className="stamp-slot" key={index}>
              <img
                src={
                  index < growth.filledSlots
                    ? forestImages.filled
                    : forestImages.empty
                }
                alt=""
                width="64"
                height="64"
              />
            </span>
          ))}
        </div>
      </div>
    </dialog>
  );
}
