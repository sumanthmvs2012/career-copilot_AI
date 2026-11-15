import { Link } from 'react-router-dom';

export default function SignupPage() {
  return (
    <main className="placeholder-page">
      <h1>Sign up</h1>
      <p>Sign up coming soon.</p>
      <p>
        Already have an account? <Link to="/login">Log in</Link>
      </p>
    </main>
  );
}
