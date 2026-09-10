import type { ReactNode } from 'react';
import { Plus } from 'lucide-react';

type CollectionHeaderProps = {
  icon: ReactNode;
  title: string;
  onAdd: () => void;
};

export function CollectionHeader({ icon, title, onAdd }: CollectionHeaderProps) {
  return (
    <div className="collection-header">
      <div>
        {icon}
        <h3>{title}</h3>
      </div>
      <button type="button" className="icon-text-button" onClick={onAdd}>
        <Plus size={16} aria-hidden="true" />
        Agregar
      </button>
    </div>
  );
}
