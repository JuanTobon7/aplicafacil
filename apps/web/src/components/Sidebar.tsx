import { NavLink, useNavigate } from "react-router";
import { displayName, type User } from "../api/auth";
import { Icon, type IconName } from "./Icon";
import { ThemeToggle } from "./ThemeToggle";

type Item = { to: string; label: string; icon: IconName; end?: boolean };

/** Las vistas de la app, agrupadas por para qué sirven. */
const GROUPS: { title: string; items: Item[] }[] = [
  {
    title: "Empleo",
    items: [
      { to: "/vacantes", label: "Vacantes", icon: "sparkles", end: true },
      { to: "/vacantes/manual", label: "Búsqueda manual", icon: "search" },
    ],
  },
  {
    title: "Mi perfil",
    items: [
      { to: "/perfil", label: "Perfil laboral", icon: "user", end: true },
      { to: "/perfil/cv", label: "Importar CV", icon: "file" },
      { to: "/perfil/busqueda", label: "Preferencias", icon: "sliders" },
    ],
  },
  {
    title: "Cuenta",
    items: [
      { to: "/cuentas", label: "Conexiones", icon: "link" },
      { to: "/ia", label: "Proveedores de IA", icon: "cpu" },
    ],
  },
];

type Props = { user: User; open: boolean; onNavigate: () => void; onLogout: () => Promise<unknown> };

/** Navegación lateral: fija en escritorio; en el celular se abre como cajón desde el botón de menú. */
export function Sidebar({ user, open, onNavigate, onLogout }: Props) {
  const navigate = useNavigate();
  const name = displayName(user);
  return (
    <aside className={`sidebar${open ? " open" : ""}`} aria-label="Navegación">
      <NavLink to="/vacantes" className="brand" onClick={onNavigate}>
        <span className="brand-mark" aria-hidden>AF</span>
        AplicaFácil
      </NavLink>
      <nav className="side-nav">
        {GROUPS.map((group) => (
          <div key={group.title} className="side-group">
            <p className="side-title">{group.title}</p>
            {group.items.map((item) => (
              <NavLink key={item.to} to={item.to} end={item.end} onClick={onNavigate}>
                <Icon name={item.icon} />
                {item.label}
              </NavLink>
            ))}
          </div>
        ))}
      </nav>
      <div className="side-footer">
        <ThemeToggle />
        <div className="side-user">
          <span className="avatar" aria-hidden>{name.charAt(0)}</span>
          <span className="grow">{name}</span>
          <button className="icon-button" title="Cambiar de cuenta" aria-label="Cambiar de cuenta"
            onClick={() => void onLogout().then(() => navigate("/"))}>
            <Icon name="logout" />
          </button>
        </div>
      </div>
    </aside>
  );
}
