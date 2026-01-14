import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

export function useKeyboardShortcuts() {
  const navigate = useNavigate();

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // Don't trigger shortcuts when typing in inputs
      if (
        (e.target as HTMLElement).tagName === 'INPUT' ||
        (e.target as HTMLElement).tagName === 'TEXTAREA' ||
        (e.target as HTMLElement).isContentEditable
      ) {
        return;
      }

      // Cmd/Ctrl + key combinations
      if (e.metaKey || e.ctrlKey) {
        switch (e.key.toLowerCase()) {
          case 'g':
            e.preventDefault();
            navigate('/gather-data');
            break;
          case 'd':
            e.preventDefault();
            navigate('/');
            break;
          case 'n':
            e.preventDefault();
            navigate('/customers');
            break;
          case '/':
            e.preventDefault();
            // Focus search - this will be handled by GlobalSearch component
            const searchButton = document.querySelector('[data-search-trigger]') as HTMLElement;
            searchButton?.click();
            break;
          case '?':
            e.preventDefault();
            // Show keyboard shortcuts help
            alert(`Keyboard Shortcuts:
⌘/Ctrl + G - Go to Gather Data
⌘/Ctrl + D - Go to Dashboard
⌘/Ctrl + N - New Migration
⌘/Ctrl + / - Focus Search
⌘/Ctrl + K - Open Search
Esc - Close modals/dialogs`);
            break;
        }
      }

      // Escape key
      if (e.key === 'Escape') {
        // Close any open modals/dialogs
        const modals = document.querySelectorAll('[data-modal]');
        modals.forEach((modal) => {
          const closeButton = modal.querySelector('[data-close-modal]') as HTMLElement;
          closeButton?.click();
        });
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [navigate]);
}
