import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { reportsApi } from '../api/reports';
import { phasesApi } from '../api/phases';
import { projectsApi } from '../api/projects';
import ProgressBar from '../components/ProgressBar';
import PhaseProgressChart from '../components/PhaseProgressChart';
import ExportButton from '../components/ExportButton';
import Breadcrumb from '../components/Breadcrumb';
import { formatBytes, formatNumber, formatDate } from '../utils/format';

export default function PhaseProgress() {
  const { phaseId } = useParams<{ phaseId: string }>();

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
    queryKey: ['projects', phase?.migrationId],
    queryFn: () => projectsApi.get(phase!.migrationId),
    enabled: !!phase?.migrationId,
  });

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
          <h3 className="text-sm font-medium text-gray-600 mb-2">Total Size</h3>
          <p className="text-3xl font-bold text-gray-900">
            {formatBytes(progress?.sourceSize || 0)}
          </p>
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
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
