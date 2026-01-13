import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import { PhaseProgress } from '../types';

interface ProgressChartProps {
  phases: PhaseProgress[];
}

export default function ProgressChart({ phases }: ProgressChartProps) {
  // Transform data for chart
  const data = phases.map((phase) => ({
    name: phase.phaseName,
    progress: phase.progress,
    objects: phase.sourceObjects,
  }));

  return (
    <ResponsiveContainer width="100%" height={300}>
      <LineChart data={data}>
        <CartesianGrid strokeDasharray="3 3" />
        <XAxis dataKey="name" />
        <YAxis />
        <Tooltip />
        <Legend />
        <Line type="monotone" dataKey="progress" stroke="#0284c7" name="Progress %" />
        <Line type="monotone" dataKey="objects" stroke="#10b981" name="Objects" />
      </LineChart>
    </ResponsiveContainer>
  );
}
