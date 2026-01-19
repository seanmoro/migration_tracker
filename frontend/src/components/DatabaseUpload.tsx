import { useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { databaseApi, RestoreResponse } from '../api/database';
import { customersApi } from '../api/customers';
import { useToastContext } from '../contexts/ToastContext';

interface DatabaseUploadProps {
  onSuccess?: () => void;
  databaseType?: 'blackpearl' | 'rio' | 'tracker' | 'postgres';
}

export default function DatabaseUpload({ onSuccess, databaseType = 'tracker' }: DatabaseUploadProps) {
  const navigate = useNavigate();
  const [uploading, setUploading] = useState(false);
  const [dragActive, setDragActive] = useState(false);
  const [result, setResult] = useState<RestoreResponse | null>(null);
  const [selectedDbType, setSelectedDbType] = useState<'blackpearl' | 'rio'>(databaseType === 'blackpearl' ? 'blackpearl' : databaseType === 'rio' ? 'rio' : 'blackpearl');
  const [selectedCustomerId, setSelectedCustomerId] = useState<string>('');
  const fileInputRef = useRef<HTMLInputElement>(null);
  const { showToast } = useToastContext();

  // Fetch customers for PostgreSQL restore
  const { data: customers = [] } = useQuery({
    queryKey: ['customers'],
    queryFn: () => customersApi.list(),
    enabled: databaseType === 'postgres' || databaseType === 'blackpearl' || databaseType === 'rio',
  });

  const handleFile = async (file: File) => {
    // Validate file type based on database type
    let validExtensions: string[];
    if (databaseType === 'tracker') {
      validExtensions = ['.db', '.zip', '.tar.gz', '.tgz', '.gz', '.tar', '.zst'];
    } else {
      validExtensions = ['.dump', '.sql', '.tar', '.tar.gz', '.tgz', '.zip', '.gz', '.zst'];
    }
    
    const fileName = file.name.toLowerCase();
    const isValid = validExtensions.some(ext => fileName.endsWith(ext));

    if (!isValid) {
      const supported = databaseType === 'tracker' 
        ? '.db, .zip, .tar.gz, .gz, .tar, .zst'
        : '.dump, .sql, .tar, .tar.gz, .zip, .zst';
      showToast(`Invalid file type. Supported: ${supported}`, 'error');
      return;
    }

    // Validate file size (max 2GB for PostgreSQL, 500MB for tracker)
    const maxSize = databaseType === 'tracker' ? 500 * 1024 * 1024 : 2 * 1024 * 1024 * 1024;
    if (file.size > maxSize) {
      const maxSizeMB = databaseType === 'tracker' ? '500MB' : '2GB';
      showToast(`File too large. Maximum size is ${maxSizeMB}`, 'error');
      return;
    }

    setUploading(true);
    setResult(null);

    try {
      let response: RestoreResponse;
      if (databaseType === 'tracker') {
        response = await databaseApi.restoreDatabase(file);
      } else {
        // For PostgreSQL restore, require customer selection
        if (!selectedCustomerId) {
          showToast('Please select a customer', 'error');
          setUploading(false);
          return;
        }
        response = await databaseApi.restorePostgreSQLDatabase(file, selectedDbType, selectedCustomerId);
      }
      
      setResult(response);

      if (response.success) {
        showToast(response.message || 'Database restored successfully!', 'success');
        if (onSuccess) {
          onSuccess();
        }
        // For PostgreSQL restore, navigate to Gather Data page with customer pre-selected
        if ((databaseType === 'postgres' || databaseType === 'blackpearl' || databaseType === 'rio') && selectedCustomerId) {
          // Find the first project for this customer to pre-select
          // Navigate to Gather Data with customer ID in state
          setTimeout(() => {
            navigate('/gather-data', {
              state: { customerId: selectedCustomerId }
            });
          }, 1500);
        }
      } else {
        showToast(response.error || 'Failed to restore database', 'error');
      }
    } catch (error: any) {
      console.error('Upload error:', error);
      let errorMessage = 'Failed to restore database';
      
      if (error.response) {
        // Server responded with error status
        errorMessage = error.response.data?.error || 
                      error.response.data?.message || 
                      `Server error: ${error.response.status} ${error.response.statusText}`;
      } else if (error.request) {
        // Request was made but no response received
        errorMessage = 'Network error: No response from server. Check your connection.';
      } else {
        // Error in request setup
        errorMessage = error.message || 'Failed to restore database';
      }
      
      showToast(errorMessage, 'error');
      setResult({
        success: false,
        error: errorMessage,
      });
    } finally {
      setUploading(false);
    }
  };

  const handleDrag = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.type === 'dragenter' || e.type === 'dragover') {
      setDragActive(true);
    } else if (e.type === 'dragleave') {
      setDragActive(false);
    }
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setDragActive(false);

    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      handleFile(e.dataTransfer.files[0]);
    }
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    e.preventDefault();
    if (e.target.files && e.target.files[0]) {
      handleFile(e.target.files[0]);
    }
  };

  const handleClick = () => {
    fileInputRef.current?.click();
  };

  return (
    <div className="w-full max-w-2xl mx-auto">
      <div className="bg-white rounded-lg shadow-md p-6">
        <h2 className="text-2xl font-bold text-gray-800 mb-4">
          {databaseType === 'tracker' ? 'Restore Tracker Database' : 'Restore PostgreSQL Database'}
        </h2>
        <p className="text-gray-600 mb-6">
          {databaseType === 'tracker' 
            ? 'Upload a zipped database backup (.zip, .tar.gz) or a database file (.db) to restore your migration tracker data.'
            : 'Upload a PostgreSQL database backup (.dump, .sql, .tar, .tar.gz) to restore BlackPearl or Rio database. Connection settings will be automatically configured - no separate credentials needed!'}
        </p>

        {(databaseType === 'blackpearl' || databaseType === 'rio' || databaseType === 'postgres') && (
          <>
            <div className="mb-6">
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Customer <span className="text-red-500">*</span>
              </label>
              <select
                value={selectedCustomerId}
                onChange={(e) => setSelectedCustomerId(e.target.value)}
                className="block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500 sm:text-sm"
                disabled={uploading}
                required
              >
                <option value="">Select a customer</option>
                {customers.map((customer) => (
                  <option key={customer.id} value={customer.id}>
                    {customer.name}
                  </option>
                ))}
              </select>
              <p className="text-xs text-gray-500 mt-1">
                The database will be restored to a customer-specific database name
              </p>
            </div>
            <div className="mb-6">
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Database Type
              </label>
              <select
                value={selectedDbType}
                onChange={(e) => setSelectedDbType(e.target.value as 'blackpearl' | 'rio')}
                className="block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500 sm:text-sm"
                disabled={uploading}
              >
                <option value="blackpearl">BlackPearl</option>
                <option value="rio">Rio</option>
              </select>
            </div>
          </>
        )}

        {/* Upload Area */}
        <div
          className={`border-2 border-dashed rounded-lg p-8 text-center transition-colors ${
            dragActive
              ? 'border-blue-500 bg-blue-50'
              : 'border-gray-300 hover:border-gray-400'
          } ${uploading ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}`}
          onDragEnter={handleDrag}
          onDragLeave={handleDrag}
          onDragOver={handleDrag}
          onDrop={handleDrop}
          onClick={handleClick}
        >
          <input
            ref={fileInputRef}
            type="file"
            className="hidden"
            accept={(databaseType === 'blackpearl' || databaseType === 'rio' || databaseType === 'postgres')
              ? ".dump,.sql,.tar,.tar.gz,.tgz,.zip,.gz,.zst"
              : ".db,.zip,.tar.gz,.tgz,.gz,.tar,.zst"}
            onChange={handleChange}
            disabled={uploading}
          />

          {uploading ? (
            <div className="flex flex-col items-center">
              <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mb-4"></div>
              <p className="text-gray-600">Uploading and restoring database...</p>
            </div>
          ) : (
            <div className="flex flex-col items-center">
              <svg
                className="w-16 h-16 text-gray-400 mb-4"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"
                />
              </svg>
              <p className="text-gray-600 mb-2">
                <span className="text-blue-600 font-semibold">Click to upload</span> or drag and drop
              </p>
              <p className="text-sm text-gray-500">
                {(databaseType === 'blackpearl' || databaseType === 'rio' || databaseType === 'postgres')
                  ? 'Supported formats: .dump, .sql, .tar, .tar.gz, .zip, .zst (Max 2GB)'
                  : 'Supported formats: .db, .zip, .tar.gz, .gz, .tar, .zst (Max 500MB)'}
              </p>
            </div>
          )}
        </div>

        {/* Result Message */}
        {result && (
          <div
            className={`mt-4 p-4 rounded-lg ${
              result.success
                ? 'bg-green-50 border border-green-200 text-green-800'
                : 'bg-red-50 border border-red-200 text-red-800'
            }`}
          >
            {result.success ? (
              <div>
                <p className="font-semibold">✓ {result.message}</p>
                {result.filename && (
                  <p className="text-sm mt-1">File: {result.filename}</p>
                )}
                <p className="text-sm mt-2">Page will reload in a few seconds...</p>
              </div>
            ) : (
              <div>
                <p className="font-semibold">✗ Error</p>
                <p className="text-sm mt-1">{result.error}</p>
              </div>
            )}
          </div>
        )}

        {/* Instructions */}
        <div className="mt-6 p-4 bg-gray-50 rounded-lg">
          <h3 className="font-semibold text-gray-800 mb-2">Instructions:</h3>
          <ul className="text-sm text-gray-600 space-y-1 list-disc list-inside">
            {databaseType === 'tracker' ? (
              <>
                <li>Upload a zipped backup file (.zip) containing migrations.db</li>
                <li>Or upload a direct database file (.db)</li>
                <li>The existing database will be backed up automatically before restore</li>
                <li>After successful restore, the page will reload to show the new data</li>
              </>
            ) : (
              <>
                <li>Upload a PostgreSQL backup file (.dump or .sql format recommended)</li>
                <li>Select whether this is a BlackPearl or Rio database</li>
                <li>The database will be restored to localhost PostgreSQL (or configured server)</li>
                <li>Connection settings will be automatically configured - no need to provide credentials separately!</li>
                <li>Ensure PostgreSQL server is running on localhost (or configured host) and client tools are installed</li>
                <li>After successful restore, you can immediately use the bucket selection feature</li>
              </>
            )}
          </ul>
        </div>
      </div>
    </div>
  );
}
