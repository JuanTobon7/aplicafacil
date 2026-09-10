import axios from 'axios';

export const httpClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? 'http://localhost:3000/api/v1',
  withCredentials: false,
});

httpClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('access_token');

  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  return config;
});

httpClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const message =
      typeof error.response?.data === 'string'
        ? error.response.data
        : error.response?.data?.message ?? error.message ?? 'No fue posible completar la peticion.';

    return Promise.reject(new Error(Array.isArray(message) ? message.join(', ') : message));
  },
);
