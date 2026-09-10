import { useEffect, useRef, useState } from 'react';
import { X } from 'lucide-react';
import type { Notice } from '../types/notice';

type NoticeMessageProps = {
  notice: Notice | null;
  duration?: number; // ms antes de auto-cerrarse
  onDismiss?: () => void;
};

export function NoticeMessage({ notice, duration = 4000, onDismiss }: NoticeMessageProps) {
  const [visibleNotice, setVisibleNotice] = useState<Notice | null>(null);
  const [leaving, setLeaving] = useState(false);
  const hideTimer = useRef<ReturnType<typeof setTimeout>>();
  const removeTimer = useRef<ReturnType<typeof setTimeout>>();

  useEffect(() => {
    if (!notice) return;

    clearTimeout(hideTimer.current);
    clearTimeout(removeTimer.current);

    setVisibleNotice(notice);
    setLeaving(false);

    hideTimer.current = setTimeout(() => {
      setLeaving(true);
      removeTimer.current = setTimeout(() => {
        setVisibleNotice(null);
        onDismiss?.();
      }, 200); // matchea la duración de --animate-toast-out
    }, duration);

    return () => {
      clearTimeout(hideTimer.current);
      clearTimeout(removeTimer.current);
    };
  }, [notice, duration, onDismiss]);

  if (!visibleNotice) return null;

  const handleClose = () => {
    clearTimeout(hideTimer.current);
    setLeaving(true);
    removeTimer.current = setTimeout(() => {
      setVisibleNotice(null);
      onDismiss?.();
    }, 200);
  };

  return (
    <div className="toast-viewport">
      <div className={`toast ${visibleNotice.tone}${leaving ? ' leaving' : ''}`}>
        <span>{visibleNotice.message}</span>
        <button type="button" className="toast-close" onClick={handleClose} aria-label="Cerrar">
          <X size={14} />
        </button>
      </div>
    </div>
  );
}