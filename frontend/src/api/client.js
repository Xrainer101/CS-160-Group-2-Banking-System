
const BASE_URL =
  import.meta.env.VITE_API_URL ||
  "http://localhost:8000";

export async function apiRequest(
  path,
  { token, ...options } = {}
) {
  const headers = {
    ...(options.body ? {
      "Content-Type": "application/json"
    } : {}),
    ...options.headers,
  };

  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  let response;

  try {
    response = await fetch(`${BASE_URL}${path}`, {
      ...options,
      headers,
    });
  } catch {
    throw new Error(
      "Could not reach the banking server. Check that the backend is running."
    );
  }

  const data = await response.json().catch(() => null);

  if (!response.ok) {
    const detail = data?.detail;

    const message =
      typeof detail === "string"
        ? detail
        : Array.isArray(detail)
          ? detail.map((item) => item.msg).join(", ")
          : `Request failed with status ${response.status}`;

    const error = new Error(message);
    error.status = response.status;
    throw error;
  }

  return data;
}