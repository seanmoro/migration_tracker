import DatabaseUpload from '../components/DatabaseUpload';
import Breadcrumb from '../components/Breadcrumb';

export default function PostgreSQLRestore() {
  return (
    <div className="space-y-6">
      <Breadcrumb
        items={[
          { label: 'Home', path: '/' },
          { label: 'PostgreSQL Restore' },
        ]}
      />
      <div className="bg-white rounded-lg shadow p-6">
        <h1 className="text-3xl font-bold text-gray-800 mb-2">PostgreSQL Database Restore</h1>
        <p className="text-gray-600 mb-6">
          Restore BlackPearl or Rio PostgreSQL database from a backup file. This allows the tool to query buckets and gather migration data.
        </p>
        <DatabaseUpload
          databaseType="postgres"
          onSuccess={() => {
            console.log('PostgreSQL database restored successfully');
          }}
        />
      </div>
    </div>
  );
}
