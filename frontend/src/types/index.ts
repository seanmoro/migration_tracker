export interface Customer {
  id: string;
  name: string;
  createdAt: string;
  lastUpdated: string;
  active: boolean;
}

export interface MigrationProject {
  id: string;
  name: string;
  customerId: string;
  type: 'IOM_BUCKET' | 'IOM_EXCLUSION' | 'RIO_CRUISE_DIVA' | 'RIO_CRUISE_SGL' | 'RIO_CRUISE_OTHER';
  createdAt: string;
  lastUpdated: string;
  active: boolean;
}

export interface MigrationPhase {
  id: string;
  name: string;
  type: 'IOM_BUCKET' | 'IOM_EXCLUSION' | 'RIO_CRUISE';
  migrationId: string;
  source: string;
  target: string;
  createdAt: string;
  lastUpdated: string;
  targetTapePartition?: string;
}

export interface MigrationData {
  id: string;
  createdAt: string;
  lastUpdated: string;
  timestamp: string;
  migrationPhaseId: string;
  userId?: string;
  sourceObjects: number;
  sourceSize: number;
  targetObjects: number;
  targetSize: number;
  type: 'REFERENCE' | 'DATA';
  targetScratchTapes?: number;
}

export interface PhaseProgress {
  phaseId: string;
  phaseName: string;
  progress: number; // 0-100
  sourceObjects: number;
  targetObjects: number;
  sourceSize: number;
  targetSize: number;
  eta?: string;
  confidence?: number;
  averageRate?: number;
}

export interface Forecast {
  eta: string;
  confidence: number;
  averageRate: number;
  remainingObjects: number;
  remainingSize: number;
}

export interface DashboardStats {
  activeMigrations: number;
  totalObjectsMigrated: number;
  averageProgress: number;
  phasesNeedingAttention: number;
}

export interface ProjectPhases {
  projectId: string;
  projectName: string;
  phases: PhaseProgress[];
}

export interface CustomerPhases {
  customerId: string;
  customerName: string;
  projects: ProjectPhases[];
}

export type ExportFormat = 'pdf' | 'excel' | 'csv' | 'json' | 'html';

export interface ExportOptions {
  format: ExportFormat;
  dateFrom?: string;
  dateTo?: string;
  includeCharts?: boolean;
  includeForecast?: boolean;
  includeRawData?: boolean;
  template?: 'executive' | 'detailed' | 'minimal';
}

export interface Bucket {
  name: string;
  source: 'blackpearl' | 'rio';
  objectCount: number;
  sizeBytes: number;
}
