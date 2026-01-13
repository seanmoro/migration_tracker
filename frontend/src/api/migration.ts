import apiClient from './client';
import { MigrationData } from '../types';

export const migrationApi = {
  gatherData: async (data: {
    projectId: string;
    phaseId: string;
    date: string;
  }): Promise<MigrationData> => {
    const response = await apiClient.post('/migration/gather-data', data);
    return response.data;
  },

  getData: async (phaseId: string): Promise<MigrationData[]> => {
    const response = await apiClient.get(`/migration/data`, {
      params: { phaseId },
    });
    return response.data;
  },
};
