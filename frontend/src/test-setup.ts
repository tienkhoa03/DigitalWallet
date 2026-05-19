import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

// React Testing Library cleanup between tests — testing.md §3.
afterEach(() => {
  cleanup();
});
