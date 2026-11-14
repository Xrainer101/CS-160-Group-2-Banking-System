export default function WelcomeSection({ user }) {
  const greeting = 'Welcome';
  return (
    <div className="welcome-section">
      <div>
        <span className="eyebrow">ACCOUNT OVERVIEW</span>
        <h1>
          {greeting}, {user.name}! <span className="wave">✦</span>
        </h1>
        <p>Here's what's happening with your money today.</p>
      </div>
      <span className="date-label">
        {new Date().toLocaleDateString('en-US', {
          weekday: 'long',
          month: 'long',
          day: 'numeric',
          year: 'numeric',
        })}
      </span>
    </div>
  );
}
