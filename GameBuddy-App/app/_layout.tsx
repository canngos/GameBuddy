import '../global.css';

import { QueryClientProvider } from '@tanstack/react-query';
import { useFonts } from 'expo-font';
import { Stack } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { StatusBar } from 'expo-status-bar';
import { useEffect } from 'react';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { queryClient } from '../src/query';
import { connectSessionToApi, useSession } from '../src/session/store';
import { fontAssets, useIsDark, useScheme, useThemeColors } from '../src/theme';

// Hold the native splash until fonts are parsed, the stored token has been read, and
// the theme preference is known. Without this the app flashes blank, then Roboto, then
// Poppins, then possibly light-before-dark — four frames of visible indecision on every
// cold start.
SplashScreen.preventAutoHideAsync().catch(() => {
  // Already hidden, which happens on a fast refresh. Not a problem.
});

// The API client is wired to the session store once, at module scope, so it is done
// before any component can fire a request during its first render.
connectSessionToApi();

export default function RootLayout() {
  const [fontsLoaded, fontError] = useFonts(fontAssets);
  const status = useSession((s) => s.status);
  const restore = useSession((s) => s.restore);
  const schemeHydrated = useScheme((s) => s.hydrated);
  const loadScheme = useScheme((s) => s.load);

  useEffect(() => {
    void restore();
    void loadScheme();
  }, [restore, loadScheme]);

  const ready = (fontsLoaded || !!fontError) && status !== 'loading' && schemeHydrated;

  useEffect(() => {
    if (ready) void SplashScreen.hideAsync();
  }, [ready]);

  // Fonts failing to load is not worth blocking launch over — the system font is ugly,
  // not broken — but it should be visible in the logs rather than silent.
  useEffect(() => {
    if (fontError) console.warn('[fonts] Poppins failed to load', fontError);
  }, [fontError]);

  if (!ready) return null;

  return <Shell />;
}

/**
 * Split out so the theme hooks run *below* the readiness gate. Called from
 * RootLayout they would read the colour scheme before the stored preference had been
 * applied, and the first paint would use the wrong palette.
 */
function Shell() {
  const colors = useThemeColors();
  const isDark = useIsDark();

  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <SafeAreaProvider>
        <QueryClientProvider client={queryClient}>
          <StatusBar style={isDark ? 'light' : 'dark'} />
          <Stack
            screenOptions={{
              headerShown: false,
              // A colour value, not a class: this styles the navigator's own container,
              // which sits outside the React tree NativeWind processes. Without it the
              // white default flashes between screens in dark mode.
              contentStyle: { backgroundColor: colors.canvas },
            }}
          />
        </QueryClientProvider>
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
}
