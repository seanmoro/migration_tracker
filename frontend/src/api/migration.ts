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
    try {
      const response = await apiClient.get(`/migration/data`, {
        params: { phaseId },
      });
      if (Array.isArray(response.data)) {
        return response.data;
      }
      console.warn('API returned non-array for migration data:', response.data);
      return [];
    } catch (error) {
      console.error('Error fetching migration data:', error);
      return [];
    }
  },

  getBuckets: async (source?: 'blackpearl' | 'rio'): Promise<Bucket[]> => {
    try {
      const response = await apiClient.get('/migration/buckets', {
        params: source ? { source } : {},
      });
      if (Array.isArray(response.data)) {
        return response.data;
      }
      console.warn('API returned non-array for buckets:', response.data);
      return [];
    } catch (error) {
      console.error('Error fetching buckets:', error);
      return [];
    }
  },
};
