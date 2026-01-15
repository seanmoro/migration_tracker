import DatabaseUpload from '../components/DatabaseUpload';
import Breadcrumb from '../components/Breadcrumb';

export default function DatabaseRestore() {
  return (
    <div className="space-y-6">
      <Breadcrumb
        items={[
          { label: 'Home', href: '/' },
          { label: 'Database Restore', href: '/database/restore' },
        ]}
      />
      <div className="bg-white rounded-lg shadow p-6">
        <h1 className="text-3xl font-bold text-gray-800 mb-2">Database Restore</h1>
        <p className="text-gray-600 mb-6">
          Restore your migration tracker database from a backup file.
        </p>
        <DatabaseUpload
          onSuccess={() => {
            console.log('Database restored successfully');
          }}
        />
      </div>
    </div>
  );
}
