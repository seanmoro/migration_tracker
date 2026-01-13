import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { phasesApi } from '../api/phases';
import { projectsApi } from '../api/projects';
import { MigrationPhase } from '../types';
import { Plus, ArrowLeft, TrendingUp, Edit, Trash2 } from 'lucide-react';
import { formatDate } from '../utils/format';
import PhaseForm from '../components/PhaseForm';
import ProgressBar from '../components/ProgressBar';

export default function Phases() {
  const { projectId } = useParams<{ projectId: string }>();
  const navigate = useNavigate();
  const [showForm, setShowForm] = useState(false);
  const [editingPhase, setEditingPhase] = useState<MigrationPhase | null>(null);
  const queryClient = useQueryClient();

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
    },
  });

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
      <div className="flex items-center justify-between">
        <div>
          <button
            onClick={() => navigate('/projects')}
            className="flex items-center space-x-2 text-gray-600 hover:text-gray-900 mb-2"
          >
            <ArrowLeft className="w-4 h-4" />
            <span>Back to Projects</span>
          </button>
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
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {phases.map((phase) => (
            <div key={phase.id} className="card hover:shadow-md transition-shadow">
              <div className="flex items-start justify-between mb-4">
                <div>
                  <h3 className="text-lg font-semibold text-gray-900">{phase.name}</h3>
                  <p className="text-sm text-gray-600 mt-1">
                    {phase.source} → {phase.target}
                  </p>
                </div>
                <span className="px-2 py-1 bg-primary-100 text-primary-700 text-xs font-medium rounded">
                  {phase.type.replace('_', ' ')}
                </span>
              </div>

              {phase.targetTapePartition && (
                <div className="mb-4">
                  <span className="text-sm text-gray-600">Tape Partition: </span>
                  <span className="text-sm font-medium">{phase.targetTapePartition}</span>
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
                  onClick={() => navigate(`/phases/${phase.id}/progress`)}
                  className="flex-1 btn btn-primary flex items-center justify-center space-x-2"
                >
                  <TrendingUp className="w-4 h-4" />
                  <span>View Progress</span>
                </button>
                <button
                  onClick={() => handleEdit(phase)}
                  className="p-2 text-gray-600 hover:text-primary-600 hover:bg-primary-50 rounded"
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
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
