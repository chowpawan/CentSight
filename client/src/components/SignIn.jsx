import { signIn } from '../auth.js';

export default function SignIn({ error }) {
  return (
    <div className="shell">
      <section className="signin">
        <h1>CentSight</h1>
        <p>Every balance, bill and subscription in one place. Sign in to connect your accounts.</p>

        {error && <div className="alert" role="alert"><span>{error}</span></div>}

        <button className="google-btn" onClick={signIn}>
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path fill="#4285F4" d="M23.5 12.3c0-.8-.1-1.6-.2-2.3H12v4.5h6.5a5.6 5.6 0 0 1-2.4 3.6v3h3.9c2.3-2.1 3.5-5.2 3.5-8.8Z"/>
            <path fill="#34A853" d="M12 24c3.2 0 5.9-1.1 7.9-2.9l-3.9-3c-1.1.7-2.4 1.2-4 1.2-3.1 0-5.7-2.1-6.6-4.9H1.4v3.1A12 12 0 0 0 12 24Z"/>
            <path fill="#FBBC05" d="M5.4 14.4a7.2 7.2 0 0 1 0-4.6V6.7H1.4a12 12 0 0 0 0 10.8l4-3.1Z"/>
            <path fill="#EA4335" d="M12 4.8c1.8 0 3.3.6 4.6 1.8l3.4-3.4A12 12 0 0 0 1.4 6.7l4 3.1C6.3 6.9 8.9 4.8 12 4.8Z"/>
          </svg>
          Continue with Google
        </button>

        <p className="small muted">
          CentSight reads your accounts through Plaid. It never sees your bank password,
          and it cannot move money.
        </p>
      </section>
    </div>
  );
}
