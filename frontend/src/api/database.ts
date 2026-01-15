import apiClient from './client';

export interface DatabaseInfo {
  path: string;
  exists: boolean;
  size: number;
  lastModified: number;
}

export interface RestoreResponse {
  success: boolean;
  message?: string;
  error?: string;
  filename?: string;
  databaseType?: string;
  format?: string;
}

export const databaseApi = {
  /**
   * Upload and restore PostgreSQL database backup (BlackPearl or Rio)
   */
  restorePostgreSQLDatabase: async (file: File, databaseType: 'blackpearl' | 'rio'): Promise<RestoreResponse> => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('databaseType', databaseType);
    
    const response = await apiClient.post<RestoreResponse>('/database/restore-postgres', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    
    return response.data;
  },

  /**
   * Upload and restore SQLite tracker database backup
   */
  restoreDatabase: async (file: File): Promise<RestoreResponse> => {
    const formData = new FormData();
    formData.append('file', file);
    
    const response = await apiClient.post<RestoreResponse>('/database/restore', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    
    return response.data;
  },

  /**
   * Get database information
   */
  getDatabaseInfo: async (): Promise<DatabaseInfo> => {
    const response = await apiClient.get<DatabaseInfo>('/database/info');
    return response.data;
  },
};
