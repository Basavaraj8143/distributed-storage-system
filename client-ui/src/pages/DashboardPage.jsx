export default function DashboardPage() {
  return (
    <section className="page">
      <p className="eyebrow">Overview</p>
      <h2>Dashboard</h2>
      <p className="page-copy">
        Live system cards and replication health will be added in Workstream D.
      </p>
      <div className="placeholder-grid">
        <article className="placeholder-card">Cluster Health Snapshot</article>
        <article className="placeholder-card">Replication Summary</article>
        <article className="placeholder-card">Recent Upload Activity</article>
      </div>
    </section>
  );
}
