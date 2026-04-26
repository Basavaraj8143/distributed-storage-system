import { useState } from "react";
import SideNav from "./SideNav";
import TopBar from "./TopBar";

export default function AppShell({ children }) {
  const [menuOpen, setMenuOpen] = useState(false);

  return (
    <div className="app-shell">
      <SideNav mobileOpen={menuOpen} onNavigate={() => setMenuOpen(false)} />
      <div className="app-main">
        <TopBar onToggleMenu={() => setMenuOpen((v) => !v)} />
        <main className="content-area">{children}</main>
      </div>
      {menuOpen ? (
        <button
          aria-label="Close navigation menu"
          className="mobile-overlay"
          onClick={() => setMenuOpen(false)}
          type="button"
        />
      ) : null}
    </div>
  );
}
