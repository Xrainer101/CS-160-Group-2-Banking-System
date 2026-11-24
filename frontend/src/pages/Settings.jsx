import { useState } from 'react';
import { deleteOwnProfile } from '../api/bankingApi';

export default function Settings({ user, notify, signOut }) {
  const [name, setName] = useState(user.name),
    [email, setEmail] = useState(user.email),
    [alerts, setAlerts] = useState(true),
    [updates, setUpdates] = useState(false),
    [deleting, setDeleting] = useState(false);

  async function handleDeleteProfile() {
    const confirmed = window.confirm(
      'Are you sure you want to DELETE your profile? THIS ACTION CANNOT BE UNDONE'
    );
    if (!confirmed) return;

    setDeleting(true);
    try {
      await deleteOwnProfile();
      notify('Account deleted successfully.');
      signOut();
    } catch (error) {
      notify(error.message || 'Unable to delete your profile.');
    } finally {
      setDeleting(false);
    }
  }

  return (
    <>
      <div className="page-heading">
        <span className="eyebrow">PREFERENCES</span>
        <h1>Settings</h1>
        <p>Customize your demonstration banking experience.</p>
      </div>
      <div className="two-column">
        <section className="panel">
          <h2>Profile</h2>
          <form
            className="stacked-form"
            onSubmit={(e) => {
              e.preventDefault();
              notify('Demo profile preferences saved for this session.');
            }}
          >
            <label>
              Display name
              <input value={name} onChange={(e) => setName(e.target.value)} />
            </label>
            <label>
              Email address
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </label>
            <button className="button button-primary">Save preferences</button>
          </form>
        </section>
        <section className="panel">
          <h2>Delete profile</h2>
          <p className="panel-subtitle">
            Close all bank accounts before deleting your profile. This action cannot be undone.
          </p>
          <button
            className="button button-danger"
            type="button"
            onClick={handleDeleteProfile}
            disabled={deleting}
          >
            {deleting ? 'Deleting profile...' : 'Delete profile'}
          </button>
        </section>
        <section className="panel">
          <h2>Notifications</h2>
          <label className="toggle-row">
            <span>
              <strong>Transaction alerts</strong>
              <small>Show demo transaction notifications</small>
            </span>
            <input
              type="checkbox"
              checked={alerts}
              onChange={(e) => setAlerts(e.target.checked)}
            />
          </label>
          <label className="toggle-row">
            <span>
              <strong>Product updates</strong>
              <small>Show sample product messages</small>
            </span>
            <input
              type="checkbox"
              checked={updates}
              onChange={(e) => setUpdates(e.target.checked)}
            />
          </label>
          <p className="panel-subtitle">
            Preferences are not sent to a server.
          </p>
        </section>
      </div>
    </>
  );
}
