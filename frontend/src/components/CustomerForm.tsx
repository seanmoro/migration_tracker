import { useState, useEffect } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { customersApi } from '../api/customers';
import { Customer } from '../types';
import { X } from 'lucide-react';
import { useToastContext } from '../contexts/ToastContext';

interface CustomerFormProps {
  customer?: Customer | null;
  onClose: () => void;
}

export default function CustomerForm({ customer, onClose }: CustomerFormProps) {
  const [name, setName] = useState('');
  const queryClient = useQueryClient();
  const toast = useToastContext();

  useEffect(() => {
    if (customer) {
      setName(customer.name);
    }
  }, [customer]);

  const mutation = useMutation({
    mutationFn: (data: { name: string }) =>
      customer
        ? customersApi.update(customer.id, data)
        : customersApi.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customers'] });
      toast.success(customer ? 'Customer updated successfully' : 'Customer created successfully');
      onClose();
    },
    onError: (error: any) => {
      toast.error(`Failed to ${customer ? 'update' : 'create'} customer: ${error.message || 'Unknown error'}`);
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    mutation.mutate({ name });
  };

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl max-w-md w-full mx-4">
        <div className="flex items-center justify-between p-6 border-b border-gray-200">
          <h2 className="text-xl font-semibold text-gray-900">
            {customer ? 'Edit Customer' : 'New Customer'}
          </h2>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          <div>
            <label className="label">Customer Name</label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="input"
              placeholder="Enter customer name"
              required
            />
          </div>

          <div className="flex items-center justify-end space-x-3 pt-4">
            <button
              type="button"
              onClick={onClose}
              className="btn btn-secondary"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={mutation.isPending}
              className="btn btn-primary"
            >
              {mutation.isPending ? 'Saving...' : customer ? 'Update' : 'Create'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
