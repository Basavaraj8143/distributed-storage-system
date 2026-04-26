export default function TopBar({ onToggleMenu }) {
  return (
    <header className="topbar">
      <div className="topbar-left">
        <button
          aria-label="Open navigation menu"
          className="menu-button"
          onClick={onToggleMenu}
          type="button"
        >
          <span />
          <span />
          <span />
        </button>
        <p className="topbar-title">Byte Harvest Storage Console</p>
      </div>
      <p className="topbar-status">Workstream A: Shell + Routing</p>
    </header>
  );
}
