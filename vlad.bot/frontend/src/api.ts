import axios from 'axios';

const api = axios.create({
    baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8000',
    headers: {
        'Content-Type': 'application/json',
    },
});

// Request logging interceptor
api.interceptors.request.use(
    (config) => {
        console.log(`→ API Request: ${config.method?.toUpperCase()} ${config.url}`, {
            data: config.data,
            params: config.params
        });
        return config;
    },
    (error) => {
        console.error('→ API Request Error:', error);
        return Promise.reject(error);
    }
);

// Response logging interceptor
api.interceptors.response.use(
    (response) => {
        console.log(`← API Response: ${response.config.method?.toUpperCase()} ${response.config.url}`, {
            status: response.status,
            data: response.data
        });
        return response;
    },
    (error) => {
        const errorDetails = {
            message: error.message,
            url: error.config?.url,
            method: error.config?.method,
            status: error.response?.status,
            statusText: error.response?.statusText,
            data: error.response?.data,
            code: error.code,
        };

        console.error('✗ API Error:', errorDetails);

        // User-friendly error message
        if (!error.response) {
            console.error('✗ Network Error: Unable to connect to backend. Is the server running?');
        } else if (error.response.status === 401) {
            console.error('✗ Authentication Error: Invalid or expired token');
        } else if (error.response.status >= 500) {
            console.error('✗ Server Error: Backend returned error', error.response.data);
        }

        return Promise.reject(error);
    }
);

// Interceptor for Auth
api.interceptors.request.use((config) => {
    const token = localStorage.getItem('token');
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

export const botsApi = {
    list: () => api.get('/bots/').then(r => r.data),
    create: (data: { name: string; description?: string }) => api.post('/bots/', data).then(r => r.data),
    delete: (id: number) => api.delete(`/bots/${id}`).then(r => r.data),
    getResults: (id: number) => api.get(`/bots/${id}/results`).then(r => r.data),
};

export const scriptsApi = {
    getLatest: (botId: number) => api.get(`/bots/${botId}/scripts/latest`).then(r => r.data),
    saveDraft: (botId: number, graphData: any) => api.post(`/bots/${botId}/scripts/draft`, { graph_data: graphData }).then(r => r.data),
    publish: (botId: number) => api.post(`/bots/${botId}/scripts/publish`).then(r => r.data),
};

export const settingsApi = {
    get: () => api.get('/settings/').then(r => r.data),
    update: (settings: Record<string, string>) => api.post('/settings/', { settings }).then(r => r.data),
};

export default api;
