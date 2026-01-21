import apiClient from './client';
import { MigrationProject } from '../types';

export const projectsApi = {
  list: async (customerId?: string): Promise<MigrationProject[]> => {
    try {
      const params = customerId ? { customerId } : {};
      const response = await apiClient.get('/projects', { params });
      if (Array.isArray(response.data)) {
        return response.data;
      }
      console.warn('API returned non-array for projects:', response.data);
      return [];
    } catch (error) {
      console.error('Error fetching projects:', error);
      return [];
    }
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
    try {
      const response = await apiClient.get('/projects/search', {
        params: { name },
      });
      if (Array.isArray(response.data)) {
        return response.data;
      }
      console.warn('API returned non-array for project search:', response.data);
      return [];
    } catch (error) {
      console.error('Error searching projects:', error);
      return [];
    }
  },

  toggleStatus: async (id: string, active: boolean): Promise<MigrationProject> => {
    const response = await apiClient.patch(`/projects/${id}/status`, null, {
      params: { active },
    });
    return response.data;
  },
};
