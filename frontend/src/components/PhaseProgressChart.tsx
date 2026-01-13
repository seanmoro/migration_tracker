import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import { MigrationData } from '../types';
import { formatDate } from '../utils/format';

interface PhaseProgressChartProps {
  data: MigrationData[];
}

export default function PhaseProgressChart({ data }: PhaseProgressChartProps) {
  const chartData = data.map((d) => ({
    date: formatDate(d.timestamp),
    sourceObjects: d.sourceObjects,
    targetObjects: d.targetObjects,
    sourceSize: d.sourceSize / (1024 * 1024 * 1024), // Convert to GB
    targetSize: d.targetSize / (1024 * 1024 * 1024),
  }));

  return (
    <ResponsiveContainer width="100%" height={400}>
      <LineChart data={chartData}>
        <CartesianGrid strokeDasharray="3 3" />
        <XAxis dataKey="date" />
        <YAxis yAxisId="left" />
        <YAxis yAxisId="right" orientation="right" />
        <Tooltip />
        <Legend />
        <Line
          yAxisId="left"
          type="monotone"
          dataKey="sourceObjects"
          stroke="#0284c7"
          name="Source Objects"
        />
        <Line
          yAxisId="left"
          type="monotone"
          dataKey="targetObjects"
          stroke="#10b981"
          name="Target Objects"
        />
        <Line
          yAxisId="right"
          type="monotone"
          dataKey="sourceSize"
          stroke="#f59e0b"
          name="Source Size (GB)"
        />
      </LineChart>
    </ResponsiveContainer>
  );
}
