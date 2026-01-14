import { useState, useEffect } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { projectsApi } from '../api/projects';
import { MigrationProject, Customer } from '../types';
import { X } from 'lucide-react';
import { useToastContext } from '../contexts/ToastContext';

interface ProjectFormProps {
  project?: MigrationProject | null;
  customers: Customer[];
  onClose: () => void;
}

const PROJECT_TYPES: MigrationProject['type'][] = [
  'IOM_BUCKET',
  'IOM_EXCLUSION',
  'RIO_CRUISE_DIVA',
  'RIO_CRUISE_SGL',
  'RIO_CRUISE_OTHER',
];

export default function ProjectForm({ project, customers, onClose }: ProjectFormProps) {
  const [name, setName] = useState('');
  const [customerId, setCustomerId] = useState('');
  const [type, setType] = useState<MigrationProject['type']>('IOM_BUCKET');
  const queryClient = useQueryClient();
  const toast = useToastContext();

  useEffect(() => {
    if (project) {
      setName(project.name);
      setCustomerId(project.customerId);
      setType(project.type);
    } else if (customers.length > 0) {
      setCustomerId(customers[0].id);
    }
  }, [project, customers]);

  const mutation = useMutation({
    mutationFn: (data: { name: string; customerId: string; type: MigrationProject['type'] }) =>
      project
        ? projectsApi.update(project.id, data)
        : projectsApi.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projects'] });
      toast.success(project ? 'Project updated successfully' : 'Project created successfully');
      onClose();
    },
    onError: (error: any) => {
      toast.error(`Failed to ${project ? 'update' : 'create'} project: ${error.message || 'Unknown error'}`);
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    mutation.mutate({ name, customerId, type });
  };

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl max-w-md w-full mx-4">
        <div className="flex items-center justify-between p-6 border-b border-gray-200">
          <h2 className="text-xl font-semibold text-gray-900">
            {project ? 'Edit Project' : 'New Project'}
          </h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          <div>
            <label className="label">Project Name</label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="input"
              placeholder="Enter project name"
              required
            />
          </div>

          <div>
            <label className="label">Customer</label>
            <select
              value={customerId}
              onChange={(e) => setCustomerId(e.target.value)}
              className="input"
              required
            >
              <option value="">Select customer</option>
              {customers.map((customer) => (
                <option key={customer.id} value={customer.id}>
                  {customer.name}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="label">Project Type</label>
            <select
              value={type}
              onChange={(e) => setType(e.target.value as MigrationProject['type'])}
              className="input"
              required
            >
              {PROJECT_TYPES.map((t) => (
                <option key={t} value={t}>
                  {t.replace('_', ' ')}
                </option>
              ))}
            </select>
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
              {mutation.isPending ? 'Saving...' : project ? 'Update' : 'Create'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
