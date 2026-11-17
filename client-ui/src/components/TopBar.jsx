export default function TopBar({ onToggleMenu, onSignOut }) {
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
      {onSignOut ? (
        <button className="ghost-button small" type="button" onClick={onSignOut}>
          Sign out
        </button>
      ) : null}
    </header>
  );
}
