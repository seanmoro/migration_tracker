import apiClient from './client';
import { CustomerPhases, DashboardStats, PhaseProgress } from '../types';

export const dashboardApi = {
  getStats: async (): Promise<DashboardStats> => {
    const response = await apiClient.get('/dashboard/stats');
    return response.data;
  },

  getActivePhases: async (): Promise<PhaseProgress[]> => {
    try {
      const response = await apiClient.get('/dashboard/active-phases');
      if (Array.isArray(response.data)) {
        return response.data;
      }
      console.warn('API returned non-array for active-phases:', response.data);
      return [];
    } catch (error) {
      console.error('Error fetching active phases:', error);
      return [];
    }
  },

  getRecentActivity: async (): Promise<any[]> => {
    try {
      const response = await apiClient.get('/dashboard/recent-activity');
      if (Array.isArray(response.data)) {
        return response.data;
      }
      console.warn('API returned non-array for recent-activity:', response.data);
      return [];
    } catch (error) {
      console.error('Error fetching recent activity:', error);
      return [];
    }
  },

  getPhasesNeedingAttention: async (): Promise<PhaseProgress[]> => {
    try {
      const response = await apiClient.get('/dashboard/phases-needing-attention');
      if (Array.isArray(response.data)) {
        return response.data;
      }
      console.warn('API returned non-array for phases-needing-attention:', response.data);
      return [];
    } catch (error) {
      console.error('Error fetching phases needing attention:', error);
      return [];
    }
  },

  getActivePhasesByCustomer: async (): Promise<CustomerPhases[]> => {
    try {
      const response = await apiClient.get('/dashboard/active-phases-by-customer');
      if (Array.isArray(response.data)) {
        // Also validate nested structure
        return response.data.map((customer: any) => ({
          ...customer,
          projects: Array.isArray(customer.projects) ? customer.projects.map((project: any) => ({
            ...project,
            phases: Array.isArray(project.phases) ? project.phases : []
          })) : []
        }));
      }
      console.warn('API returned non-array for active-phases-by-customer:', response.data);
      return [];
    } catch (error) {
      console.error('Error fetching phases by customer:', error);
      return [];
    }
  },
};
