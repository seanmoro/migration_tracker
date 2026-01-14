import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import { PhaseProgress } from '../types';

interface ProgressChartProps {
  phases: PhaseProgress[];
}

const formatTooltipValue = (value: number, name: string) => {
  if (name === 'Progress %') {
    return [`${value.toFixed(2)}%`, name];
  }
  return [value.toLocaleString('en-US', { maximumFractionDigits: 2 }), name];
};

const formatYAxis = (tickItem: number) => {
  return tickItem.toLocaleString('en-US', { maximumFractionDigits: 2 });
};

export default function ProgressChart({ phases }: ProgressChartProps) {
  // Transform data for chart
  const data = phases.map((phase) => ({
    name: phase.phaseName,
    progress: parseFloat(phase.progress.toFixed(2)),
    objects: phase.sourceObjects,
  }));

  return (
    <ResponsiveContainer width="100%" height={300}>
      <LineChart data={data}>
        <CartesianGrid strokeDasharray="3 3" />
        <XAxis dataKey="name" />
        <YAxis tickFormatter={formatYAxis} />
        <Tooltip formatter={formatTooltipValue} />
        <Legend />
        <Line type="monotone" dataKey="progress" stroke="#0284c7" name="Progress %" />
        <Line type="monotone" dataKey="objects" stroke="#10b981" name="Objects" />
      </LineChart>
    </ResponsiveContainer>
  );
}
