import { render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { App } from '@/App';
import { store } from '@/app/store';

/**
 * Smoke render. Uses MemoryRouter + Provider directly (the cross-feature renderWithProviders
 * helper called out in .claude/rules/testing.md §3 will land alongside the first feature page).
 */
describe('App', () => {
  it('renders the header', () => {
    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/']}>
          <Routes>
            <Route path="/" element={<App />} />
          </Routes>
        </MemoryRouter>
      </Provider>,
    );
    expect(screen.getByRole('heading', { name: /DigitalWallet/i })).toBeInTheDocument();
  });
});
