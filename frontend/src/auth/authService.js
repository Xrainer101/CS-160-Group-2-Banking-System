const domain = import.meta.env.VITE_AUTH0_DOMAIN;
const clientId = import.meta.env.VITE_AUTH0_CLIENT_ID;
const audience = import.meta.env.VITE_AUTH0_AUDIENCE;
const connection = import.meta.env.VITE_AUTH0_CONNECTION || 'Username-Password-Authentication';
const sessionKey = 'team2bank.auth.session';

function tokenUrl() {
    return `https://${domain}/oauth/token`;
}

async function readResponse(response) {
    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
    const error = new Error(data.error_description || data.description || data.error || 'Auth0 request failed.',);
    error.code = data.error || data.code;
    error.details = data;
    throw error;
    }
    return data;
}

async function requestToken(body) {
    const response = await fetch(tokenUrl(), {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify(body),
    });
    return readResponse(response);
}

// auth0 username and password login
export async function login(username, password) {
    try {
        const session = await requestToken({
        grant_type: 'http://auth0.com/oauth/grant-type/password-realm',
        client_id: clientId,
        username,
        password,
        realm: connection,
        audience,
        scope: 'openid profile email',
        });
        saveSession(session);
        return { authenticated: true, session };
    } catch (error) {
        // MFA: Duo handling
        if (error.code === 'mfa_required') {
            return {
                authenticated: false,
                requiresMfa: true,
                mfaToken: error.details.mfa_token,
            };
        }
        throw error;
    }
}

// Six digit MFA code verification
export async function verifyMfa(mfaToken, code) {
    const session = await requestToken({
        grant_type: 'http://auth0.com/oauth/grant-type/mfa-otp',
        client_id: clientId,
        mfa_token: mfaToken,
        otp: code,
        audience,
        scope: 'openid profile email',
    });
    saveSession(session);
    return { authenticated: true, session };
}

export async function startMfaEnrollment(mfaToken) {
  const response = await fetch(`https://${domain}/mfa/associate`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${mfaToken}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      authenticator_types: ['otp'],
    }),
  });
  return readResponse(response);
}

export async function verifyMfaEnrollment(mfaToken, code) {
  return verifyMfa(mfaToken, code);
}

// auth0 signup
export async function signup(profile) {
    const response = await fetch(`https://${domain}/dbconnections/signup`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({
            client_id: clientId,
            connection,
            email: profile.email,
            username: profile.username,
            password: profile.password,
            user_metadata: {
                full_name: profile.fullName,
                date_of_birth: profile.dateOfBirth,
                phone_number: profile.phone,
            },
        }),
    });
    return readResponse(response);
}

// Ask auth0 to send a password-reset email for user
export async function requestPasswordReset(email) {
    const response = await fetch(`https://${domain}/dbconnections/change_password`, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({client_id: clientId,email,connection}),
    });

    const message = await response.text();
    if (!response.ok) {
        let details = {};
        try {
            details = JSON.parse(message);
        } catch {
            details = { error_description: message };
        }
        const error = new Error(
            details.error_description || details.description || details.error || 'Unable to request a password reset.',
        );
        error.code = details.error || details.code;
        error.details = details;
        throw error;
    }
    return { message };
}

// ---- Session storage and clearing ----
export function getSession() {
    const saved = sessionStorage.getItem(sessionKey);
    return saved ? JSON.parse(saved) : null;
}

export function saveSession(session) {
    sessionStorage.setItem(sessionKey, JSON.stringify(session));
}

export function clearSession() {
    sessionStorage.removeItem(sessionKey);
}
// Access token retrieval
export function getAccessToken() {
    return getSession()?.access_token || null;
}
