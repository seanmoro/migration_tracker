import { BrowserRouter, Routes, Route } from 'react-router-dom';
import Layout from './components/Layout';
import Dashboard from './views/Dashboard';
import Customers from './views/Customers';
import Projects from './views/Projects';
import Phases from './views/Phases';
import PhaseProgress from './views/PhaseProgress';
import GatherData from './views/GatherData';
import DatabaseRestore from './views/DatabaseRestore';
import PostgreSQLRestore from './views/PostgreSQLRestore';
import BucketQuery from './views/BucketQuery';

function App() {
  return (
    <BrowserRouter>
      <Layout>
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/customers" element={<Customers />} />
          <Route path="/projects" element={<Projects />} />
          <Route path="/projects/:projectId/phases" element={<Phases />} />
          <Route path="/phases/:phaseId/progress" element={<PhaseProgress />} />
          <Route path="/gather-data" element={<GatherData />} />
          <Route path="/bucket-query" element={<BucketQuery />} />
          <Route path="/database/restore" element={<DatabaseRestore />} />
          <Route path="/database/postgres-restore" element={<PostgreSQLRestore />} />
        </Routes>
      </Layout>
    </BrowserRouter>
  );
}

export default App;
