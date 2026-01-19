import apiClient from './client';
import { CustomerPhases, DashboardStats, PhaseProgress } from '../types';

export const dashboardApi = {
  getStats: async (): Promise<DashboardStats> => {
    const response = await apiClient.get('/dashboard/stats');
    return response.data;
  },

  getActivePhases: async (): Promise<PhaseProgress[]> => {
    const response = await apiClient.get('/dashboard/active-phases');
    return Array.isArray(response.data) ? response.data : [];
  },

  getRecentActivity: async (): Promise<any[]> => {
    const response = await apiClient.get('/dashboard/recent-activity');
    return Array.isArray(response.data) ? response.data : [];
  },

  getPhasesNeedingAttention: async (): Promise<PhaseProgress[]> => {
    const response = await apiClient.get('/dashboard/phases-needing-attention');
    return Array.isArray(response.data) ? response.data : [];
  },

  getActivePhasesByCustomer: async (): Promise<CustomerPhases[]> => {
    const response = await apiClient.get('/dashboard/active-phases-by-customer');
    return Array.isArray(response.data) ? response.data : [];
  },
};
