import { useEffect, useRef, type ReactNode } from 'react';
import { X } from 'lucide-react';

export default function Dialog({
  title,
  children,
  className,
  onClose
}: {
  title: string;
  children: ReactNode;
  className?: string;
  onClose: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const el = ref.current!;
    el.showModal();
    return () => el.close();
  }, []);
  return <dialog
    ref={ref}
    className={className}
    onCancel={e => {
      e.preventDefault();
      onClose();
    }}
    onClick={e => {
      if (e.target === e.currentTarget) onClose();
    }}
    aria-labelledby="dialog-title"
  >
    <div className="dialog-heading">
      <h2 id="dialog-title">{title}</h2>
      <button
        className="icon-button"
        aria-label="닫기"
        onClick={onClose}
      >
        <X size={20} />
      </button>
    </div>
    {children}
  </dialog>;
}
