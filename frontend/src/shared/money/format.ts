/**
 * Format a monetary string as currency.
 *
 * Money arrives from the backend as a decimal string to preserve numeric(19,4) precision
 * (.claude/rules/frontend_coding.md §13). MUST NOT parse the value to a JS `number` first.
 */
export function formatMoney(value: string, currencyCode: string, locale = 'en-US'): string {
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency: currencyCode,
    minimumFractionDigits: 2,
    maximumFractionDigits: 4,
  }).format(Number(value));
}
