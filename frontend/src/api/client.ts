import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_URL || '/api';

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 600000, // 10 minutes default timeout (for large file uploads)
});

// Request interceptor for auth tokens
apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('auth_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Response interceptor for error handling and data normalization
apiClient.interceptors.response.use(
  (response) => {
    // Normalize array responses - ensure arrays are always arrays
    if (response.data && typeof response.data === 'object' && !Array.isArray(response.data)) {
      // If it's an object but we expect an array (based on endpoint), convert to array
      const url = response.config.url || '';
      if (url.includes('/active-phases') || 
          url.includes('/phases-needing-attention') || 
          url.includes('/active-phases-by-customer') ||
          url.includes('/recent-activity')) {
        // If response is not an array, wrap it or return empty array
        if (!Array.isArray(response.data)) {
          console.warn(`API returned non-array for ${url}:`, response.data);
          response.data = [];
        }
      }
    }
    return response;
  },
  (error) => {
    if (error.response?.status === 401) {
      // Handle unauthorized
      localStorage.removeItem('auth_token');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default apiClient;
