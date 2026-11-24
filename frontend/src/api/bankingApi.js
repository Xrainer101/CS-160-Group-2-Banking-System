import {getAccessToken} from '../auth/authService';
const baseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8000';

// Reads the auth0 access token and sends it as a Bearer token to the backend
// Returns backend errors
async function request(path, options = {}) {
    const token = getAccessToken();
    if (!token) {
        throw new Error('No Auth0 access token is available.');
    }

    const response = await fetch(`${baseUrl}${path}`, {
        ...options,
        headers: {
            'Content-Type': 'application/json',
            Accept: 'application/json',
            Authorization: `Bearer ${token}`,
        },
    });

    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
        const message =
        typeof data.detail === 'string'? data.detail : 'The banking backend request failed.';
        const error = new Error(message);
        error.status = response.status;
        error.detail = data.detail;
        throw error;
    }
    return data;
}

export function getCurrentUser() {
    return request('/api/auth/me');
}

export function deleteOwnProfile() {
    return request('/api/auth/profile', { method: 'DELETE' });
}

export function createAccount(accountType) {
    return request('/api/accounts', {
        method: 'POST',
        body: JSON.stringify({ account_type: accountType }),
    });
}

export function closeAccount(accountId) {
    return request(`/api/accounts/${encodeURIComponent(accountId)}/close`, { method: 'POST' });
}

export function getAccountDetail(accountId, signal) {
    return request(`/api/accounts/${encodeURIComponent(accountId)}`, { signal });
}

export function moveAccountMoney(accountId, kind, amount) {
    return request(`/api/accounts/${encodeURIComponent(accountId)}/${kind}`, {
        method: 'POST', body: JSON.stringify({ amount }),
    });
}

export function getAccounts() {
    return request('/api/accounts');
}

export function getExternalAccounts(signal) {
    return request('/api/external-accounts', { signal });
}

export function processDueTransfers() {
    return request('/api/transfers/process-due', { method: 'POST' });
}

export function createTransfer(transfer) {
    return request('/api/transfers', {
        method: 'POST',
        body: JSON.stringify(transfer),
    });
}
