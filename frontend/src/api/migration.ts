import apiClient from './client';
import { Bucket, BucketData, MigrationData } from '../types';

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

  getBucketsForCustomer: async (customerId: string, databaseType: 'blackpearl' | 'rio' = 'blackpearl'): Promise<Bucket[]> => {
    try {
      const response = await apiClient.get('/migration/buckets/customer', {
        params: { customerId, databaseType },
      });
      if (Array.isArray(response.data)) {
        return response.data;
      }
      console.warn('API returned non-array for customer buckets:', response.data);
      return [];
    } catch (error) {
      console.error('Error fetching customer buckets:', error);
      return [];
    }
  },

  getBucketSize: async (customerId: string, bucketName: string, databaseType: 'blackpearl' | 'rio' = 'blackpearl'): Promise<Bucket | null> => {
    try {
      const response = await apiClient.get('/migration/buckets/size', {
        params: { customerId, bucketName, databaseType },
      });
      return response.data;
    } catch (error: any) {
      if (error.response?.status === 404) {
        return null;
      }
      console.error('Error fetching bucket size:', error);
      throw error;
    }
  },

  getBucketData: async (phaseId: string, bucketName?: string, dateFrom?: string, dateTo?: string): Promise<BucketData[]> => {
    try {
      const params: any = { phaseId };
      if (bucketName) params.bucketName = bucketName;
      if (dateFrom) params.dateFrom = dateFrom;
      if (dateTo) params.dateTo = dateTo;
      
      const response = await apiClient.get('/migration/bucket-data', { params });
      if (Array.isArray(response.data)) {
        return response.data;
      }
      console.warn('API returned non-array for bucket data:', response.data);
      return [];
    } catch (error) {
      console.error('Error fetching bucket data:', error);
      return [];
    }
  },

  deleteDataPoint: async (phaseId: string, date: string): Promise<void> => {
    await apiClient.delete('/migration/data', {
      params: { phaseId, date },
    });
  },
};
