import { useState } from 'react';
import { X, FileText, FileSpreadsheet, FileJson, FileCode, File } from 'lucide-react';
import { ExportFormat } from '../types';

interface ExportDialogProps {
  onExport: (format: ExportFormat, options: any) => void;
  onClose: () => void;
}

const formats: { value: ExportFormat; label: string; icon: any; description: string }[] = [
  { value: 'pdf', label: 'PDF', icon: FileText, description: 'Executive summary with charts' },
  { value: 'excel', label: 'Excel', icon: FileSpreadsheet, description: 'Detailed data with sheets' },
  { value: 'csv', label: 'CSV', icon: File, description: 'Raw data for analysis' },
  { value: 'json', label: 'JSON', icon: FileJson, description: 'Structured data format' },
  { value: 'html', label: 'HTML', icon: FileCode, description: 'Web-friendly format' },
];

export default function ExportDialog({ onExport, onClose }: ExportDialogProps) {
  const [format, setFormat] = useState<ExportFormat>('pdf');
  const [includeCharts, setIncludeCharts] = useState(true);
  const [includeForecast, setIncludeForecast] = useState(true);
  const [includeRawData, setIncludeRawData] = useState(false);
  const [template, setTemplate] = useState<'executive' | 'detailed' | 'minimal'>('executive');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onExport(format, {
      includeCharts,
      includeForecast,
      includeRawData,
      template,
    });
  };

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl max-w-lg w-full mx-4">
        <div className="flex items-center justify-between p-6 border-b border-gray-200">
          <h2 className="text-xl font-semibold text-gray-900">Export Report</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-6">
          {/* Format Selection */}
          <div>
            <label className="label mb-3">Format</label>
            <div className="grid grid-cols-2 gap-3">
              {formats.map((f) => {
                const Icon = f.icon;
                return (
                  <button
                    key={f.value}
                    type="button"
                    onClick={() => setFormat(f.value)}
                    className={`p-4 border-2 rounded-lg text-left transition-colors ${
                      format === f.value
                        ? 'border-primary-500 bg-primary-50'
                        : 'border-gray-200 hover:border-gray-300'
                    }`}
                  >
                    <div className="flex items-center space-x-3 mb-2">
                      <Icon className="w-5 h-5" />
                      <span className="font-medium">{f.label}</span>
                    </div>
                    <p className="text-sm text-gray-600">{f.description}</p>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Options */}
          <div className="space-y-3">
            <label className="label">Options</label>
            <div className="space-y-2">
              <label className="flex items-center space-x-2">
                <input
                  type="checkbox"
                  checked={includeCharts}
                  onChange={(e) => setIncludeCharts(e.target.checked)}
                  className="rounded border-gray-300 text-primary-600 focus:ring-primary-500"
                />
                <span className="text-sm text-gray-700">Include Charts</span>
              </label>
              <label className="flex items-center space-x-2">
                <input
                  type="checkbox"
                  checked={includeForecast}
                  onChange={(e) => setIncludeForecast(e.target.checked)}
                  className="rounded border-gray-300 text-primary-600 focus:ring-primary-500"
                />
                <span className="text-sm text-gray-700">Include Forecast</span>
              </label>
              <label className="flex items-center space-x-2">
                <input
                  type="checkbox"
                  checked={includeRawData}
                  onChange={(e) => setIncludeRawData(e.target.checked)}
                  className="rounded border-gray-300 text-primary-600 focus:ring-primary-500"
                />
                <span className="text-sm text-gray-700">Include Raw Data</span>
              </label>
            </div>
          </div>

          {/* Template (for PDF) */}
          {format === 'pdf' && (
            <div>
              <label className="label">Template</label>
              <select
                value={template}
                onChange={(e) => setTemplate(e.target.value as any)}
                className="input"
              >
                <option value="executive">Executive (Summary)</option>
                <option value="detailed">Detailed (Full Data)</option>
                <option value="minimal">Minimal (Data Only)</option>
              </select>
            </div>
          )}

          <div className="flex items-center justify-end space-x-3 pt-4">
            <button type="button" onClick={onClose} className="btn btn-secondary">
              Cancel
            </button>
            <button type="submit" className="btn btn-primary">
              Export
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
