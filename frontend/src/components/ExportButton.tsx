import { useState } from 'react';
import { Download } from 'lucide-react';
import { reportsApi } from '../api/reports';
import { ExportFormat } from '../types';
import ExportDialog from './ExportDialog';

interface ExportButtonProps {
  phaseId: string;
}

export default function ExportButton({ phaseId }: ExportButtonProps) {
  const [showDialog, setShowDialog] = useState(false);
  const [isExporting, setIsExporting] = useState(false);

  const handleExport = async (format: ExportFormat, options: any) => {
    setIsExporting(true);
    try {
      const blob = await reportsApi.exportPhase(phaseId, { format, ...options });
      
      // Create download link
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      
      const extensions = {
        pdf: 'pdf',
        excel: 'xlsx',
        csv: 'csv',
        json: 'json',
        html: 'html',
      };
      
      a.download = `phase-report-${phaseId}-${Date.now()}.${extensions[format]}`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
      
      setShowDialog(false);
    } catch (error) {
      console.error('Export failed:', error);
      alert('Export failed. Please try again.');
    } finally {
      setIsExporting(false);
    }
  };

  return (
    <>
      <button
        onClick={() => setShowDialog(true)}
        className="btn btn-primary flex items-center space-x-2"
        disabled={isExporting}
      >
        <Download className="w-5 h-5" />
        <span>{isExporting ? 'Exporting...' : 'Export Report'}</span>
      </button>

      {showDialog && (
        <ExportDialog
          onExport={handleExport}
          onClose={() => setShowDialog(false)}
        />
      )}
    </>
  );
}
