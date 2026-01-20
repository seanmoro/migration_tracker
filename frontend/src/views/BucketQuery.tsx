import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { migrationApi } from '../api/migration';
import { Loader2 } from 'lucide-react';
import { formatBytes, formatNumber } from '../utils/format';
import Breadcrumb from '../components/Breadcrumb';

export default function BucketQuery() {
  const [selectedBucket, setSelectedBucket] = useState('');

  const { data: buckets = [], isLoading: bucketsLoading } = useQuery({
    queryKey: ['buckets'],
    queryFn: () => migrationApi.getBuckets(),
  });

  // Find the selected bucket from the buckets list
  const bucketSize = buckets.find(b => b.name === selectedBucket);

  return (
    <div className="space-y-6">
      <Breadcrumb items={[
        { label: 'Dashboard', path: '/' },
        { label: 'Query Bucket Size' }
      ]} />
      
      <div>
        <h1 className="text-3xl font-bold text-gray-900">Query Bucket Size</h1>
        <p className="text-gray-600 mt-1">Query aggregate bucket size from the currently restored database</p>
      </div>

      <div className="card space-y-6">
        {/* Bucket Selection */}
        <div>
          <label htmlFor="bucket" className="block text-sm font-medium text-gray-700 mb-2">
            Bucket <span className="text-red-500">*</span>
          </label>
          <select
            id="bucket"
            value={selectedBucket}
            onChange={(e) => setSelectedBucket(e.target.value)}
            className="input w-full"
            disabled={bucketsLoading || buckets.length === 0}
          >
            <option value="">-- Select Bucket --</option>
            {buckets.map((bucket) => (
              <option key={bucket.name} value={bucket.name}>
                {bucket.name} ({bucket.source === 'blackpearl' ? 'BlackPearl' : 'Rio'})
              </option>
            ))}
          </select>
          {bucketsLoading && (
            <p className="mt-2 text-sm text-gray-500 flex items-center">
              <Loader2 className="w-4 h-4 mr-2 animate-spin" />
              Loading buckets from restored database...
            </p>
          )}
          {!bucketsLoading && buckets.length === 0 && (
            <p className="mt-2 text-sm text-yellow-600">
              No buckets found. Make sure the database has been restored.
            </p>
          )}
        </div>

        {/* Bucket Size Results */}
        {selectedBucket && bucketSize && (
          <div className="border-t border-gray-200 pt-6">
            <div className="space-y-4">
              <h3 className="text-lg font-semibold text-gray-900">Bucket Size Information</h3>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div className="bg-gray-50 rounded-lg p-4">
                  <div className="text-sm text-gray-600 mb-1">Bucket Name</div>
                  <div className="text-xl font-semibold text-gray-900">{bucketSize.name}</div>
                </div>
                <div className="bg-gray-50 rounded-lg p-4">
                  <div className="text-sm text-gray-600 mb-1">Object Count</div>
                  <div className="text-xl font-semibold text-gray-900">
                    {formatNumber(bucketSize.objectCount)}
                  </div>
                </div>
                <div className="bg-gray-50 rounded-lg p-4">
                  <div className="text-sm text-gray-600 mb-1">Total Size</div>
                  <div className="text-xl font-semibold text-gray-900">
                    {formatBytes(bucketSize.sizeBytes)}
                  </div>
                </div>
              </div>
              <div className="mt-4 p-4 bg-blue-50 rounded-lg">
                <div className="text-sm text-blue-800">
                  <strong>Size Breakdown:</strong>
                  <ul className="mt-2 space-y-1 list-disc list-inside">
                    <li>Bytes: {bucketSize.sizeBytes.toLocaleString()}</li>
                    <li>KB: {(bucketSize.sizeBytes / 1024).toFixed(2)}</li>
                    <li>MB: {(bucketSize.sizeBytes / 1024 / 1024).toFixed(2)}</li>
                    <li>GB: {(bucketSize.sizeBytes / 1024 / 1024 / 1024).toFixed(2)}</li>
                    <li>TB: {(bucketSize.sizeBytes / 1024 / 1024 / 1024 / 1024).toFixed(2)}</li>
                    <li>PB: {(bucketSize.sizeBytes / 1024 / 1024 / 1024 / 1024 / 1024).toFixed(2)}</li>
                  </ul>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
