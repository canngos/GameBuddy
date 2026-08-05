import { QueryClient } from '@tanstack/react-query';
import { ApiError } from './api/envelope';

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Games, keywords and avatars are reference data that changes when we deploy a
      // new catalogue, not while someone is picking from it. Five minutes stops
      // onboarding refetching the same 100 games on every step.
      staleTime: 5 * 60 * 1000,
      retry: (failureCount, error) => {
        // Retrying a rejection just delays showing the user what went wrong.
        if (error instanceof ApiError && !error.isTransient) return false;
        return failureCount < 2;
      },
    },
    mutations: {
      // Never automatic. A retried POST /auth/register is a second verification mail,
      // and a retried purchase is worse.
      retry: false,
    },
  },
});
