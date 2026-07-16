import { Sparkles } from 'lucide-react';

export function BrandBlock() {
  return (
    <div className="brand-block">
      <div className="brand-mark">
        <Sparkles size={22} aria-hidden="true" />
      </div>
      <div>
        <p className="eyebrow">Aplicafacil</p>
        <h1>Profile</h1>
      </div>
    </div>
  );
}
