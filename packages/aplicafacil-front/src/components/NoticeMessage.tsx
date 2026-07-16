import type { Notice } from '../types/notice';

type NoticeMessageProps = {
  notice: Notice | null;
};

export function NoticeMessage({ notice }: NoticeMessageProps) {
  if (!notice) return null;

  return <div className={`notice ${notice.tone}`}>{notice.message}</div>;
}
