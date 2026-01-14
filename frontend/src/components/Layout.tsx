import { ReactNode } from 'react';
import Sidebar from './Sidebar';
import Header from './Header';
import { useKeyboardShortcuts } from '../hooks/useKeyboardShortcuts';

interface LayoutProps {
  children: ReactNode;
}

export default function Layout({ children }: LayoutProps) {
  useKeyboardShortcuts();

  return (
    <div className="min-h-screen bg-gray-50">
      <Header />
      <div className="flex">
        <Sidebar />
        <main className="flex-1 p-6 ml-64 pt-20 pb-24">
          {children}
        </main>
      </div>
    </div>
  );
}
