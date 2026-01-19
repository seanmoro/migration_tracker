import apiClient from './client';
import { Customer } from '../types';

export const customersApi = {
  list: async (): Promise<Customer[]> => {
    try {
      const response = await apiClient.get('/customers');
      if (Array.isArray(response.data)) {
        return response.data;
      }
      console.warn('API returned non-array for customers:', response.data);
      return [];
    } catch (error) {
      console.error('Error fetching customers:', error);
      return [];
    }
  },

  get: async (id: string): Promise<Customer> => {
    const response = await apiClient.get(`/customers/${id}`);
    return response.data;
  },

  create: async (data: { name: string }): Promise<Customer> => {
    const response = await apiClient.post('/customers', data);
    return response.data;
  },

  update: async (id: string, data: Partial<Customer>): Promise<Customer> => {
    const response = await apiClient.put(`/customers/${id}`, data);
    return response.data;
  },

  delete: async (id: string): Promise<void> => {
    await apiClient.delete(`/customers/${id}`);
  },

  search: async (name: string): Promise<Customer[]> => {
    try {
      const response = await apiClient.get('/customers/search', {
        params: { name },
      });
      if (Array.isArray(response.data)) {
        return response.data;
      }
      console.warn('API returned non-array for customer search:', response.data);
      return [];
    } catch (error) {
      console.error('Error searching customers:', error);
      return [];
    }
  },
};
