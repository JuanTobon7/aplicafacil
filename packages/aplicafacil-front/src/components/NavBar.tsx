import { NavLink } from 'react-router';
import { User } from 'lucide-react';
import { BrandBlock } from './BrandBlock';

type NavBarProps = {
  isAuthenticated: boolean;
  userName?: string;
};

export function NavBar({ isAuthenticated, userName }: NavBarProps) {
  return (
    <header className="nav-bar">
      <BrandBlock />

      <nav className="nav-bar-actions">
        <NavLink
          to="/auth"
          className={({ isActive }) => `nav-user-chip${isActive ? ' active' : ''}`}
        >
          {isAuthenticated ? (
            <>
              <span className="nav-avatar" aria-hidden="true">
                {userName ? userName.charAt(0).toUpperCase() : <User size={16} />}
              </span>
              <span className="nav-user-name">{userName ?? 'Mi cuenta'}</span>
            </>
          ) : (
            <>
              <User size={18} aria-hidden="true" />
              <span>Ingresar</span>
            </>
          )}
        </NavLink>
      </nav>
    </header>
  );
}