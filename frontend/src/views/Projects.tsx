import { useState, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { projectsApi } from '../api/projects';
import { customersApi } from '../api/customers';
import { phasesApi } from '../api/phases';
import { reportsApi } from '../api/reports';
import { MigrationProject } from '../types';
import { Plus, Search, Edit, Trash2 } from 'lucide-react';
import { formatDate } from '../utils/format';
import ProjectForm from '../components/ProjectForm';
import Breadcrumb from '../components/Breadcrumb';
import ProgressBar from '../components/ProgressBar';
import { useToastContext } from '../contexts/ToastContext';

export default function Projects() {
  const [searchTerm, setSearchTerm] = useState('');
  const [showInactive, setShowInactive] = useState(false);
  const [showForm, setShowForm] = useState(false);
  const [editingProject, setEditingProject] = useState<MigrationProject | null>(null);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const toast = useToastContext();

  const { data: projects = [], isLoading } = useQuery({
    queryKey: ['projects', searchTerm, showInactive],
    queryFn: () =>
      searchTerm
        ? projectsApi.search(searchTerm, showInactive)
        : projectsApi.list(undefined, showInactive),
  });

  const { data: customers = [] } = useQuery({
    queryKey: ['customers'],
    queryFn: () => customersApi.list(),
  });

  // Fetch phases for all projects (always include inactive to show all phases)
  const { data: allPhases = [], isLoading: phasesLoading } = useQuery({
    queryKey: ['all-phases', projects.map(p => p.id).sort().join(',')],
    queryFn: async () => {
      console.debug(`Fetching phases for ${projects.length} projects`);
      // Fetch phases for all projects in parallel
      // Always include inactive phases so we can show all phases for each project
      const phasePromises = projects.map(project =>
        phasesApi.list(project.id, true) // true = include inactive
          .then(phases => {
            // Log for debugging - log even if 0 phases
            console.debug(`Project ${project.name} (${project.id}): ${phases.length} phases returned`, phases);
            return phases.map(phase => ({ ...phase, _projectId: project.id })); // Add project ID for debugging
          })
          .catch((error) => {
            console.error(`Error fetching phases for project ${project.name} (${project.id}):`, error);
            return [];
          })
      );
      const results = await Promise.all(phasePromises);
      const allPhases = results.flat();
      console.debug(`Total phases fetched: ${allPhases.length} for ${projects.length} projects`, allPhases);
      return allPhases;
    },
    enabled: projects.length > 0,
  });

  // Fetch progress for all phases
  const phaseIds = useMemo(() => allPhases.map(p => p.id).sort().join(','), [allPhases]);
  const { data: phaseProgressMap = {} } = useQuery({
    queryKey: ['all-phases-progress', phaseIds],
    queryFn: async () => {
      if (allPhases.length === 0) return {};
      const progressPromises = allPhases.map(phase =>
        reportsApi.getPhaseProgress(phase.id)
          .then(progress => ({ phaseId: phase.id, progress }))
          .catch(() => ({ phaseId: phase.id, progress: null }))
      );
      const results = await Promise.all(progressPromises);
      const progressMap: Record<string, number> = {};
      results.forEach(({ phaseId, progress }) => {
        progressMap[phaseId] = progress?.progress ?? 0;
      });
      return progressMap;
    },
    enabled: allPhases.length > 0,
  });

  // Get phases for a specific project
  const getProjectPhases = (projectId: string) => {
    // Backend returns "projectId" in JSON due to @JsonProperty, but TypeScript interface has "migrationId"
    // Handle both field names for compatibility
    const phases = allPhases.filter(phase => {
      const phaseProjectId = (phase as any).projectId || phase.migrationId;
      return phaseProjectId === projectId;
    });
    if (phases.length === 0 && allPhases.length > 0) {
      // Debug: check if there's a mismatch
      console.debug(`No phases found for project ${projectId}. Looking for projectId=${projectId}`);
      console.debug(`Available phases:`, allPhases.map(p => ({ 
        id: p.id, 
        name: p.name, 
        migrationId: p.migrationId,
        projectId: (p as any).projectId,
        match: ((p as any).projectId || p.migrationId) === projectId
      })));
    }
    return phases;
  };

  // Get progress for a specific phase
  const getPhaseProgress = (phaseId: string): number => {
    return phaseProgressMap[phaseId] ?? 0;
  };

  const deleteMutation = useMutation({
    mutationFn: (id: string) => projectsApi.delete(id),
    onSuccess: () => {
      // Invalidate all project queries (with or without searchTerm)
      queryClient.invalidateQueries({ queryKey: ['projects'] });
      queryClient.invalidateQueries({ queryKey: ['all-phases'] });
      queryClient.invalidateQueries({ queryKey: ['all-phases-progress'] });
      // Force refetch to ensure UI updates
      queryClient.refetchQueries({ queryKey: ['projects'] });
      toast.success('Project deleted successfully');
    },
    onError: (error: any) => {
      toast.error(`Failed to delete project: ${error.message || 'Unknown error'}`);
    },
  });

  const handleEdit = (project: MigrationProject) => {
    setEditingProject(project);
    setShowForm(true);
  };

  const handleCreate = () => {
    setEditingProject(null);
    setShowForm(true);
  };

  const handleFormClose = () => {
    setShowForm(false);
    setEditingProject(null);
  };

  const getCustomerName = (customerId: string) => {
    return customers.find((c) => c.id === customerId)?.name || 'Unknown';
  };

  if (showForm) {
    return (
      <ProjectForm
        project={editingProject}
        customers={customers}
        onClose={handleFormClose}
      />
    );
  }

  return (
    <div className="space-y-6">
      <Breadcrumb items={[
        { label: 'Dashboard', path: '/' },
        { label: 'Projects' }
      ]} />
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Projects</h1>
          <p className="text-gray-600 mt-1">Manage migration projects</p>
        </div>
        <button
          onClick={handleCreate}
          className="btn btn-primary flex items-center space-x-2"
        >
          <Plus className="w-5 h-5" />
          <span>New Project</span>
        </button>
      </div>

      {/* Search and Filter */}
      <div className="flex items-center space-x-4">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 w-5 h-5" />
          <input
            type="text"
            placeholder="Search projects..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="input pl-10"
          />
        </div>
        <label className="flex items-center space-x-2 cursor-pointer">
          <input
            type="checkbox"
            checked={showInactive}
            onChange={(e) => setShowInactive(e.target.checked)}
            className="w-4 h-4 text-primary-600 border-gray-300 rounded focus:ring-primary-500"
          />
          <span className="text-sm text-gray-700">Show inactive</span>
        </label>
      </div>

      {/* Projects List */}
      {isLoading ? (
        <div className="text-center py-12 text-gray-500">Loading projects...</div>
      ) : projects.length === 0 ? (
        <div className="card text-center py-12">
          <p className="text-gray-500">No projects found</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {projects.map((project) => (
            <div key={project.id} className="card hover:shadow-md transition-shadow">
              <div className="flex items-start justify-between mb-4">
                <div>
                  <h3 className="text-lg font-semibold text-gray-900">{project.name}</h3>
                  <p className="text-sm text-gray-600 mt-1">
                    {getCustomerName(project.customerId)}
                  </p>
                </div>
                <span className="px-2 py-1 bg-primary-100 text-primary-700 text-xs font-medium rounded">
                  {project.type.replace('_', ' ')}
                </span>
              </div>

              <div className="flex items-center justify-between text-sm text-gray-600 mb-4">
                <span>Created: {formatDate(project.createdAt)}</span>
                <span
                  className={`px-2 py-1 rounded-full text-xs ${
                    project.active
                      ? 'bg-green-100 text-green-800'
                      : 'bg-red-100 text-red-800'
                  }`}
                >
                  {project.active ? 'Active' : 'Inactive'}
                </span>
              </div>

              {/* Phases List */}
              {(() => {
                if (phasesLoading) {
                  return (
                    <div className="mb-4 text-sm text-gray-500 text-center py-2">
                      Loading phases...
                    </div>
                  );
                }
                
                const projectPhases = getProjectPhases(project.id);
                // Filter by active status if showInactive is false
                // Include phases where active is true, null, or undefined (backward compatibility)
                const visiblePhases = showInactive 
                  ? projectPhases 
                  : projectPhases.filter(phase => phase.active === true || phase.active === null || phase.active === undefined);
                
                // Debug logging
                if (projectPhases.length > 0 && visiblePhases.length === 0) {
                  console.debug(`Project ${project.name} has ${projectPhases.length} total phases, but ${visiblePhases.length} visible (showInactive=${showInactive})`);
                  projectPhases.forEach(phase => {
                    console.debug(`  Phase ${phase.name}: active=${phase.active} (type: ${typeof phase.active})`);
                  });
                }
                
                if (visiblePhases.length === 0) {
                  return (
                    <div className="mb-4 text-sm text-gray-500 text-center py-2">
                      No phases yet
                    </div>
                  );
                }
                return (
                  <div className="mb-4 space-y-3">
                    {visiblePhases.map((phase) => (
                      <div key={phase.id} className={`border rounded p-2 hover:bg-gray-50 transition-colors ${
                        (phase.active === false) ? 'border-red-200 bg-red-50' : 'border-gray-200'
                      }`}>
                        <div className="flex items-center justify-between mb-1">
                          <div className="flex-1 min-w-0">
                            <h4 className="text-sm font-medium text-gray-900 truncate">{phase.name}</h4>
                            <p className="text-xs text-gray-600 truncate">{phase.source} → {phase.target}</p>
                          </div>
                          <div className="ml-2 flex items-center space-x-1 flex-shrink-0">
                            <span className="px-2 py-0.5 bg-primary-100 text-primary-700 text-xs font-medium rounded">
                              {phase.type.replace('_', ' ')}
                            </span>
                            {(phase.active === false) && (
                              <span className="px-2 py-0.5 bg-red-100 text-red-800 text-xs font-medium rounded">
                                Inactive
                              </span>
                            )}
                          </div>
                        </div>
                        <div className="mt-2">
                          <ProgressBar progress={getPhaseProgress(phase.id)} size="sm" label="Progress" showPercentage={true} />
                        </div>
                      </div>
                    ))}
                  </div>
                );
              })()}

              <div className="flex items-center space-x-2 pt-4 border-t border-gray-200">
                <button
                  onClick={() => navigate(`/projects/${project.id}/phases`)}
                  className="flex-1 btn btn-secondary text-sm"
                >
                  Manage Phases
                </button>
                <button
                  onClick={() => {
                    projectsApi.toggleStatus(project.id, !project.active)
                      .then(() => {
                        queryClient.invalidateQueries({ queryKey: ['projects'] });
                        toast.success(`Project marked as ${!project.active ? 'active' : 'inactive'}`);
                      })
                      .catch((error: any) => {
                        toast.error(`Failed to update status: ${error.message || 'Unknown error'}`);
                      });
                  }}
                  className={`px-2 py-1 text-xs rounded ${
                    project.active
                      ? 'bg-yellow-100 text-yellow-800 hover:bg-yellow-200'
                      : 'bg-green-100 text-green-800 hover:bg-green-200'
                  }`}
                  title={project.active ? 'Mark as inactive' : 'Mark as active'}
                >
                  {project.active ? 'Deactivate' : 'Activate'}
                </button>
                <button
                  onClick={() => handleEdit(project)}
                  className="p-2 text-gray-600 hover:text-primary-600 hover:bg-primary-50 rounded"
                >
                  <Edit className="w-4 h-4" />
                </button>
                <button
                  onClick={() => {
                    if (confirm('Are you sure you want to delete this project?')) {
                      deleteMutation.mutate(project.id);
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
