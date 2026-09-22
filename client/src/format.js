const usd = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });
const usdRound = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 0 });

export const money = (n) => usd.format(n ?? 0);
export const moneyRound = (n) => usdRound.format(n ?? 0);

// "FOOD_AND_DRINK" -> "Food and drink"
export const categoryLabel = (c) => {
  if (!c) return 'Other';
  const s = c.toLowerCase().replace(/_/g, ' ');
  return s.charAt(0).toUpperCase() + s.slice(1);
};

export const monthLabel = (ym, style = 'long') => {
  const [y, m] = ym.split('-').map(Number);
  return new Date(y, m - 1, 1).toLocaleDateString('en-US', { month: style, year: style === 'long' ? 'numeric' : undefined });
};

export const shortDate = (iso) => {
  if (!iso) return '';
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d).toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
};

export const daysUntil = (iso) => {
  if (!iso) return null;
  const [y, m, d] = iso.split('-').map(Number);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return Math.round((new Date(y, m - 1, d) - today) / 86400000);
};

export const frequencyLabel = (f) =>
  ({ WEEKLY: 'Weekly', BIWEEKLY: 'Every 2 weeks', SEMI_MONTHLY: 'Twice a month', MONTHLY: 'Monthly', ANNUALLY: 'Yearly' }[f] ||
  'Irregular');
