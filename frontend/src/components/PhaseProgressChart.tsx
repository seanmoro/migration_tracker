import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import { MigrationData } from '../types';
import { formatDate, formatBytes } from '../utils/format';

interface PhaseProgressChartProps {
  data: MigrationData[];
}

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
    sourceSize: parseFloat((d.sourceSize / (1024 * 1024 * 1024)).toFixed(2)), // Convert to GB for chart axis
    sourceSizeBytes: d.sourceSize, // Keep original for tooltip formatting
    targetSize: parseFloat((d.targetSize / (1024 * 1024 * 1024)).toFixed(2)),
    targetSizeBytes: d.targetSize, // Keep original for tooltip formatting
  }));

  return (
    <ResponsiveContainer width="100%" height={400}>
      <LineChart data={chartData}>
        <CartesianGrid strokeDasharray="3 3" />
        <XAxis dataKey="date" />
        <YAxis yAxisId="left" tickFormatter={formatYAxisObjects} />
        <YAxis yAxisId="right" orientation="right" tickFormatter={formatYAxisSize} />
        <Tooltip 
          formatter={(value: number, name: string, props: any) => {
            // Recharts passes: value, name, and props object with payload property
            // The payload contains the full data point with all fields
            if (name.includes('Size')) {
              const payload = props?.payload;
              if (payload) {
                if (name === 'Source Size' && payload.sourceSizeBytes !== undefined) {
                  return [formatBytes(payload.sourceSizeBytes), name];
                } else if (name === 'Target Size' && payload.targetSizeBytes !== undefined) {
                  return [formatBytes(payload.targetSizeBytes), name];
                }
                // Fallback: convert GB value back to bytes (approximate)
                const bytes = value * 1024 * 1024 * 1024;
                return [formatBytes(bytes), name];
              }
            }
            // For objects, format as number
            return [value.toLocaleString('en-US', { maximumFractionDigits: 2 }), name];
          }}
        />
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
          name="Source Size"
        />
        <Line
          yAxisId="right"
          type="monotone"
          dataKey="targetSize"
          stroke="#8b5cf6"
          name="Target Size"
        />
      </LineChart>
    </ResponsiveContainer>
  );
}
