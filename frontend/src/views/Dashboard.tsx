import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { dashboardApi } from '../api/dashboard';
import StatCard from '../components/StatCard';
import ProgressChart from '../components/ProgressChart';
import Breadcrumb from '../components/Breadcrumb';
import { Activity, TrendingUp, AlertCircle, Database, ArrowRight, ChevronDown, ChevronRight, Plus } from 'lucide-react';
import { formatBytes, formatNumber } from '../utils/format';

export default function Dashboard() {
  const navigate = useNavigate();
  const [expandedCustomers, setExpandedCustomers] = useState<Set<string>>(new Set());
  const [expandedProjects, setExpandedProjects] = useState<Set<string>>(new Set());
  
  const { data: stats, isLoading: statsLoading } = useQuery({
    queryKey: ['dashboard', 'stats'],
    queryFn: () => dashboardApi.getStats(),
  });

  const { data: activePhases, isLoading: phasesLoading } = useQuery({
    queryKey: ['dashboard', 'active-phases'],
    queryFn: () => dashboardApi.getActivePhases(),
    retry: 1,
  });

  const { data: phasesByCustomer, isLoading: phasesByCustomerLoading } = useQuery({
    queryKey: ['dashboard', 'active-phases-by-customer'],
    queryFn: () => dashboardApi.getActivePhasesByCustomer(),
    retry: 1,
  });

  const { data: phasesNeedingAttention, isLoading: attentionLoading } = useQuery({
    queryKey: ['dashboard', 'phases-needing-attention'],
    queryFn: () => dashboardApi.getPhasesNeedingAttention(),
    retry: 1,
  });

  // Normalize data to ensure arrays
  const normalizedActivePhases = Array.isArray(activePhases) ? activePhases : [];
  const normalizedPhasesByCustomer = Array.isArray(phasesByCustomer) ? phasesByCustomer : [];
  const normalizedPhasesNeedingAttention = Array.isArray(phasesNeedingAttention) ? phasesNeedingAttention : [];

  const toggleCustomer = (customerId: string) => {
    const newExpanded = new Set(expandedCustomers);
    if (newExpanded.has(customerId)) {
      newExpanded.delete(customerId);
    } else {
      newExpanded.add(customerId);
    }
    setExpandedCustomers(newExpanded);
  };

  const toggleProject = (projectId: string) => {
    const newExpanded = new Set(expandedProjects);
    if (newExpanded.has(projectId)) {
      newExpanded.delete(projectId);
    } else {
      newExpanded.add(projectId);
    }
    setExpandedProjects(newExpanded);
  };

  if (statsLoading || phasesLoading || phasesByCustomerLoading || attentionLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-gray-500">Loading dashboard...</div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <Breadcrumb items={[{ label: 'Dashboard' }]} />
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Dashboard</h1>
          <p className="text-gray-600 mt-1">Overview of all migration activities</p>
        </div>
        <div className="flex items-center space-x-3">
          <button
            onClick={() => navigate('/gather-data')}
            className="btn btn-secondary flex items-center space-x-2"
          >
            <Database className="w-4 h-4" />
            <span>Gather Data</span>
          </button>
          <button
            onClick={() => navigate('/customers')}
            className="btn btn-primary flex items-center space-x-2"
          >
            <Plus className="w-4 h-4" />
            <span>New Migration</span>
          </button>
        </div>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <StatCard
          title="Active Migrations"
          value={stats?.activeMigrations || 0}
          icon={Activity}
          subtitle="view all phases"
          onClick={() => {
            const element = document.getElementById('active-phases');
            element?.scrollIntoView({ behavior: 'smooth' });
          }}
          clickable={!!(stats?.activeMigrations && stats.activeMigrations > 0)}
        />
        <StatCard
          title="Total Objects Migrated"
          value={formatNumber(stats?.totalObjectsMigrated || 0)}
          icon={Database}
        />
        <StatCard
          title="Average Progress"
          value={`${(stats?.averageProgress || 0).toFixed(2)}%`}
          icon={TrendingUp}
        />
        <StatCard
          title="Needs Attention"
          value={stats?.phasesNeedingAttention || 0}
          icon={AlertCircle}
          subtitle="phases requiring review"
          onClick={() => {
            const element = document.getElementById('phases-needing-attention');
            element?.scrollIntoView({ behavior: 'smooth' });
          }}
          clickable={!!(stats?.phasesNeedingAttention && stats.phasesNeedingAttention > 0)}
        />
      </div>

      {/* Phases Needing Attention */}
      {normalizedPhasesNeedingAttention.length > 0 && (
        <div id="phases-needing-attention" className="card border-l-4 border-l-red-500">
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center space-x-2">
              <AlertCircle className="w-5 h-5 text-red-600" />
              <h2 className="text-xl font-semibold text-gray-900">
                Phases Needing Attention ({normalizedPhasesNeedingAttention.length})
              </h2>
            </div>
          </div>
          <p className="text-sm text-gray-600 mb-4">
            These phases have progress below 50% and may require review or intervention.
          </p>
          <div className="space-y-4">
            {normalizedPhasesNeedingAttention.map((phase) => (
              <div
                key={phase.phaseId}
                className="border border-red-200 rounded-lg p-4 bg-red-50 hover:bg-red-100 transition-colors cursor-pointer"
                onClick={() => navigate(`/phases/${phase.phaseId}/progress`)}
              >
                <div className="flex items-center justify-between mb-2">
                  <h3 className="font-medium text-gray-900">{phase.phaseName}</h3>
                  <div className="flex items-center space-x-2">
                    <span className="text-sm font-semibold text-red-600">{phase.progress.toFixed(2)}%</span>
                    <ArrowRight className="w-4 h-4 text-red-600" />
                  </div>
                </div>
                <div className="w-full bg-gray-200 rounded-full h-2 mb-2">
                  <div
                    className="bg-red-600 h-2 rounded-full transition-all"
                    style={{ width: `${phase.progress}%` }}
                  />
                </div>
                <div className="flex justify-between text-sm text-gray-600">
                  <span>
                    {formatNumber(phase.targetObjects)} / {formatNumber(phase.sourceObjects)} objects migrated
                  </span>
                  <span className="text-red-600">
                    {formatNumber(phase.sourceObjects - phase.targetObjects)} remaining
                  </span>
                </div>
                <div className="mt-2 text-xs text-gray-500">
                  {phase.progress < 25 && '⚠️ Very low progress - may be stalled'}
                  {phase.progress >= 25 && phase.progress < 50 && '⚠️ Below target - review recommended'}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Active Phases by Customer */}
      <div id="active-phases" className="card">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-xl font-semibold text-gray-900">
            Active Phases ({stats?.activeMigrations || 0})
          </h2>
        </div>
        {normalizedPhasesByCustomer.length > 0 ? (
          <div className="space-y-2">
            {normalizedPhasesByCustomer.map((customer) => {
              const isCustomerExpanded = expandedCustomers.has(customer.customerId);
              const projects = Array.isArray(customer.projects) ? customer.projects : [];
              const totalPhases = projects.reduce((sum, p) => {
                const phases = Array.isArray(p.phases) ? p.phases : [];
                return sum + phases.length;
              }, 0);
              
              return (
                <div key={customer.customerId} className="border border-gray-200 rounded-lg">
                  {/* Customer Header */}
                  <div
                    className="flex items-center justify-between p-4 bg-gray-50 hover:bg-gray-100 cursor-pointer"
                    onClick={() => toggleCustomer(customer.customerId)}
                  >
                    <div className="flex items-center space-x-3">
                      {isCustomerExpanded ? (
                        <ChevronDown className="w-5 h-5 text-gray-600" />
                      ) : (
                        <ChevronRight className="w-5 h-5 text-gray-600" />
                      )}
                      <h3 className="font-semibold text-gray-900">{customer.customerName}</h3>
                      <span className="text-sm text-gray-600">({totalPhases} phases)</span>
                    </div>
                  </div>

                  {/* Projects and Phases */}
                  {isCustomerExpanded && (
                    <div className="p-2 space-y-2">
                      {projects.map((project) => {
                        const isProjectExpanded = expandedProjects.has(project.projectId);
                        const phases = Array.isArray(project.phases) ? project.phases : [];
                        
                        return (
                          <div key={project.projectId} className="border border-gray-200 rounded-lg bg-white">
                            {/* Project Header */}
                            <div
                              className="flex items-center justify-between p-3 hover:bg-gray-50 cursor-pointer"
                              onClick={() => toggleProject(project.projectId)}
                            >
                              <div className="flex items-center space-x-3">
                                {isProjectExpanded ? (
                                  <ChevronDown className="w-4 h-4 text-gray-500" />
                                ) : (
                                  <ChevronRight className="w-4 h-4 text-gray-500" />
                                )}
                                <h4 className="font-medium text-gray-800">{project.projectName}</h4>
                                <span className="text-sm text-gray-500">({phases.length} phases)</span>
                              </div>
                            </div>

                            {/* Phases List */}
                            {isProjectExpanded && (
                              <div className="p-2 space-y-2">
                                {phases.map((phase) => (
                                  <div
                                    key={phase.phaseId}
                                    className="border border-gray-200 rounded p-3 hover:bg-primary-50 transition-colors cursor-pointer"
                                    onClick={(e) => {
                                      e.stopPropagation();
                                      navigate(`/phases/${phase.phaseId}/progress`);
                                    }}
                                  >
                                    <div className="flex items-center justify-between mb-2">
                                      <h5 className="font-medium text-gray-900">{phase.phaseName}</h5>
                                      <div className="flex items-center space-x-2">
                                        <span className={`text-sm font-semibold ${
                                          phase.progress < 50 ? 'text-red-600' : phase.progress < 80 ? 'text-yellow-600' : 'text-primary-600'
                                        }`}>
                                          {phase.progress.toFixed(2)}%
                                        </span>
                                        <ArrowRight className="w-4 h-4 text-gray-400" />
                                      </div>
                                    </div>
                                    <div className="w-full bg-gray-200 rounded-full h-2 mb-2">
                                      <div
                                        className={`h-2 rounded-full transition-all ${
                                          phase.progress < 50 ? 'bg-red-600' : phase.progress < 80 ? 'bg-yellow-500' : 'bg-primary-600'
                                        }`}
                                        style={{ width: `${phase.progress}%` }}
                                      />
                                    </div>
                                    <div className="flex justify-between text-xs text-gray-600">
                                      <span>
                                        {formatNumber(phase.targetObjects)} / {formatNumber(phase.sourceObjects)} objects
                                      </span>
                                      <span>{formatBytes(phase.sourceSize)}</span>
                                    </div>
                                  </div>
                                ))}
                              </div>
                            )}
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        ) : (
          <p className="text-gray-500">No active phases</p>
        )}
      </div>

      {/* Progress Chart */}
      {normalizedActivePhases.length > 0 && (
        <div className="card">
          <h2 className="text-xl font-semibold text-gray-900 mb-4">Progress Overview</h2>
          <ProgressChart phases={normalizedActivePhases} />
        </div>
      )}
    </div>
  );
}
