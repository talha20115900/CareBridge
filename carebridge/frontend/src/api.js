let csrfToken;

export class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.status = status;
  }
}

async function csrf() {
  if (!csrfToken) {
    const response = await fetch('/api/auth/csrf', { cache: 'no-store' });
    if (!response.ok) throw new Error('Could not start a secure session. Reload the page.');
    csrfToken = await response.json();
  }
  return csrfToken;
}

export async function api(path, { method = 'GET', body, signal } = {}) {
  const headers = new Headers({ Accept: 'application/json' });
  if (method !== 'GET') {
    const token = await csrf();
    headers.set(token.headerName, token.token);
  }
  if (body && !(body instanceof FormData) && !(body instanceof URLSearchParams)) {
    headers.set('Content-Type', 'application/json');
    body = JSON.stringify(body);
  }
  const response = await fetch('/api' + path, { method, body, headers, signal, cache: 'no-store' });
  if (response.status === 401 || response.status === 403 ||
      path === '/auth/login' || path === '/auth/logout') csrfToken = undefined;

  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    const messages = {
      400: 'Check the submitted values.',
      401: path === '/auth/login' ? 'Email or password is incorrect.' : 'Sign in again to continue.',
      403: 'Access denied or session expired. Try refreshing the page.',
      404: 'This item is unavailable.',
      413: 'The file is too large.',
    };
    throw new ApiError(error.message || messages[response.status] || 'The request failed. Try again.', response.status);
  }
  return response.status === 204 ? null : response.json();
}
