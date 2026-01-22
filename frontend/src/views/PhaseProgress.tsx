import { useParams } from 'react-router-dom';
import { useQuery, useQueryClient, useMutation } from '@tanstack/react-query';
import { reportsApi } from '../api/reports';
import { phasesApi } from '../api/phases';
import { projectsApi } from '../api/projects';
import { migrationApi } from '../api/migration';
import ProgressBar from '../components/ProgressBar';
import PhaseProgressChart from '../components/PhaseProgressChart';
import ExportButton from '../components/ExportButton';
import Breadcrumb from '../components/Breadcrumb';
import { formatBytes, formatNumber, formatDate } from '../utils/format';
import { useToastContext } from '../contexts/ToastContext';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';

export default function PhaseProgress() {
  const { phaseId } = useParams<{ phaseId: string }>();
  const queryClient = useQueryClient();
  const toast = useToastContext();

  const { data: phase } = useQuery({
    queryKey: ['phases', phaseId],
    queryFn: () => phasesApi.get(phaseId!),
    enabled: !!phaseId,
  });

  const { data: progress } = useQuery({
    queryKey: ['reports', 'progress', phaseId],
    queryFn: () => reportsApi.getPhaseProgress(phaseId!),
    enabled: !!phaseId,
  });

  const { data: migrationData = [] } = useQuery({
    queryKey: ['reports', 'data', phaseId],
    queryFn: () => reportsApi.getPhaseData(phaseId!),
    enabled: !!phaseId,
  });

  const { data: forecast } = useQuery({
    queryKey: ['reports', 'forecast', phaseId],
    queryFn: () => reportsApi.getForecast(phaseId!),
    enabled: !!phaseId,
  });

  const { data: project } = useQuery({
    queryKey: ['projects', phase?.migrationId || (phase as any)?.projectId],
    queryFn: () => {
      const projectId = phase?.migrationId || (phase as any)?.projectId;
      if (!projectId) throw new Error('Phase has no project ID');
      return projectsApi.get(projectId);
    },
    enabled: !!(phase?.migrationId || (phase as any)?.projectId),
  });

  const { data: bucketData = [] } = useQuery({
    queryKey: ['bucket-data', phaseId],
    queryFn: () => migrationApi.getBucketData(phaseId!),
    enabled: !!phaseId,
  });

  const deleteMutation = useMutation({
    mutationFn: ({ phaseId, date }: { phaseId: string; date: string }) =>
      migrationApi.deleteDataPoint(phaseId, date),
    onSuccess: () => {
      // Invalidate all related queries to refresh the data
      queryClient.invalidateQueries({ queryKey: ['reports', 'data', phaseId] });
      queryClient.invalidateQueries({ queryKey: ['reports', 'progress', phaseId] });
      queryClient.invalidateQueries({ queryKey: ['reports', 'forecast', phaseId] });
      queryClient.invalidateQueries({ queryKey: ['bucket-data', phaseId] });
      queryClient.invalidateQueries({ queryKey: ['phase-data', phaseId] });
      toast.success('Data point deleted successfully');
    },
    onError: (error: any) => {
      toast.error(`Failed to delete data point: ${error.message}`);
    },
  });

  const handleDelete = (date: string) => {
    if (window.confirm(`Are you sure you want to delete the data point for ${formatDate(date)}?`)) {
      deleteMutation.mutate({ phaseId: phaseId!, date });
    }
  };

  if (!phase) {
    return <div>Loading...</div>;
  }

  return (
    <div className="space-y-6">
      <Breadcrumb items={[
        { label: 'Dashboard', path: '/' },
        { label: 'Projects', path: '/projects' },
        { label: project?.name || 'Project', path: `/projects/${phase.migrationId}/phases` },
        { label: 'Phases', path: `/projects/${phase.migrationId}/phases` },
        { label: phase.name }
      ]} />
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">{phase.name}</h1>
          <p className="text-gray-600 mt-1">
            {phase.source} → {phase.target}
          </p>
        </div>
        <ExportButton phaseId={phaseId!} />
      </div>

      {/* Progress Summary */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <div className="card">
          <h3 className="text-sm font-medium text-gray-600 mb-2">Progress</h3>
          <p className="text-3xl font-bold text-gray-900">{(progress?.progress || 0).toFixed(2)}%</p>
        </div>
        <div className="card">
          <h3 className="text-sm font-medium text-gray-600 mb-2">Source Objects</h3>
          <p className="text-3xl font-bold text-gray-900">
            {formatNumber(progress?.sourceObjects || 0)}
          </p>
        </div>
        <div className="card">
          <h3 className="text-sm font-medium text-gray-600 mb-2">Target Objects</h3>
          <p className="text-3xl font-bold text-gray-900">
            {formatNumber(progress?.targetObjects || 0)}
          </p>
        </div>
        <div className="card">
          <h3 className="text-sm font-medium text-gray-600 mb-2">Objects Remaining</h3>
          <p className="text-3xl font-bold text-gray-900">
            {formatNumber((progress?.sourceObjects || 0) - (progress?.targetObjects || 0))}
          </p>
        </div>
      </div>

      {/* Storage Domain Details */}
      <div className="card">
        <h2 className="text-xl font-semibold text-gray-900 mb-4">Storage Domain Details</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {/* Source Storage Domain */}
          <div className="border border-gray-200 rounded-lg p-4">
            <h3 className="text-lg font-semibold text-gray-900 mb-3">Source: {phase.source}</h3>
            <div className="space-y-3">
              <div className="flex justify-between items-center">
                <span className="text-sm text-gray-600">Objects</span>
                <span className="text-sm font-semibold text-gray-900">
                  {formatNumber(progress?.sourceObjects || 0)}
                </span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-sm text-gray-600">Data Size</span>
                <span className="text-sm font-semibold text-gray-900">
                  {formatBytes(progress?.sourceSize || 0)}
                </span>
              </div>
              {progress?.sourceTapeCount !== undefined && (
                <div className="flex justify-between items-center">
                  <span className="text-sm text-gray-600">Tapes</span>
                  <span className="text-sm font-semibold text-gray-900">
                    {formatNumber(progress.sourceTapeCount)}
                  </span>
                </div>
              )}
            </div>
          </div>

          {/* Target Storage Domain */}
          <div className="border border-gray-200 rounded-lg p-4">
            <h3 className="text-lg font-semibold text-gray-900 mb-3">Target: {phase.target}</h3>
            <div className="space-y-3">
              <div className="flex justify-between items-center">
                <span className="text-sm text-gray-600">Objects</span>
                <span className="text-sm font-semibold text-gray-900">
                  {formatNumber(progress?.targetObjects || 0)}
                </span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-sm text-gray-600">Data Size</span>
                <span className="text-sm font-semibold text-gray-900">
                  {formatBytes(progress?.targetSize || 0)}
                </span>
              </div>
              {progress?.targetTapeCount !== undefined && (
                <div className="flex justify-between items-center">
                  <span className="text-sm text-gray-600">Tapes</span>
                  <span className="text-sm font-semibold text-gray-900">
                    {formatNumber(progress.targetTapeCount)}
                  </span>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* Progress Bar */}
      <div className="card">
        <ProgressBar progress={progress?.progress || 0} size="lg" />
      </div>

      {/* Forecast */}
      {forecast && (
        <div className="card">
          <h2 className="text-xl font-semibold text-gray-900 mb-4">Forecast</h2>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div>
              <p className="text-sm text-gray-600">Estimated Completion</p>
              <p className="text-lg font-semibold text-gray-900">
                {formatDate(forecast.eta)}
              </p>
            </div>
            <div>
              <p className="text-sm text-gray-600">Confidence</p>
              <p className="text-lg font-semibold text-gray-900">{forecast.confidence}%</p>
            </div>
            <div>
              <p className="text-sm text-gray-600">Average Rate</p>
              <p className="text-lg font-semibold text-gray-900">
                {formatNumber(forecast.averageRate)} objects/day
              </p>
            </div>
          </div>
        </div>
      )}

      {/* Progress Chart */}
      {migrationData.length > 0 && (
        <div className="card">
          <h2 className="text-xl font-semibold text-gray-900 mb-4">Progress Over Time</h2>
          <PhaseProgressChart data={migrationData} />
        </div>
      )}

      {/* Bucket Size Trends */}
      {bucketData.length > 0 && (() => {
        // Group bucket data by bucket name
        const bucketsByName = new Map<string, typeof bucketData>();
        bucketData.forEach(bd => {
          const bucketName = bd.bucketName;
          if (!bucketsByName.has(bucketName)) {
            bucketsByName.set(bucketName, []);
          }
          bucketsByName.get(bucketName)!.push(bd);
        });

        // Sort by timestamp for each bucket
        bucketsByName.forEach((data) => {
          data.sort((a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime());
        });

        // Create chart data - one entry per timestamp with all bucket sizes
        const timestamps = new Set(bucketData.map(bd => bd.timestamp));
        const chartData = Array.from(timestamps).sort().map(timestamp => {
          const entry: any = { date: formatDate(timestamp) };
          bucketsByName.forEach((data, bucketName) => {
            const dataPoint = data.find(d => d.timestamp === timestamp);
            if (dataPoint) {
              entry[bucketName] = parseFloat((dataPoint.sizeBytes / (1024 * 1024 * 1024)).toFixed(2)); // GB for chart
              entry[`${bucketName}_bytes`] = dataPoint.sizeBytes; // Keep original for tooltip
            }
          });
          return entry;
        });

        // Get unique colors for buckets
        const colors = ['#0284c7', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899', '#14b8a6', '#f97316'];

        return (
          <div className="card">
            <h2 className="text-xl font-semibold text-gray-900 mb-4">Bucket Size Trends</h2>
            <ResponsiveContainer width="100%" height={400}>
              <LineChart data={chartData}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="date" />
                <YAxis tickFormatter={(value) => {
                  // Convert GB back to bytes for proper auto-scaling
                  const bytes = value * 1024 * 1024 * 1024;
                  return formatBytes(bytes);
                }} />
                <Tooltip 
                  formatter={(value: number, name: string, props: any) => {
                    // Recharts passes: value, name (bucket name), and props with payload
                    const payload = props?.payload;
                    if (payload) {
                      // Check for bucketName_bytes field
                      const bytesKey = `${name}_bytes`;
                      if (payload[bytesKey] !== undefined) {
                        return [formatBytes(payload[bytesKey]), name];
                      }
                    }
                    // Fallback: convert GB back to bytes (approximate)
                    const bytes = value * 1024 * 1024 * 1024;
                    return [formatBytes(bytes), name];
                  }}
                />
                <Legend />
                {Array.from(bucketsByName.keys()).map((bucketName, index) => (
                  <Line
                    key={bucketName}
                    type="monotone"
                    dataKey={bucketName}
                    stroke={colors[index % colors.length]}
                    name={bucketName}
                    strokeWidth={2}
                  />
                ))}
              </LineChart>
            </ResponsiveContainer>
            
            {/* Bucket Summary Table */}
            <div className="mt-6 overflow-x-auto">
              <h3 className="text-lg font-semibold text-gray-900 mb-3">Bucket Summary</h3>
              <table className="w-full">
                <thead>
                  <tr className="border-b border-gray-200">
                    <th className="text-left py-2 px-4 font-semibold text-gray-700">Bucket Name</th>
                    <th className="text-left py-2 px-4 font-semibold text-gray-700">Source</th>
                    <th className="text-right py-2 px-4 font-semibold text-gray-700">Latest Size</th>
                    <th className="text-right py-2 px-4 font-semibold text-gray-700">Latest Objects</th>
                    <th className="text-right py-2 px-4 font-semibold text-gray-700">Data Points</th>
                  </tr>
                </thead>
                <tbody>
                  {Array.from(bucketsByName.entries()).map(([bucketName, data]) => {
                    const latest = data[data.length - 1];
                    return (
                      <tr key={bucketName} className="border-b border-gray-100 hover:bg-gray-50">
                        <td className="py-2 px-4 font-medium">{bucketName}</td>
                        <td className="py-2 px-4">
                          <span className={`px-2 py-1 rounded text-xs ${
                            latest.source === 'blackpearl' ? 'bg-blue-100 text-blue-800' : 'bg-green-100 text-green-800'
                          }`}>
                            {latest.source === 'blackpearl' ? 'BlackPearl' : 'Rio'}
                          </span>
                        </td>
                        <td className="py-2 px-4 text-right">{formatBytes(latest.sizeBytes)}</td>
                        <td className="py-2 px-4 text-right">{formatNumber(latest.objectCount)}</td>
                        <td className="py-2 px-4 text-right">{data.length}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        );
      })()}

      {/* Data Points */}
      <div className="card">
        <h2 className="text-xl font-semibold text-gray-900 mb-4">Data Points</h2>
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-gray-200">
                <th className="text-left py-3 px-4 font-semibold text-gray-700">Date</th>
                <th className="text-left py-3 px-4 font-semibold text-gray-700">Source Objects</th>
                <th className="text-left py-3 px-4 font-semibold text-gray-700">Target Objects</th>
                <th className="text-left py-3 px-4 font-semibold text-gray-700">Source Size</th>
                <th className="text-left py-3 px-4 font-semibold text-gray-700">Type</th>
                <th className="text-left py-3 px-4 font-semibold text-gray-700">Actions</th>
              </tr>
            </thead>
            <tbody>
              {migrationData.map((data) => (
                <tr key={data.id} className="border-b border-gray-100 hover:bg-gray-50">
                  <td className="py-3 px-4">{formatDate(data.timestamp)}</td>
                  <td className="py-3 px-4">{formatNumber(data.sourceObjects)}</td>
                  <td className="py-3 px-4">{formatNumber(data.targetObjects)}</td>
                  <td className="py-3 px-4">{formatBytes(data.sourceSize)}</td>
                  <td className="py-3 px-4">
                    <span
                      className={`px-2 py-1 rounded-full text-xs ${
                        data.type === 'REFERENCE'
                          ? 'bg-blue-100 text-blue-800'
                          : 'bg-green-100 text-green-800'
                      }`}
                    >
                      {data.type}
                    </span>
                  </td>
                  <td className="py-3 px-4">
                    {data.type !== 'REFERENCE' && (
                      <button
                        onClick={() => handleDelete(data.timestamp)}
                        disabled={deleteMutation.isPending}
                        className="text-red-600 hover:text-red-800 disabled:opacity-50 disabled:cursor-not-allowed"
                        title="Delete data point"
                      >
                        <svg
                          className="w-5 h-5"
                          fill="none"
                          stroke="currentColor"
                          viewBox="0 0 24 24"
                        >
                          <path
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            strokeWidth={2}
                            d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"
                          />
                        </svg>
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
