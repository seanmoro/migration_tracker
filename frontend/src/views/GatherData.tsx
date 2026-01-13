import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { projectsApi } from '../api/projects';
import { phasesApi } from '../api/phases';
import { migrationApi } from '../api/migration';
import { CheckCircle2, XCircle, Loader2 } from 'lucide-react';

export default function GatherData() {
  const [selectedProject, setSelectedProject] = useState('');
  const [selectedPhase, setSelectedPhase] = useState('');
  const [date, setDate] = useState(new Date().toISOString().split('T')[0]);
  const queryClient = useQueryClient();

  const { data: projects = [] } = useQuery({
    queryKey: ['projects'],
    queryFn: () => projectsApi.list(),
  });

  const { data: phases = [] } = useQuery({
    queryKey: ['phases', selectedProject],
    queryFn: () => phasesApi.list(selectedProject),
    enabled: !!selectedProject,
  });

  const gatherMutation = useMutation({
    mutationFn: (data: { projectId: string; phaseId: string; date: string }) =>
      migrationApi.gatherData(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reports'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      alert('Data gathered successfully!');
    },
    onError: (error: any) => {
      alert(`Failed to gather data: ${error.message || 'Unknown error'}`);
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedProject || !selectedPhase || !date) {
      alert('Please fill in all fields');
      return;
    }
    gatherMutation.mutate({
      projectId: selectedProject,
      phaseId: selectedPhase,
      date,
    });
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold text-gray-900">Gather Migration Data</h1>
        <p className="text-gray-600 mt-1">Collect migration statistics from database</p>
      </div>

      <div className="card max-w-2xl">
        <form onSubmit={handleSubmit} className="space-y-6">
          <div>
            <label className="label">Project</label>
            <select
              value={selectedProject}
              onChange={(e) => {
                setSelectedProject(e.target.value);
                setSelectedPhase('');
              }}
              className="input"
              required
            >
              <option value="">Select a project</option>
              {projects.map((project) => (
                <option key={project.id} value={project.id}>
                  {project.name}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="label">Phase</label>
            <select
              value={selectedPhase}
              onChange={(e) => setSelectedPhase(e.target.value)}
              className="input"
              required
              disabled={!selectedProject || phases.length === 0}
            >
              <option value="">Select a phase</option>
              {phases.map((phase) => (
                <option key={phase.id} value={phase.id}>
                  {phase.name}
                </option>
              ))}
            </select>
            {selectedProject && phases.length === 0 && (
              <p className="text-sm text-gray-500 mt-1">No phases found for this project</p>
            )}
          </div>

          <div>
            <label className="label">Date</label>
            <input
              type="date"
              value={date}
              onChange={(e) => setDate(e.target.value)}
              className="input"
              required
            />
            <p className="text-sm text-gray-500 mt-1">
              Use the date from the database file name
            </p>
          </div>

          {/* Database Status */}
          <div className="border-t border-gray-200 pt-4">
            <h3 className="text-sm font-medium text-gray-700 mb-3">Database Status</h3>
            <div className="space-y-2">
              <div className="flex items-center space-x-2">
                <CheckCircle2 className="w-5 h-5 text-green-600" />
                <span className="text-sm text-gray-700">BlackPearl DB: Connected</span>
              </div>
              <div className="flex items-center space-x-2">
                <CheckCircle2 className="w-5 h-5 text-green-600" />
                <span className="text-sm text-gray-700">Rio DB: Connected</span>
              </div>
            </div>
          </div>

          <div className="flex items-center justify-end space-x-3 pt-4">
            <button
              type="button"
              onClick={() => {
                setSelectedProject('');
                setSelectedPhase('');
                setDate(new Date().toISOString().split('T')[0]);
              }}
              className="btn btn-secondary"
            >
              Reset
            </button>
            <button
              type="submit"
              disabled={gatherMutation.isPending || !selectedProject || !selectedPhase}
              className="btn btn-primary flex items-center space-x-2"
            >
              {gatherMutation.isPending ? (
                <>
                  <Loader2 className="w-5 h-5 animate-spin" />
                  <span>Gathering...</span>
                </>
              ) : (
                <span>Gather Data</span>
              )}
            </button>
          </div>
        </form>

        {/* Results */}
        {gatherMutation.isSuccess && (
          <div className="mt-6 p-4 bg-green-50 border border-green-200 rounded-lg">
            <div className="flex items-center space-x-2 text-green-800">
              <CheckCircle2 className="w-5 h-5" />
              <span className="font-medium">Data gathered successfully!</span>
            </div>
          </div>
        )}

        {gatherMutation.isError && (
          <div className="mt-6 p-4 bg-red-50 border border-red-200 rounded-lg">
            <div className="flex items-center space-x-2 text-red-800">
              <XCircle className="w-5 h-5" />
              <span className="font-medium">Failed to gather data</span>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
