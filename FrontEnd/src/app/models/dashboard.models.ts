export interface StatCard {
  label: string;
  value: string | number;
  subLabel: string;
  subValue: string;
  trend: 'up' | 'down';
  icon: string;
  iconBg: string;
}

export interface Claim {
  reference: string;
  client: string;
  type: string;
  typeClass: string;
  date: string;
  status: string;
  statusClass: string;
}

export interface Agent {
  name: string;
  description: string;
  score: number;
  icon: string;
  iconBg: string;
  barColor: string;
}

export interface ConfidenceThreshold {
  value: number;
  message: string;
}
