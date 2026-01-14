import { useState, useEffect } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { phasesApi } from '../api/phases';
import { MigrationPhase } from '../types';
import { X } from 'lucide-react';
import { useToastContext } from '../contexts/ToastContext';

interface PhaseFormProps {
  phase?: MigrationPhase | null;
  projectId: string;
  onClose: () => void;
}

const PHASE_TYPES: MigrationPhase['type'][] = [
  'IOM_BUCKET',
  'IOM_EXCLUSION',
  'RIO_CRUISE',
];

export default function PhaseForm({ phase, projectId, onClose }: PhaseFormProps) {
  const [name, setName] = useState('');
  const [type, setType] = useState<MigrationPhase['type']>('IOM_BUCKET');
  const [source, setSource] = useState('');
  const [target, setTarget] = useState('');
  const [targetTapePartition, setTargetTapePartition] = useState('');
  const queryClient = useQueryClient();
  const toast = useToastContext();

  useEffect(() => {
    if (phase) {
      setName(phase.name);
      setType(phase.type);
      setSource(phase.source);
      setTarget(phase.target);
      setTargetTapePartition(phase.targetTapePartition || '');
    }
  }, [phase]);

  const mutation = useMutation({
    mutationFn: (data: {
      name: string;
      projectId: string;
      type: MigrationPhase['type'];
      source: string;
      target: string;
      targetTapePartition?: string;
    }) =>
      phase
        ? phasesApi.update(phase.id, data)
        : phasesApi.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['phases'] });
      toast.success(phase ? 'Phase updated successfully' : 'Phase created successfully');
      onClose();
    },
    onError: (error: any) => {
      toast.error(`Failed to ${phase ? 'update' : 'create'} phase: ${error.message || 'Unknown error'}`);
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    mutation.mutate({
      name,
      projectId,
      type,
      source,
      target,
      targetTapePartition: targetTapePartition || undefined,
    });
  };

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl max-w-md w-full mx-4">
        <div className="flex items-center justify-between p-6 border-b border-gray-200">
          <h2 className="text-xl font-semibold text-gray-900">
            {phase ? 'Edit Phase' : 'New Phase'}
          </h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          <div>
            <label className="label">Phase Name</label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="input"
              placeholder="Enter phase name"
              required
            />
          </div>

          <div>
            <label className="label">Phase Type</label>
            <select
              value={type}
              onChange={(e) => setType(e.target.value as MigrationPhase['type'])}
              className="input"
              required
            >
              {PHASE_TYPES.map((t) => (
                <option key={t} value={t}>
                  {t.replace('_', ' ')}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="label">Source</label>
            <input
              type="text"
              value={source}
              onChange={(e) => setSource(e.target.value)}
              className="input"
              placeholder="Storage domain or broker name"
              required
            />
          </div>

          <div>
            <label className="label">Target</label>
            <input
              type="text"
              value={target}
              onChange={(e) => setTarget(e.target.value)}
              className="input"
              placeholder="Storage domain or broker name"
              required
            />
          </div>

          <div>
            <label className="label">Tape Partition (Optional)</label>
            <input
              type="text"
              value={targetTapePartition}
              onChange={(e) => setTargetTapePartition(e.target.value)}
              className="input"
              placeholder="e.g., TAPE-01"
            />
          </div>

          <div className="flex items-center justify-end space-x-3 pt-4">
            <button type="button" onClick={onClose} className="btn btn-secondary">
              Cancel
            </button>
            <button
              type="submit"
              disabled={mutation.isPending}
              className="btn btn-primary"
            >
              {mutation.isPending ? 'Saving...' : phase ? 'Update' : 'Create'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
