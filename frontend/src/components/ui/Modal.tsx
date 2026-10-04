import { useEffect, useId, useRef, type ReactNode } from "react";
import { XIcon } from "./Icons";

interface ModalProps {
  open: boolean;
  onClose: () => void;
  title: string;
  subtitle?: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
  size?: "md" | "lg";
}

/**
 * Accessible modal built on <dialog>: focus is trapped by the browser, Esc
 * closes it, and it becomes a bottom sheet on small screens.
 */
export function Modal({ open, onClose, title, subtitle, children, footer, size = "md" }: ModalProps) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();

  useEffect(() => {
    const d = ref.current;
    if (!d) return;
    if (open && !d.open) d.showModal();
    if (!open && d.open) d.close();
  }, [open]);

  return (
    <dialog
      ref={ref}
      onCancel={(e) => {
        e.preventDefault();
        onClose();
      }}
      onClick={(e) => {
        if (e.target === ref.current) onClose();
      }}
      aria-labelledby={titleId}
      className={`m-0 mt-auto w-full max-w-none rounded-t-3xl bg-white p-0 text-ink shadow-2xl sm:m-auto sm:rounded-3xl ${
        size === "lg" ? "sm:max-w-2xl" : "sm:max-w-lg"
      } max-h-[92dvh]`}
    >
      {open && (
        <div className="flex max-h-[92dvh] flex-col">
          <header className="flex items-start justify-between gap-4 border-b border-line px-5 pt-5 pb-4">
            <div className="min-w-0">
              <h2 id={titleId} className="font-display text-2xl font-bold leading-tight">
                {title}
              </h2>
              {subtitle && <div className="mt-1 text-sm text-muted">{subtitle}</div>}
            </div>
            <button
              type="button"
              onClick={onClose}
              className="-mr-2 grid h-10 w-10 shrink-0 place-items-center rounded-full text-muted hover:bg-ink/5"
              aria-label="Close"
            >
              <XIcon />
            </button>
          </header>
          <div className="overflow-y-auto px-5 py-5">{children}</div>
          {footer && <footer className="pb-safe border-t border-line px-5 py-4">{footer}</footer>}
        </div>
      )}
    </dialog>
  );
}

export function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel,
  onConfirm,
  onCancel,
  busy,
  error,
}: {
  open: boolean;
  title: string;
  message: ReactNode;
  confirmLabel: string;
  onConfirm: () => void;
  onCancel: () => void;
  busy?: boolean;
  error?: string | null;
}) {
  return (
    <Modal
      open={open}
      onClose={onCancel}
      title={title}
      footer={
        <div className="flex justify-end gap-2">
          <button type="button" onClick={onCancel} className="h-11 rounded-xl px-4 font-semibold text-ink-soft hover:bg-ink/5">
            Cancel
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={busy}
            className="h-11 rounded-xl bg-missed px-4 font-semibold text-white hover:bg-missed/90 disabled:opacity-60"
          >
            {busy ? "Deleting…" : confirmLabel}
          </button>
        </div>
      }
    >
      <div className="text-ink-soft">{message}</div>
      {error && (
        <p role="alert" className="mt-3 rounded-xl bg-missed-50 px-3 py-2 text-sm text-missed">
          {error}
        </p>
      )}
    </Modal>
  );
}
