async function request(path, options = {}) {
  const res = await fetch(`/api${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(body.error_message || body.display_message || `Request failed (${res.status})`);
  }
  return body;
}

export const api = {
  linkToken: () => request('/link/token', { method: 'POST' }),
  exchange: (public_token, institution_name) =>
    request('/link/exchange', { method: 'POST', body: JSON.stringify({ public_token, institution_name }) }),
  sync: () => request('/sync', { method: 'POST' }),
  items: () => request('/items'),
  removeItem: (id) => request(`/items/${id}`, { method: 'DELETE' }),
  summary: () => request('/summary'),
  months: () => request('/months'),
  spending: (month) => request(`/spending?month=${month}`),
  recurring: () => request('/recurring'),
  transactions: (limit = 40) => request(`/transactions?limit=${limit}`),
};
