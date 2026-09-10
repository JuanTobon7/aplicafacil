import { Trash2 } from 'lucide-react';

type IconButtonProps = {
  label: string;
  onClick: () => void;
  disabled?: boolean;
};

export function IconButton({ label, onClick, disabled }: IconButtonProps) {
  return (
    <button type="button" className="icon-button" aria-label={label} title={label} onClick={onClick} disabled={disabled}>
      <Trash2 size={17} aria-hidden="true" />
    </button>
  );
}
