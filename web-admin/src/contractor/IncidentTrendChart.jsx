import React, { useMemo, useState } from 'react';
import {
  Chart as ChartJS, CategoryScale, LinearScale, PointElement, LineElement, Tooltip, Filler,
} from 'chart.js';
import { Line } from 'react-chartjs-2';
import { SEVERITY_COLOR, STATUS_COLOR, monthKey, monthLabel } from './contractorData';

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Tooltip, Filler);

const SERIES = [
  { key: 'ALL', label: 'All Incidents', color: '#ea580c' },
  { key: 'WARNING', label: 'Warning', color: SEVERITY_COLOR.WARNING },
  { key: 'DANGER', label: 'Danger', color: SEVERITY_COLOR.DANGER },
  { key: 'EMERGENCY', label: 'Emergency', color: STATUS_COLOR.EMERGENCY },
];

const MONTHS_BACK = 6;

/** Last N month keys, oldest first. */
const recentMonthKeys = (n) => {
  const d = new Date();
  return Array.from({ length: n }, (_, i) => {
    const offset = n - 1 - i;
    return monthKey(new Date(d.getFullYear(), d.getMonth() - offset, 1).getTime());
  });
};

/**
 * Incident trend as a line graph.
 *  - Line (dropdown): All incidents, Warning, Danger or Emergency.
 *  - X axis (dropdown): month by month, or the days within one chosen month.
 *  - Y axis: number of incidents.
 */
const IncidentTrendChart = ({ incidents }) => {
  const [seriesKey, setSeriesKey] = useState('ALL');
  const [range, setRange] = useState('MONTHS'); // MONTHS | SINGLE
  const monthKeys = useMemo(() => recentMonthKeys(MONTHS_BACK), []);
  const [selectedMonth, setSelectedMonth] = useState(monthKeys[monthKeys.length - 1]);

  const series = SERIES.find((s) => s.key === seriesKey);

  const { labels, values, xTitle } = useMemo(() => {
    const matching = incidents.filter((i) => seriesKey === 'ALL' || i.severity === seriesKey);

    if (range === 'MONTHS') {
      const counts = Object.fromEntries(monthKeys.map((k) => [k, 0]));
      matching.forEach((i) => {
        const k = monthKey(i.createdAt);
        if (k in counts) counts[k] += 1;
      });
      return {
        labels: monthKeys.map((k) => monthLabel(k).slice(0, 3)),
        values: monthKeys.map((k) => counts[k]),
        xTitle: 'Month',
      };
    }

    const [y, m] = selectedMonth.split('-').map(Number);
    const days = new Date(y, m, 0).getDate();
    const counts = Array(days).fill(0);
    matching.forEach((i) => {
      if (monthKey(i.createdAt) === selectedMonth) counts[new Date(i.createdAt).getDate() - 1] += 1;
    });
    return {
      labels: counts.map((_, idx) => String(idx + 1)),
      values: counts,
      xTitle: `Day of ${monthLabel(selectedMonth)}`,
    };
  }, [incidents, seriesKey, range, selectedMonth, monthKeys]);

  const chartData = {
    labels,
    datasets: [{
      label: series.label,
      data: values,
      borderColor: series.color,
      backgroundColor: `${series.color}22`,
      pointBackgroundColor: series.color,
      pointRadius: 4,
      pointHoverRadius: 6,
      borderWidth: 2.5,
      tension: 0.3,
      fill: true,
    }],
  };

  // Y axis: leave 3 extra gridlines of headroom above the highest value.
  const peak = Math.max(0, ...values);
  const yStep = peak <= 10 ? 1 : Math.ceil(peak / 10);
  const yMax = (Math.ceil(peak / yStep) + 3) * yStep;

  const options = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
      tooltip: {
        callbacks: {
          // In Day view the title would just be the day number, so hide it.
          title: (items) => (range === 'SINGLE' ? '' : items[0]?.label),
          label: (ctx) => ` ${ctx.parsed.y} ${ctx.parsed.y === 1 ? 'incident' : 'incidents'}`,
        },
      },
    },
    scales: {
      x: { title: { display: true, text: xTitle }, grid: { display: false } },
      y: {
        beginAtZero: true,
        max: yMax,
        ticks: { precision: 0, stepSize: yStep },
        title: { display: true, text: 'Number of incidents' },
      },
    },
  };

  return (
    <>
      <div className="ct-chart-controls">
        <select
          id="ct-trend-series"
          className="ct-chart-select"
          aria-label="Incident line"
          value={seriesKey}
          onChange={(e) => setSeriesKey(e.target.value)}
        >
          {SERIES.map((s) => <option key={s.key} value={s.key}>{s.label}</option>)}
        </select>
        <select
          id="ct-trend-range"
          className="ct-chart-select"
          aria-label="X axis"
          value={range}
          onChange={(e) => setRange(e.target.value)}
        >
          <option value="MONTHS">Month</option>
          <option value="SINGLE">Day</option>
        </select>
        {range === 'SINGLE' && (
          <select
            id="ct-trend-month"
            className="ct-chart-select"
            aria-label="Month"
            value={selectedMonth}
            onChange={(e) => setSelectedMonth(e.target.value)}
          >
            {[...monthKeys].reverse().map((k) => <option key={k} value={k}>{monthLabel(k)}</option>)}
          </select>
        )}
      </div>
      <div className="ct-line-chart">
        <Line data={chartData} options={options} />
      </div>
    </>
  );
};

export default IncidentTrendChart;
