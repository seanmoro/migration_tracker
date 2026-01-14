import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search, X } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { customersApi } from '../api/customers';
import { projectsApi } from '../api/projects';
import { phasesApi } from '../api/phases';

interface SearchResult {
  type: 'customer' | 'project' | 'phase';
  id: string;
  name: string;
  subtitle?: string;
  path: string;
}

export default function GlobalSearch() {
  const [isOpen, setIsOpen] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedIndex, setSelectedIndex] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const navigate = useNavigate();

  const { data: customers = [] } = useQuery({
    queryKey: ['customers', 'search', searchTerm],
    queryFn: () => customersApi.search(searchTerm || ''),
    enabled: isOpen && searchTerm.length > 0,
  });

  const { data: projects = [] } = useQuery({
    queryKey: ['projects', 'search', searchTerm],
    queryFn: () => projectsApi.search(searchTerm || ''),
    enabled: isOpen && searchTerm.length > 0,
  });

  const { data: phases = [] } = useQuery({
    queryKey: ['phases', 'search', searchTerm],
    queryFn: async () => {
      // Search phases across all projects
      const allProjects = await projectsApi.list();
      const allPhases = await Promise.all(
        allProjects.map(project => phasesApi.list(project.id))
      );
      const flatPhases = allPhases.flat();
      return flatPhases.filter(phase =>
        phase.name.toLowerCase().includes(searchTerm.toLowerCase())
      );
    },
    enabled: isOpen && searchTerm.length > 0,
  });

  const results: SearchResult[] = [
    ...customers.map(c => ({
      type: 'customer' as const,
      id: c.id,
      name: c.name,
      subtitle: 'Customer',
      path: `/customers`,
    })),
    ...projects.map(p => ({
      type: 'project' as const,
      id: p.id,
      name: p.name,
      subtitle: 'Project',
      path: `/projects/${p.id}/phases`,
    })),
    ...phases.map(p => ({
      type: 'phase' as const,
      id: p.id,
      name: p.name,
      subtitle: 'Phase',
      path: `/phases/${p.id}/progress`,
    })),
  ].slice(0, 10); // Limit to 10 results

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // Cmd/Ctrl + K to open search
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        setIsOpen(true);
      }
      // Escape to close
      if (e.key === 'Escape' && isOpen) {
        setIsOpen(false);
        setSearchTerm('');
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen]);

  useEffect(() => {
    if (isOpen && inputRef.current) {
      inputRef.current.focus();
    }
  }, [isOpen]);

  useEffect(() => {
    setSelectedIndex(0);
  }, [searchTerm]);

  const handleSelect = (result: SearchResult) => {
    navigate(result.path);
    setIsOpen(false);
    setSearchTerm('');
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setSelectedIndex(prev => Math.min(prev + 1, results.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setSelectedIndex(prev => Math.max(prev - 1, 0));
    } else if (e.key === 'Enter' && results[selectedIndex]) {
      e.preventDefault();
      handleSelect(results[selectedIndex]);
    }
  };

  if (!isOpen) {
    return (
      <button
        data-search-trigger
        onClick={() => setIsOpen(true)}
        className="flex items-center space-x-2 px-4 py-2 text-sm text-gray-600 bg-gray-100 rounded-lg hover:bg-gray-200 transition-colors"
      >
        <Search className="w-4 h-4" />
        <span>Search...</span>
        <kbd className="hidden md:inline-flex items-center px-2 py-1 text-xs font-semibold text-gray-500 bg-white border border-gray-300 rounded">
          ⌘K
        </kbd>
      </button>
    );
  }

  return (
    <>
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-black bg-opacity-50 z-40"
        onClick={() => {
          setIsOpen(false);
          setSearchTerm('');
        }}
      />
      
      {/* Search Modal */}
      <div className="fixed top-20 left-1/2 transform -translate-x-1/2 w-full max-w-2xl z-50">
        <div className="bg-white rounded-lg shadow-xl border border-gray-200">
          {/* Search Input */}
          <div className="flex items-center space-x-3 p-4 border-b border-gray-200">
            <Search className="w-5 h-5 text-gray-400" />
            <input
              ref={inputRef}
              type="text"
              placeholder="Search customers, projects, phases..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              onKeyDown={handleKeyDown}
              className="flex-1 outline-none text-gray-900 placeholder-gray-400"
            />
            <button
              onClick={() => {
                setIsOpen(false);
                setSearchTerm('');
              }}
              className="p-1 hover:bg-gray-100 rounded"
            >
              <X className="w-4 h-4 text-gray-400" />
            </button>
          </div>

          {/* Results */}
          {searchTerm.length > 0 && (
            <div className="max-h-96 overflow-y-auto">
              {results.length > 0 ? (
                <div className="py-2">
                  {results.map((result, index) => (
                    <button
                      key={`${result.type}-${result.id}`}
                      onClick={() => handleSelect(result)}
                      className={`w-full text-left px-4 py-3 hover:bg-gray-50 transition-colors ${
                        index === selectedIndex ? 'bg-primary-50' : ''
                      }`}
                    >
                      <div className="flex items-center justify-between">
                        <div>
                          <p className="font-medium text-gray-900">{result.name}</p>
                          <p className="text-sm text-gray-500 mt-0.5">{result.subtitle}</p>
                        </div>
                        <span className="text-xs text-gray-400 capitalize">{result.type}</span>
                      </div>
                    </button>
                  ))}
                </div>
              ) : (
                <div className="px-4 py-8 text-center text-gray-500">
                  <p>No results found for "{searchTerm}"</p>
                </div>
              )}
            </div>
          )}

          {/* Hint */}
          {searchTerm.length === 0 && (
            <div className="px-4 py-8 text-center text-gray-400">
              <p>Start typing to search...</p>
              <p className="text-xs mt-2">Use ↑↓ to navigate, Enter to select, Esc to close</p>
            </div>
          )}
        </div>
      </div>
    </>
  );
}
