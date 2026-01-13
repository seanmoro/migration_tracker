import apiClient from './client';
import { CustomerPhases, DashboardStats, PhaseProgress } from '../types';

export const dashboardApi = {
  getStats: async (): Promise<DashboardStats> => {
    const response = await apiClient.get('/dashboard/stats');
    return response.data;
  },

  getActivePhases: async (): Promise<PhaseProgress[]> => {
    const response = await apiClient.get('/dashboard/active-phases');
    return response.data;
  },

  getRecentActivity: async (): Promise<any[]> => {
    const response = await apiClient.get('/dashboard/recent-activity');
    return response.data;
  },

  getPhasesNeedingAttention: async (): Promise<PhaseProgress[]> => {
    const response = await apiClient.get('/dashboard/phases-needing-attention');
    return response.data;
  },

  getActivePhasesByCustomer: async (): Promise<CustomerPhases[]> => {
    const response = await apiClient.get('/dashboard/active-phases-by-customer');
    return response.data;
  },
};
