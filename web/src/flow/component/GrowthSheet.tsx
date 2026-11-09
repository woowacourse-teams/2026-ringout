import { useEffect, useRef } from "react";
import type { ForestSnapshot } from "../../bridge/protocol";
import { forestImages } from "./assets";

export function GrowthSheet({
  growth,
  onClose,
}: {
  growth: ForestSnapshot["growth"];
  onClose(): void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const dragStart = useRef<number | null>(null);
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    const node = dialog.current!;
    node.showModal();
    return () => {
      node.close();
      previous?.focus();
    };
  }, []);
  return (
    <dialog
      ref={dialog}
      className="growth-sheet"
      aria-labelledby="growth-sheet-title"
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
        <img
          className="sheet-seed"
          src={forestImages.seed}
          alt="웃고 있는 나무 씨앗"
          width="273"
          height="273"
        />
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
