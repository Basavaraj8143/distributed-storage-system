import { NavLink } from "react-router-dom";

const navItems = [
  { to: "/dashboard", label: "Dashboard" },
  { to: "/upload", label: "Upload" },
  { to: "/files", label: "Files" },
  { to: "/nodes", label: "Node Status" },
  { to: "/logs", label: "Logs" }
];

export default function SideNav({ mobileOpen, onNavigate }) {
  return (
    <aside className={`sidenav ${mobileOpen ? "open" : ""}`}>
      <div className="brand-box">
        <p className="brand-prefix">Distributed Storage</p>
        <h1>Byte Harvest</h1>
      </div>
      <nav className="nav-list" aria-label="Main navigation">
        {navItems.map((item) => (
          <NavLink
            key={item.to}
            className={({ isActive }) => `nav-item ${isActive ? "active" : ""}`}
            onClick={onNavigate}
            to={item.to}
          >
            {item.label}
          </NavLink>
        ))}
      </nav>
    </aside>
  );
}
