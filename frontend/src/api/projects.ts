import apiClient from './client';
import { MigrationProject } from '../types';

export const projectsApi = {
  list: async (customerId?: string): Promise<MigrationProject[]> => {
    const params = customerId ? { customerId } : {};
    const response = await apiClient.get('/projects', { params });
    return response.data;
  },

  get: async (id: string): Promise<MigrationProject> => {
    const response = await apiClient.get(`/projects/${id}`);
    return response.data;
  },

  create: async (data: {
    name: string;
    customerId: string;
    type: MigrationProject['type'];
  }): Promise<MigrationProject> => {
    const response = await apiClient.post('/projects', data);
    return response.data;
  },

  update: async (id: string, data: Partial<MigrationProject>): Promise<MigrationProject> => {
    const response = await apiClient.put(`/projects/${id}`, data);
    return response.data;
  },

  delete: async (id: string): Promise<void> => {
    await apiClient.delete(`/projects/${id}`);
  },

  search: async (name: string): Promise<MigrationProject[]> => {
    const response = await apiClient.get('/projects/search', {
      params: { name },
    });
    return response.data;
  },
};
