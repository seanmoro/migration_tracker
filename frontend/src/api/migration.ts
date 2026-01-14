import apiClient from './client';
import { Bucket, MigrationData } from '../types';

export const migrationApi = {
  gatherData: async (data: {
    projectId: string;
    phaseId: string;
    date: string;
    selectedBuckets?: string[];
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

  getBuckets: async (source?: 'blackpearl' | 'rio'): Promise<Bucket[]> => {
    const response = await apiClient.get('/migration/buckets', {
      params: source ? { source } : {},
    });
    return response.data;
  },
};
