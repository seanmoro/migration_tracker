import { BarChart3 } from 'lucide-react';

export default function Header() {
  return (
    <header className="bg-white border-b border-gray-200 fixed top-0 left-0 right-0 z-50 h-16 shadow-sm">
      <div className="flex items-center justify-between px-6 h-full max-w-full">
        <div className="flex items-center space-x-3 min-w-0">
          <BarChart3 className="w-8 h-8 text-primary-600 flex-shrink-0" />
          <h1 className="text-xl font-bold text-gray-900 truncate">Migration Tracker</h1>
        </div>
        <div className="flex items-center space-x-4 flex-shrink-0">
          <span className="text-sm text-gray-600 whitespace-nowrap">v0.6.2</span>
        </div>
      </div>
    </header>
  );
}
