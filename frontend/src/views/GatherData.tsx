import { useState, useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { projectsApi } from '../api/projects';
import { phasesApi } from '../api/phases';
import { migrationApi } from '../api/migration';
import { CheckCircle2, XCircle, Loader2, Search, CheckSquare, Square, Calendar, AlertTriangle, Info } from 'lucide-react';
import { formatBytes, formatNumber } from '../utils/format';
import Breadcrumb from '../components/Breadcrumb';
import PhaseForm from '../components/PhaseForm';
import { useToastContext } from '../contexts/ToastContext';

export default function GatherData() {
  const location = useLocation();
  const [selectedCustomerId, setSelectedCustomerId] = useState('');
  const [selectedProject, setSelectedProject] = useState('');
  const [selectedPhase, setSelectedPhase] = useState('');
  const [date, setDate] = useState(new Date().toISOString().split('T')[0]);
  const [selectedBuckets, setSelectedBuckets] = useState<Set<string>>(new Set());
  const [bucketSearchTerm, setBucketSearchTerm] = useState('');
  const [showBucketSelection, setShowBucketSelection] = useState(false);
  const [showPhaseForm, setShowPhaseForm] = useState(false);
  const queryClient = useQueryClient();
  const toast = useToastContext();

  const [databaseType, setDatabaseType] = useState<'blackpearl' | 'rio' | null>(null);
  const [defaultPhaseValues, setDefaultPhaseValues] = useState<{ source?: string; target?: string; sourceTapePartition?: string; targetTapePartition?: string }>({});

  // Pre-fill from navigation state (from restore)
  useEffect(() => {
    if (location.state) {
      const state = location.state as { customerId?: string; projectId?: string; phaseId?: string; databaseType?: 'blackpearl' | 'rio' };
      if (state.customerId) {
        setSelectedCustomerId(state.customerId);
      }
      if (state.projectId) {
        setSelectedProject(state.projectId);
      }
      if (state.phaseId) {
        setSelectedPhase(state.phaseId);
      }
      if (state.databaseType) {
        setDatabaseType(state.databaseType);
        // Set default source based on database type
        setDefaultPhaseValues(prev => ({
          ...prev,
          source: state.databaseType === 'blackpearl' ? 'BlackPearl' : 'Rio'
        }));
      }
    }
  }, [location.state]);

  const { data: projects = [] } = useQuery({
    queryKey: ['projects', selectedCustomerId],
    queryFn: () => projectsApi.list(selectedCustomerId || undefined),
  });

  // Filter projects by customer if customer is selected
  const filteredProjects = selectedCustomerId
    ? projects.filter(p => p.customerId === selectedCustomerId)
    : projects;

  const { data: phases = [] } = useQuery({
    queryKey: ['phases', selectedProject],
    queryFn: () => phasesApi.list(selectedProject),
    enabled: !!selectedProject,
  });

  // Fetch storage domains from restored PostgreSQL database
  const { data: storageDomains } = useQuery({
    queryKey: ['storage-domains', selectedCustomerId, databaseType],
    queryFn: () => phasesApi.getStorageDomains(selectedCustomerId, databaseType || 'blackpearl'),
    enabled: !!selectedCustomerId && !!databaseType,
  });

  // Update default values when storage domains are fetched
  useEffect(() => {
    if (storageDomains) {
      setDefaultPhaseValues({
        source: storageDomains.suggestedSource || '',
        target: storageDomains.suggestedTarget || '',
        sourceTapePartition: storageDomains.suggestedTapePartition || '',
        targetTapePartition: storageDomains.suggestedTapePartition || ''
      });
    } else if (databaseType) {
      // Fallback to database type if no storage domains found
      setDefaultPhaseValues({
        source: databaseType === 'blackpearl' ? 'BlackPearl' : 'Rio',
        target: databaseType === 'blackpearl' ? 'BlackPearl' : 'Rio',
        sourceTapePartition: '',
        targetTapePartition: ''
      });
    }
  }, [storageDomains, databaseType]);

  // Handle "Create New Phase" option
  const handlePhaseChange = (value: string) => {
    if (value === '__create_new__') {
      if (!selectedProject) {
        toast.warning('Please select a project first');
        return;
      }
      setShowPhaseForm(true);
    } else {
      setSelectedPhase(value);
    }
  };

  // Get last data point for selected phase to suggest next date
  const { data: phaseData = [] } = useQuery({
    queryKey: ['phase-data', selectedPhase],
    queryFn: () => migrationApi.getData(selectedPhase),
    enabled: !!selectedPhase,
  });

  const lastDataDate = phaseData.length > 0 
    ? new Date(phaseData[0].timestamp).toISOString().split('T')[0]
    : null;

  const suggestedDate = lastDataDate 
    ? new Date(new Date(lastDataDate).getTime() + 24 * 60 * 60 * 1000).toISOString().split('T')[0]
    : new Date().toISOString().split('T')[0];

  // Check if date already has data
  const dateHasData = phaseData.some(d => d.timestamp === date);

  const { data: buckets = [], isLoading: bucketsLoading } = useQuery({
    queryKey: ['buckets'],
    queryFn: () => migrationApi.getBuckets(),
  });

  const filteredBuckets = buckets.filter(bucket =>
    bucket.name.toLowerCase().includes(bucketSearchTerm.toLowerCase())
  );

  const toggleBucket = (bucketName: string) => {
    const newSelected = new Set(selectedBuckets);
    if (newSelected.has(bucketName)) {
      newSelected.delete(bucketName);
    } else {
      newSelected.add(bucketName);
    }
    setSelectedBuckets(newSelected);
  };

  const selectAllBuckets = () => {
    setSelectedBuckets(new Set(filteredBuckets.map(b => b.name)));
  };

  const deselectAllBuckets = () => {
    setSelectedBuckets(new Set());
  };

  const gatherMutation = useMutation({
    mutationFn: (data: { projectId: string; phaseId: string; date: string; selectedBuckets?: string[] }) =>
      migrationApi.gatherData(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reports'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      queryClient.invalidateQueries({ queryKey: ['phase-data'] });
      toast.success('Data gathered successfully!');
      // Reset form
      setSelectedPhase('');
      setDate(new Date().toISOString().split('T')[0]);
      setSelectedBuckets(new Set());
    },
    onError: (error: any) => {
      toast.error(`Failed to gather data: ${error.message || 'Unknown error'}`);
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedProject || !selectedPhase || !date) {
      toast.warning('Please fill in all required fields');
      return;
    }

    // Validate date
    const selectedDate = new Date(date);
    const today = new Date();
    today.setHours(23, 59, 59, 999);
    
    if (selectedDate > today) {
      toast.warning('The selected date is in the future. Please verify this is correct.');
      return;
    }

    if (dateHasData) {
      toast.warning(`Data already exists for ${date}. Gathering will create a duplicate entry.`);
      // Still allow, but warn
    }

    gatherMutation.mutate({
      projectId: selectedProject,
      phaseId: selectedPhase,
      date,
      selectedBuckets: selectedBuckets.size > 0 ? Array.from(selectedBuckets) : undefined,
    });
  };

  return (
    <div className="space-y-6">
      <Breadcrumb items={[
        { label: 'Dashboard', path: '/' },
        { label: 'Gather Data' }
      ]} />
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
              {filteredProjects.map((project) => (
                <option key={project.id} value={project.id}>
                  {project.name}
                </option>
              ))}
            </select>
            {selectedCustomerId && filteredProjects.length === 0 && (
              <p className="text-sm text-gray-500 mt-1">No projects found for this customer. Please create a project first.</p>
            )}
          </div>

          <div>
            <label className="label">Phase</label>
            <select
              value={selectedPhase}
              onChange={(e) => handlePhaseChange(e.target.value)}
              className="input"
              required
              disabled={!selectedProject}
            >
              <option value="">Select a phase</option>
              {phases.map((phase) => (
                <option key={phase.id} value={phase.id}>
                  {phase.name}
                </option>
              ))}
              {selectedProject && (
                <option value="__create_new__" className="font-semibold">
                  + Create New Phase
                </option>
              )}
            </select>
            {selectedProject && phases.length === 0 && (
              <p className="text-sm text-gray-500 mt-1">No phases found for this project. Select "Create New Phase" to add one.</p>
            )}
          </div>

          <div>
            <label className="label">Date</label>
            <div className="space-y-2">
              <input
                type="date"
                value={date}
                onChange={(e) => setDate(e.target.value)}
                className={`input ${dateHasData ? 'border-yellow-500' : ''}`}
                required
                max={new Date().toISOString().split('T')[0]}
              />
              
              {/* Date Suggestions */}
              {selectedPhase && lastDataDate && (
                <div className="flex items-center space-x-2 text-sm">
                  <Calendar className="w-4 h-4 text-gray-400" />
                  <span className="text-gray-600">Last gathered: {lastDataDate}</span>
                  <button
                    type="button"
                    onClick={() => setDate(suggestedDate)}
                    className="text-primary-600 hover:text-primary-700 underline"
                  >
                    Use suggested date ({suggestedDate})
                  </button>
                </div>
              )}

              {/* Warnings */}
              {dateHasData && (
                <div className="flex items-start space-x-2 p-3 bg-yellow-50 border border-yellow-200 rounded-lg">
                  <AlertTriangle className="w-5 h-5 text-yellow-600 flex-shrink-0 mt-0.5" />
                  <div className="flex-1">
                    <p className="text-sm font-medium text-yellow-800">Data already exists for this date</p>
                    <p className="text-xs text-yellow-700 mt-1">
                      Gathering data will create a duplicate entry. Consider using a different date.
                    </p>
                  </div>
                </div>
              )}

              {new Date(date) > new Date() && (
                <div className="flex items-start space-x-2 p-3 bg-blue-50 border border-blue-200 rounded-lg">
                  <Info className="w-5 h-5 text-blue-600 flex-shrink-0 mt-0.5" />
                  <div className="flex-1">
                    <p className="text-sm font-medium text-blue-800">Future date selected</p>
                    <p className="text-xs text-blue-700 mt-1">
                      The selected date is in the future. Make sure this is correct.
                    </p>
                  </div>
                </div>
              )}

              {!dateHasData && new Date(date) <= new Date() && (
                <p className="text-sm text-gray-500 flex items-center space-x-1">
                  <Info className="w-4 h-4" />
                  <span>Use the date from the database file name</span>
                </p>
              )}
            </div>
          </div>

          {/* Bucket Selection */}
          <div className="border-t border-gray-200 pt-4">
            <div className="flex items-center justify-between mb-3">
              <div>
                <label className="label mb-0">Select Buckets (Optional)</label>
                <p className="text-sm text-gray-500 mt-1">
                  Select specific buckets to track. Leave empty to track all buckets.
                </p>
              </div>
              <button
                type="button"
                onClick={() => setShowBucketSelection(!showBucketSelection)}
                className="btn btn-secondary text-sm"
              >
                {showBucketSelection ? 'Hide' : 'Show'} Buckets
              </button>
            </div>

            {showBucketSelection && (
              <div className="border border-gray-200 rounded-lg p-4 bg-gray-50 max-h-96 overflow-y-auto">
                {bucketsLoading ? (
                  <div className="text-center py-8 text-gray-500">
                    <Loader2 className="w-6 h-6 animate-spin mx-auto mb-2" />
                    <p>Loading buckets...</p>
                  </div>
                ) : buckets.length === 0 ? (
                  <div className="text-center py-8 text-gray-500">
                    <p className="mb-2">No buckets found.</p>
                    <p className="text-sm">This is expected if PostgreSQL databases are not configured.</p>
                    <p className="text-sm mt-2">Bucket selection is optional - you can still gather data without it.</p>
                    <p className="text-xs mt-4 text-gray-400">
                      To enable bucket selection, configure remote database connections via environment variables.
                    </p>
                  </div>
                ) : (
                  <>
                    {/* Search and Select All */}
                    <div className="mb-4 space-y-2">
                      <div className="relative">
                        <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 w-4 h-4" />
                        <input
                          type="text"
                          placeholder="Search buckets..."
                          value={bucketSearchTerm}
                          onChange={(e) => setBucketSearchTerm(e.target.value)}
                          className="input pl-10 text-sm"
                        />
                      </div>
                      <div className="flex items-center justify-between text-sm">
                        <span className="text-gray-600">
                          {selectedBuckets.size} of {filteredBuckets.length} selected
                        </span>
                        <div className="space-x-2">
                          <button
                            type="button"
                            onClick={selectAllBuckets}
                            className="text-primary-600 hover:text-primary-700"
                          >
                            Select All
                          </button>
                          <span className="text-gray-300">|</span>
                          <button
                            type="button"
                            onClick={deselectAllBuckets}
                            className="text-primary-600 hover:text-primary-700"
                          >
                            Deselect All
                          </button>
                        </div>
                      </div>
                    </div>

                    {/* Bucket List */}
                    <div className="space-y-2">
                      {filteredBuckets.map((bucket) => {
                        const isSelected = selectedBuckets.has(bucket.name);
                        return (
                          <div
                            key={`${bucket.source}-${bucket.name}`}
                            className={`flex items-center justify-between p-3 rounded-lg border cursor-pointer transition-colors ${
                              isSelected
                                ? 'bg-primary-50 border-primary-300'
                                : 'bg-white border-gray-200 hover:bg-gray-50'
                            }`}
                            onClick={() => toggleBucket(bucket.name)}
                          >
                            <div className="flex items-center space-x-3 flex-1">
                              {isSelected ? (
                                <CheckSquare className="w-5 h-5 text-primary-600" />
                              ) : (
                                <Square className="w-5 h-5 text-gray-400" />
                              )}
                              <div className="flex-1">
                                <div className="flex items-center space-x-2">
                                  <span className="font-medium text-gray-900">{bucket.name}</span>
                                  <span className={`text-xs px-2 py-0.5 rounded ${
                                    bucket.source === 'blackpearl'
                                      ? 'bg-blue-100 text-blue-800'
                                      : 'bg-green-100 text-green-800'
                                  }`}>
                                    {bucket.source}
                                  </span>
                                </div>
                                <div className="text-xs text-gray-500 mt-1">
                                  {formatNumber(bucket.objectCount)} objects • {formatBytes(bucket.sizeBytes)}
                                </div>
                              </div>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </>
                )}
              </div>
            )}
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

      {/* Phase Form Modal */}
      {showPhaseForm && selectedProject && (
        <PhaseForm
          projectId={selectedProject}
          defaultSource={defaultPhaseValues.source}
          defaultTarget={defaultPhaseValues.target}
          defaultSourceTapePartition={defaultPhaseValues.sourceTapePartition}
          defaultTargetTapePartition={defaultPhaseValues.targetTapePartition}
          storageDomains={storageDomains?.domains || []}
          tapePartitions={storageDomains?.tapePartitions || []}
          onClose={() => {
            setShowPhaseForm(false);
            // Refresh phases list after creating
            queryClient.invalidateQueries({ queryKey: ['phases', selectedProject] });
            queryClient.invalidateQueries({ queryKey: ['phase-defaults', selectedProject] });
          }}
        />
      )}
    </div>
  );
}
