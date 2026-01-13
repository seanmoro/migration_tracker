import apiClient from './client';
import { MigrationData, PhaseProgress, Forecast, ExportOptions } from '../types';

export const reportsApi = {
  getPhaseProgress: async (phaseId: string): Promise<PhaseProgress> => {
    const response = await apiClient.get(`/reports/phases/${phaseId}/progress`);
    return response.data;
  },

  getPhaseData: async (phaseId: string, dateFrom?: string, dateTo?: string): Promise<MigrationData[]> => {
    const response = await apiClient.get(`/reports/phases/${phaseId}/data`, {
      params: { dateFrom, dateTo },
    });
    return response.data;
  },

  getForecast: async (phaseId: string): Promise<Forecast> => {
    const response = await apiClient.get(`/reports/phases/${phaseId}/forecast`);
    return response.data;
  },

  exportPhase: async (phaseId: string, options: ExportOptions): Promise<Blob> => {
    const response = await apiClient.post(
      `/reports/phases/${phaseId}/export`,
      options,
      { responseType: 'blob' }
    );
    return response.data;
  },

  exportProject: async (projectId: string, options: ExportOptions): Promise<Blob> => {
    const response = await apiClient.post(
      `/reports/projects/${projectId}/export`,
      options,
      { responseType: 'blob' }
    );
    return response.data;
  },

  exportCustomer: async (customerId: string, options: ExportOptions): Promise<Blob> => {
    const response = await apiClient.post(
      `/reports/customers/${customerId}/export`,
      options,
      { responseType: 'blob' }
    );
    return response.data;
  },
};
