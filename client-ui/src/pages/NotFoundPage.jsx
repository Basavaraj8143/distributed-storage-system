import { Link } from "react-router-dom";

export default function NotFoundPage() {
  return (
    <section className="page">
      <p className="eyebrow">Route Missing</p>
      <h2>Page not found</h2>
      <p className="page-copy">
        The page you requested does not exist in this build.
      </p>
      <Link className="home-link" to="/dashboard">
        Go to Dashboard
      </Link>
    </section>
  );
}
