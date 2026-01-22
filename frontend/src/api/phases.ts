import apiClient from './client';
import { MigrationPhase } from '../types';

export const phasesApi = {
  list: async (projectId: string, includeInactive = false): Promise<MigrationPhase[]> => {
    const response = await apiClient.get('/phases', {
      params: { projectId, includeInactive },
    });
    // Normalize: Backend returns "projectId" in JSON, but we use "migrationId" in TypeScript
    // Map projectId to migrationId for consistency
    const phases = Array.isArray(response.data) ? response.data : [];
    return phases.map((phase: any) => ({
      ...phase,
      migrationId: phase.projectId || phase.migrationId, // Use projectId if present, fallback to migrationId
    }));
  },

  get: async (id: string): Promise<MigrationPhase> => {
    const response = await apiClient.get(`/phases/${id}`);
    // Normalize: Backend returns "projectId" in JSON, but we use "migrationId" in TypeScript
    const phase = response.data;
    return {
      ...phase,
      migrationId: phase.projectId || phase.migrationId,
    };
  },

  create: async (data: {
    name: string;
    projectId: string;
    type: MigrationPhase['type'];
    source: string;
    target: string;
    sourceTapePartition?: string;
    targetTapePartition?: string;
  }): Promise<MigrationPhase> => {
    const response = await apiClient.post('/phases', data);
    // Normalize: Backend returns "projectId" in JSON, but we use "migrationId" in TypeScript
    const phase = response.data;
    return {
      ...phase,
      migrationId: phase.projectId || phase.migrationId,
    };
  },

  update: async (id: string, data: Partial<MigrationPhase>): Promise<MigrationPhase> => {
    const response = await apiClient.put(`/phases/${id}`, data);
    // Normalize: Backend returns "projectId" in JSON, but we use "migrationId" in TypeScript
    const phase = response.data;
    return {
      ...phase,
      migrationId: phase.projectId || phase.migrationId,
    };
  },

  delete: async (id: string): Promise<void> => {
    await apiClient.delete(`/phases/${id}`);
  },

  search: async (projectId: string, name: string, includeInactive = false): Promise<MigrationPhase[]> => {
    const response = await apiClient.get('/phases/search', {
      params: { projectId, name, includeInactive },
    });
    // Normalize: Backend returns "projectId" in JSON, but we use "migrationId" in TypeScript
    const phases = Array.isArray(response.data) ? response.data : [];
    return phases.map((phase: any) => ({
      ...phase,
      migrationId: phase.projectId || phase.migrationId,
    }));
  },

  getDefaultValues: async (projectId: string): Promise<{ source?: string; target?: string; sourceTapePartition?: string; targetTapePartition?: string }> => {
    const response = await apiClient.get('/phases/defaults', {
      params: { projectId },
    });
    return response.data || {};
  },

  getStorageDomains: async (customerId: string, databaseType: 'blackpearl' | 'rio'): Promise<{
    domains: string[];
    tapePartitions?: string[];
    suggestedSource?: string;
    suggestedTarget?: string;
    suggestedTapePartition?: string;
  }> => {
    const response = await apiClient.get('/phases/storage-domains', {
      params: { customerId, databaseType },
    });
    return response.data || { domains: [], tapePartitions: [] };
  },

  toggleStatus: async (id: string, active: boolean): Promise<MigrationPhase> => {
    const response = await apiClient.patch(`/phases/${id}/status`, null, {
      params: { active },
    });
    // Normalize: Backend returns "projectId" in JSON, but we use "migrationId" in TypeScript
    const phase = response.data;
    return {
      ...phase,
      migrationId: phase.projectId || phase.migrationId,
    };
  },
};
