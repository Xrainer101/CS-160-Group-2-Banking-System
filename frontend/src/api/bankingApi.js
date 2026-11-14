import {getAccessToken} from '../auth/authService';
const baseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8000';

// Reads the auth0 access token and sends it as a Bearer token to the backend
// Returns backend errors
async function request(path) {
    const token = getAccessToken();
    if (!token) {
        throw new Error('No Auth0 access token is available.');
    }

    const response = await fetch(`${baseUrl}${path}`, {
        headers: {
            Accept: 'application/json',
            Authorization: `Bearer ${token}`,
        },
    });

    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
        const message =
        typeof data.detail === 'string'? data.detail : 'The banking backend request failed.';
        throw new Error(message);
    }
    return data;
}

export function getCurrentUser() {
    return request('/api/auth/me');
}

export function getAccounts() {
    return request('/api/accounts');
}