import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { phasesApi } from '../api/phases';
import { projectsApi } from '../api/projects';
import { MigrationPhase } from '../types';
import { Plus, TrendingUp, Edit, Trash2, CheckSquare, Square, Copy, Database } from 'lucide-react';
import { formatDate } from '../utils/format';
import PhaseForm from '../components/PhaseForm';
import ProgressBar from '../components/ProgressBar';
import BulkActionBar from '../components/BulkActionBar';
import Breadcrumb from '../components/Breadcrumb';
import { useToastContext } from '../contexts/ToastContext';

export default function Phases() {
  const { projectId } = useParams<{ projectId: string }>();
  const navigate = useNavigate();
  const [showForm, setShowForm] = useState(false);
  const [editingPhase, setEditingPhase] = useState<MigrationPhase | null>(null);
  const [selectedPhases, setSelectedPhases] = useState<Set<string>>(new Set());
  const queryClient = useQueryClient();
  const toast = useToastContext();

  const { data: project } = useQuery({
    queryKey: ['projects', projectId],
    queryFn: () => projectsApi.get(projectId!),
    enabled: !!projectId,
  });

  const { data: phases = [], isLoading } = useQuery({
    queryKey: ['phases', projectId],
    queryFn: () => phasesApi.list(projectId!),
    enabled: !!projectId,
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => phasesApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['phases'] });
      toast.success('Phase deleted successfully');
    },
    onError: (error: any) => {
      toast.error(`Failed to delete phase: ${error.message || 'Unknown error'}`);
    },
  });

  const duplicateMutation = useMutation({
    mutationFn: async (phase: MigrationPhase) => {
      const duplicateData = {
        name: `${phase.name} (Copy)`,
        projectId: phase.migrationId,
        type: phase.type as MigrationPhase['type'],
        source: phase.source,
        target: phase.target,
        sourceTapePartition: phase.sourceTapePartition,
        targetTapePartition: phase.targetTapePartition,
      };
      return phasesApi.create(duplicateData);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['phases'] });
      toast.success('Phase duplicated successfully');
    },
    onError: (error: any) => {
      toast.error(`Failed to duplicate phase: ${error.message || 'Unknown error'}`);
    },
  });

  const handleDuplicate = (phase: MigrationPhase) => {
    duplicateMutation.mutate(phase);
  };

  const handleQuickGatherData = (phase: MigrationPhase) => {
    navigate('/gather-data', {
      state: {
        projectId: phase.migrationId,
        phaseId: phase.id,
      },
    });
  };

  const handleEdit = (phase: MigrationPhase) => {
    setEditingPhase(phase);
    setShowForm(true);
  };

  const handleCreate = () => {
    setEditingPhase(null);
    setShowForm(true);
  };

  const handleFormClose = () => {
    setShowForm(false);
    setEditingPhase(null);
  };

  const togglePhaseSelection = (phaseId: string) => {
    const newSelected = new Set(selectedPhases);
    if (newSelected.has(phaseId)) {
      newSelected.delete(phaseId);
    } else {
      newSelected.add(phaseId);
    }
    setSelectedPhases(newSelected);
  };

  const selectAllPhases = () => {
    setSelectedPhases(new Set(phases.map(p => p.id)));
  };

  const clearSelection = () => {
    setSelectedPhases(new Set());
  };

  const handleBulkGatherData = () => {
    if (selectedPhases.size === 0) return;
    // Navigate to gather data with pre-selected phases
    navigate('/gather-data', { 
      state: { 
        projectId, 
        phaseIds: Array.from(selectedPhases) 
      } 
    });
  };

  const handleBulkDelete = () => {
    if (selectedPhases.size === 0) return;
    if (!confirm(`Delete ${selectedPhases.size} phase(s)? This action cannot be undone.`)) {
      return;
    }
    Promise.all(Array.from(selectedPhases).map(id => deleteMutation.mutateAsync(id)))
      .then(() => {
        clearSelection();
      });
  };

  if (showForm && projectId) {
    return (
      <PhaseForm
        phase={editingPhase}
        projectId={projectId}
        onClose={handleFormClose}
      />
    );
  }

  return (
    <div className="space-y-6">
      <Breadcrumb items={[
        { label: 'Dashboard', path: '/' },
        { label: 'Projects', path: '/projects' },
        { label: project?.name || 'Project', path: `/projects/${projectId}/phases` },
        { label: 'Phases' }
      ]} />
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">
            {project?.name || 'Project'} - Phases
          </h1>
          <p className="text-gray-600 mt-1">Manage migration phases</p>
        </div>
        <button
          onClick={handleCreate}
          className="btn btn-primary flex items-center space-x-2"
        >
          <Plus className="w-5 h-5" />
          <span>New Phase</span>
        </button>
      </div>

      {isLoading ? (
        <div className="text-center py-12 text-gray-500">Loading phases...</div>
      ) : phases.length === 0 ? (
        <div className="card text-center py-12">
          <p className="text-gray-500">No phases found</p>
        </div>
      ) : (
        <>
          {/* Bulk Selection Controls */}
          <div className="flex items-center justify-between p-4 bg-gray-50 rounded-lg border border-gray-200">
            <div className="flex items-center space-x-4">
              <button
                onClick={selectedPhases.size === phases.length ? clearSelection : selectAllPhases}
                className="flex items-center space-x-2 text-sm text-gray-700 hover:text-gray-900"
              >
                {selectedPhases.size === phases.length ? (
                  <CheckSquare className="w-5 h-5 text-primary-600" />
                ) : (
                  <Square className="w-5 h-5 text-gray-400" />
                )}
                <span>
                  {selectedPhases.size === phases.length ? 'Deselect All' : 'Select All'}
                </span>
              </button>
              {selectedPhases.size > 0 && (
                <span className="text-sm text-gray-600">
                  {selectedPhases.size} of {phases.length} selected
                </span>
              )}
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {phases.map((phase) => {
              const isSelected = selectedPhases.has(phase.id);
              return (
                <div 
                  key={phase.id} 
                  className={`card hover:shadow-md transition-shadow ${
                    isSelected ? 'ring-2 ring-primary-500 bg-primary-50' : ''
                  }`}
                >
                  <div className="flex items-start justify-between mb-4">
                    <div className="flex items-start space-x-3 flex-1">
                      <button
                        onClick={() => togglePhaseSelection(phase.id)}
                        className="mt-1"
                      >
                        {isSelected ? (
                          <CheckSquare className="w-5 h-5 text-primary-600" />
                        ) : (
                          <Square className="w-5 h-5 text-gray-400 hover:text-gray-600" />
                        )}
                      </button>
                      <div className="flex-1">
                        <h3 className="text-lg font-semibold text-gray-900">{phase.name}</h3>
                        <p className="text-sm text-gray-600 mt-1">
                          {phase.source} → {phase.target}
                        </p>
                      </div>
                    </div>
                    <span className="px-2 py-1 bg-primary-100 text-primary-700 text-xs font-medium rounded">
                      {phase.type.replace('_', ' ')}
                    </span>
                  </div>

              {(phase.sourceTapePartition || phase.targetTapePartition) && (
                <div className="mb-4 space-y-1">
                  {phase.sourceTapePartition && (
                    <div>
                      <span className="text-sm text-gray-600">Source Tape Partition: </span>
                      <span className="text-sm font-medium">{phase.sourceTapePartition}</span>
                    </div>
                  )}
                  {phase.targetTapePartition && (
                    <div>
                      <span className="text-sm text-gray-600">Target Tape Partition: </span>
                      <span className="text-sm font-medium">{phase.targetTapePartition}</span>
                    </div>
                  )}
                </div>
              )}

              <div className="mb-4">
                <ProgressBar progress={0} label="Progress" />
              </div>

              <div className="flex items-center justify-between text-sm text-gray-600 mb-4">
                <span>Created: {formatDate(phase.createdAt)}</span>
              </div>

              <div className="flex items-center space-x-2 pt-4 border-t border-gray-200">
                <button
                  onClick={() => handleQuickGatherData(phase)}
                  className="flex-1 btn btn-primary flex items-center justify-center space-x-2"
                  title="Gather Data"
                >
                  <Database className="w-4 h-4" />
                  <span>Gather Data</span>
                </button>
                <button
                  onClick={() => navigate(`/phases/${phase.id}/progress`)}
                  className="p-2 text-gray-600 hover:text-primary-600 hover:bg-primary-50 rounded"
                  title="View Progress"
                >
                  <TrendingUp className="w-4 h-4" />
                </button>
                <button
                  onClick={() => handleDuplicate(phase)}
                  className="p-2 text-gray-600 hover:text-blue-600 hover:bg-blue-50 rounded"
                  title="Duplicate Phase"
                >
                  <Copy className="w-4 h-4" />
                </button>
                <button
                  onClick={() => handleEdit(phase)}
                  className="p-2 text-gray-600 hover:text-primary-600 hover:bg-primary-50 rounded"
                  title="Edit Phase"
                >
                  <Edit className="w-4 h-4" />
                </button>
                <button
                  onClick={() => {
                    if (confirm('Are you sure you want to delete this phase?')) {
                      deleteMutation.mutate(phase.id);
                    }
                  }}
                  className="p-2 text-gray-600 hover:text-red-600 hover:bg-red-50 rounded"
                  title="Delete Phase"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
                </div>
              );
            })}
          </div>

          {/* Bulk Action Bar */}
          <BulkActionBar
            count={selectedPhases.size}
            actions={[
              {
                label: `Gather Data (${selectedPhases.size})`,
                onClick: handleBulkGatherData,
                variant: 'primary',
              },
              {
                label: `Delete (${selectedPhases.size})`,
                onClick: handleBulkDelete,
                variant: 'danger',
              },
            ]}
            onClear={clearSelection}
          />
        </>
      )}
    </div>
  );
}
