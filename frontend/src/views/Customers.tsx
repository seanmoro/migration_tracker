import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { customersApi } from '../api/customers';
import { Customer } from '../types';
import { Plus, Search, Edit, Trash2 } from 'lucide-react';
import { formatDate } from '../utils/format';
import CustomerForm from '../components/CustomerForm';
import Breadcrumb from '../components/Breadcrumb';
import { useToastContext } from '../contexts/ToastContext';

export default function Customers() {
  const [searchTerm, setSearchTerm] = useState('');
  const [showForm, setShowForm] = useState(false);
  const [editingCustomer, setEditingCustomer] = useState<Customer | null>(null);
  const queryClient = useQueryClient();
  const toast = useToastContext();

  const { data: customers = [], isLoading } = useQuery({
    queryKey: ['customers', searchTerm],
    queryFn: () =>
      searchTerm
        ? customersApi.search(searchTerm)
        : customersApi.list(),
  });

  // Debug logging
  console.log('Customers component - customers:', customers, 'isLoading:', isLoading);

  const deleteMutation = useMutation({
    mutationFn: (id: string) => customersApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customers'] });
      toast.success('Customer deleted successfully');
    },
    onError: (error: any) => {
      toast.error(`Failed to delete customer: ${error.message || 'Unknown error'}`);
    },
  });

  const handleEdit = (customer: Customer) => {
    setEditingCustomer(customer);
    setShowForm(true);
  };

  const handleCreate = () => {
    setEditingCustomer(null);
    setShowForm(true);
  };

  const handleFormClose = () => {
    setShowForm(false);
    setEditingCustomer(null);
  };

  if (showForm) {
    return (
      <CustomerForm
        customer={editingCustomer}
        onClose={handleFormClose}
      />
    );
  }

  return (
    <div className="space-y-6">
      <Breadcrumb items={[
        { label: 'Dashboard', path: '/' },
        { label: 'Customers' }
      ]} />
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Customers</h1>
          <p className="text-gray-600 mt-1">Manage customer accounts</p>
        </div>
        <button
          onClick={handleCreate}
          className="btn btn-primary flex items-center space-x-2"
        >
          <Plus className="w-5 h-5" />
          <span>New Customer</span>
        </button>
      </div>

      {/* Search */}
      <div className="relative">
        <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 w-5 h-5" />
        <input
          type="text"
          placeholder="Search customers..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          className="input pl-10"
        />
      </div>

      {/* Customers List */}
      {isLoading ? (
        <div className="text-center py-12 text-gray-500">Loading customers...</div>
      ) : customers.length === 0 ? (
        <div className="card text-center py-12">
          <p className="text-gray-500">No customers found</p>
        </div>
      ) : (
        <div className="card">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-gray-200">
                  <th className="text-left py-3 px-4 font-semibold text-gray-700">Name</th>
                  <th className="text-left py-3 px-4 font-semibold text-gray-700">Created</th>
                  <th className="text-left py-3 px-4 font-semibold text-gray-700">Status</th>
                  <th className="text-right py-3 px-4 font-semibold text-gray-700">Actions</th>
                </tr>
              </thead>
              <tbody>
                {customers.map((customer) => (
                  <tr key={customer.id} className="border-b border-gray-100 hover:bg-gray-50">
                    <td className="py-3 px-4">
                      <span className="font-medium text-gray-900">{customer.name}</span>
                    </td>
                    <td className="py-3 px-4 text-gray-600">
                      {formatDate(customer.createdAt)}
                    </td>
                    <td className="py-3 px-4">
                      <span
                        className={`px-2 py-1 rounded-full text-xs font-medium ${
                          customer.active
                            ? 'bg-green-100 text-green-800'
                            : 'bg-gray-100 text-gray-800'
                        }`}
                      >
                        {customer.active ? 'Active' : 'Inactive'}
                      </span>
                    </td>
                    <td className="py-3 px-4">
                      <div className="flex items-center justify-end space-x-2">
                        <button
                          onClick={() => handleEdit(customer)}
                          className="p-2 text-gray-600 hover:text-primary-600 hover:bg-primary-50 rounded"
                        >
                          <Edit className="w-4 h-4" />
                        </button>
                        <button
                          onClick={() => {
                            if (confirm('Are you sure you want to delete this customer?')) {
                              deleteMutation.mutate(customer.id);
                            }
                          }}
                          className="p-2 text-gray-600 hover:text-red-600 hover:bg-red-50 rounded"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
