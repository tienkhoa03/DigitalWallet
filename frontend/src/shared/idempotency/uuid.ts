/**
 * UUIDv7 generator for the Idempotency-Key header (.claude/rules/frontend_coding.md §13).
 * Each logical action mints one UUID at form-mount and reuses it across retries; a fresh
 * navigation mints a fresh key.
 *
 * Implementation: time-ordered RFC 9562 v7 with 48-bit Unix-ms prefix + 12-bit rand_a +
 * 62-bit rand_b. Falls back to crypto.randomUUID v4 when crypto.getRandomValues is unavailable.
 */
export function uuidv7(): string {
  if (typeof crypto === 'undefined' || !crypto.getRandomValues) {
    return crypto.randomUUID();
  }

  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);

  const ts = BigInt(Date.now());
  bytes[0] = Number((ts >> 40n) & 0xffn);
  bytes[1] = Number((ts >> 32n) & 0xffn);
  bytes[2] = Number((ts >> 24n) & 0xffn);
  bytes[3] = Number((ts >> 16n) & 0xffn);
  bytes[4] = Number((ts >> 8n) & 0xffn);
  bytes[5] = Number(ts & 0xffn);

  bytes[6] = ((bytes[6] ?? 0) & 0x0f) | 0x70; // version 7
  bytes[8] = ((bytes[8] ?? 0) & 0x3f) | 0x80; // RFC variant

  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
