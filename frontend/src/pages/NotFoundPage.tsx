import { Link } from "react-router-dom";

export function NotFoundPage() {
  return (
    <section className="space-y-3">
      <h1 className="text-2xl font-semibold">Page not found</h1>
      <p className="text-slate-600">The page you requested does not exist.</p>
      <Link to="/" className="text-sm font-medium text-indigo-600 hover:underline">
        Back to home
      </Link>
    </section>
  );
}
