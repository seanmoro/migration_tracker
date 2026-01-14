import { X } from 'lucide-react';

interface BulkAction {
  label: string;
  onClick: () => void;
  variant?: 'primary' | 'secondary' | 'danger';
}

interface BulkActionBarProps {
  count: number;
  actions: BulkAction[];
  onClear: () => void;
}

export default function BulkActionBar({ count, actions, onClear }: BulkActionBarProps) {
  if (count === 0) return null;

  return (
    <div className="fixed bottom-0 left-64 right-0 bg-white border-t border-gray-200 shadow-lg z-40 p-4">
      <div className="flex items-center justify-between max-w-7xl mx-auto">
        <div className="flex items-center space-x-4">
          <span className="text-sm font-medium text-gray-700">
            {count} {count === 1 ? 'item' : 'items'} selected
          </span>
          <button
            onClick={onClear}
            className="text-sm text-gray-600 hover:text-gray-900 flex items-center space-x-1"
          >
            <X className="w-4 h-4" />
            <span>Clear selection</span>
          </button>
        </div>
        <div className="flex items-center space-x-2">
          {actions.map((action, index) => (
            <button
              key={index}
              onClick={action.onClick}
              className={`btn ${
                action.variant === 'danger'
                  ? 'btn-danger'
                  : action.variant === 'secondary'
                  ? 'btn-secondary'
                  : 'btn-primary'
              }`}
            >
              {action.label}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
