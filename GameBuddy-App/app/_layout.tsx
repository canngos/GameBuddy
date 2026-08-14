import '../global.css';

import { QueryClientProvider } from '@tanstack/react-query';
import { useFonts } from 'expo-font';
import { Stack } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { View } from 'react-native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { KeyboardProvider } from 'react-native-keyboard-controller';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { identifyForCrashReports } from '../src/diagnostics/crashReporting';
import { installGlobalErrorHandler } from '../src/errors';
import { queryClient } from '../src/query';
import { AnimatedSplash } from '../src/ui/AnimatedSplash';
import { AppErrorBoundary } from '../src/ui/AppErrorBoundary';
import { connectSessionToApi, useSession } from '../src/session/store';
import { fontAssets, useIsDark, useScheme, useThemeColors } from '../src/theme';
import { useSoundEnabled } from '../src/ui/sound';

// Hold the native splash until fonts are parsed, the stored token has been read, and
// the theme preference is known. Without this the app flashes blank, then Roboto, then
// Poppins, then possibly light-before-dark — four frames of visible indecision on every
// cold start.
SplashScreen.preventAutoHideAsync().catch(() => {
  // Already hidden, which happens on a fast refresh. Not a problem.
});

// Before anything else, so an error thrown while the rest of this module is still
// evaluating is logged rather than swallowed.
installGlobalErrorHandler();

// The API client is wired to the session store once, at module scope, so it is done
// before any component can fire a request during its first render.
connectSessionToApi();

/**
 * Expo Router renders this instead of the tree when a descendant throws.
 *
 * Exported from the root layout so it covers everything. Without it a render error
 * unmounts the app and leaves a white screen carrying no information at all — which is
 * what a release build did, while the same error in development was a red box.
 */
export { AppErrorBoundary as ErrorBoundary };

export default function RootLayout() {
  const [fontsLoaded, fontError] = useFonts(fontAssets);
  // Whether the animated draw-on splash has finished. It plays once per cold start, on
  // top of the already-mounted shell, continuing where the static native splash stops.
  const [introDone, setIntroDone] = useState(false);
  const status = useSession((s) => s.status);
  const restore = useSession((s) => s.restore);
  const schemeHydrated = useScheme((s) => s.hydrated);
  const loadScheme = useScheme((s) => s.load);
  const loadSound = useSoundEnabled((s) => s.load);
  const userId = useSession((s) => s.userId);

  // Ties crash reports to an account. Native crash capture is already running by the time
  // any of this executes — it is installed by the Crashlytics NDK handler at process start,
  // which is the whole reason it can see a segfault that no JavaScript handler can. This
  // only adds the identity, so a report has something to correlate on: one intermittent
  // crash is noise, whereas "always the same accounts" or "always a first launch" is a lead.
  useEffect(() => {
    if (userId) identifyForCrashReports(userId);
  }, [userId]);

  useEffect(() => {
    void restore();
    void loadScheme();
    // Deliberately not part of `ready` below. A theme read late repaints the whole app, so
    // launch waits for it; a sound preference read late costs at most one unwanted blip in
    // the first moments of a session — and gating the splash on it would delay every launch
    // for a setting almost nobody changes. See `src/ui/sound.ts`.
    void loadSound();
  }, [restore, loadScheme, loadSound]);

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

  // The shell mounts underneath the overlay, so by the time the animation lifts the app
  // is already rendered and interactive — the splash never makes anyone wait for it.
  return (
    <View style={{ flex: 1 }}>
      <Shell />
      {!introDone && <AnimatedSplash onDone={() => setIntroDone(true)} />}
    </View>
  );
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
      {/* Feeds real keyboard geometry to the whole tree.

          React Native's own KeyboardAvoidingView relies on the system resizing the window
          — which Android stopped doing once Expo enabled edge-to-edge by default in SDK
          54. `adjustResize` is still in the manifest and is now simply ignored, so the
          component became a no-op and the keyboard covered anything at the bottom of the
          screen. The chat compose box was unusable: field and Send button both sat behind
          it, so you could neither see what you typed nor reach the button.

          This provider reads the keyboard inset from the platform directly rather than
          inferring it from window size, so it is unaffected by edge-to-edge. */}
      <KeyboardProvider>
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
      </KeyboardProvider>
    </GestureHandlerRootView>
  );
}
