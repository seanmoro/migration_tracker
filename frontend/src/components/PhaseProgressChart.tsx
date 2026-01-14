import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import { MigrationData } from '../types';
import { formatDate } from '../utils/format';

interface PhaseProgressChartProps {
  data: MigrationData[];
}

const formatTooltipValue = (value: number, name: string) => {
  if (name.includes('Size')) {
    // Value is already in GB
    return [`${value.toFixed(2)} GB`, name];
  }
  // For objects, format as number with 2 decimals if needed
  return [value.toLocaleString('en-US', { maximumFractionDigits: 2 }), name];
};

const formatYAxisObjects = (tickItem: number) => {
  if (tickItem >= 1000000) {
    return `${(tickItem / 1000000).toFixed(2)}M`;
  }
  if (tickItem >= 1000) {
    return `${(tickItem / 1000).toFixed(2)}K`;
  }
  return tickItem.toFixed(2);
};

const formatYAxisSize = (tickItem: number) => {
  return `${tickItem.toFixed(2)} GB`;
};

export default function PhaseProgressChart({ data }: PhaseProgressChartProps) {
  const chartData = data.map((d) => ({
    date: formatDate(d.timestamp),
    sourceObjects: d.sourceObjects,
    targetObjects: d.targetObjects,
    sourceSize: parseFloat((d.sourceSize / (1024 * 1024 * 1024)).toFixed(2)), // Convert to GB, 2 decimals
    targetSize: parseFloat((d.targetSize / (1024 * 1024 * 1024)).toFixed(2)),
  }));

  return (
    <ResponsiveContainer width="100%" height={400}>
      <LineChart data={chartData}>
        <CartesianGrid strokeDasharray="3 3" />
        <XAxis dataKey="date" />
        <YAxis yAxisId="left" tickFormatter={formatYAxisObjects} />
        <YAxis yAxisId="right" orientation="right" tickFormatter={formatYAxisSize} />
        <Tooltip formatter={formatTooltipValue} />
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
