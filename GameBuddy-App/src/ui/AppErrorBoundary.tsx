import type { ErrorBoundaryProps } from 'expo-router';
import { useEffect } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { recordHandledError } from '../diagnostics/crashReporting';
import { tNow } from '../i18n/useT';

/**
 * The screen's copy, in the app's language when the i18n module is healthy and in
 * English when it is not. `tNow()` reaches into plain data through zustand's
 * `getState()` — no provider, no hook — but this screen renders precisely when
 * something already threw, so even that is allowed to fail.
 */
function boundaryCopy() {
  try {
    const t = tNow();
    return { ...t.errorScreen, retry: t.common.retry };
  } catch {
    return {
      title: 'GameBuddy hit a problem',
      blurb: 'This is a bug, not something you did.',
      copyHint: 'The text above can be selected and copied.',
      retry: 'Try again',
    };
  }
}

/**
 * What the app shows when a render throws.
 *
 * Without one of these, a single bad render unmounts the whole tree and leaves a white
 * screen with no message — which is exactly what a release build did, and there was no
 * way to tell from the device whether the app had crashed, hung, or lost the network.
 * In development the same error is a red box with a stack trace; in production it was
 * nothing at all.
 *
 * Deliberately built from bare `react-native` primitives with inline styles: no
 * NativeWind, no theme hook, no custom `Text`. Everything this file could import is
 * something that might be the thing that just failed, and an error screen that can throw
 * is worse than none.
 *
 * The message is shown in full rather than replaced with "Something went wrong". This is
 * a real user-facing screen, so it opens with a plain sentence — but the detail underneath
 * it is what makes a bug report worth reading, and hiding it protects nobody.
 */
export function AppErrorBoundary({ error, retry }: ErrorBoundaryProps) {
  const copy = boundaryCopy();
  // Report it. Expo Router's ErrorBoundary catches a render throw before the global handler
  // sees it, so without this a render crash is both on screen and invisible in Crashlytics.
  // Keyed on the error object so retrying with the same instance does not report it twice.
  // `recordHandledError` only touches the null-guarded diagnostics module, which cannot throw.
  useEffect(() => {
    recordHandledError(error, 'ErrorBoundary');
  }, [error]);
  return (
    <View style={{ flex: 1, backgroundColor: '#12121A', padding: 24, justifyContent: 'center' }}>
      <Text style={{ color: '#FFFFFF', fontSize: 22, fontWeight: '700', marginBottom: 8 }}>
        {copy.title}
      </Text>

      <Text style={{ color: '#A0A0B0', fontSize: 15, lineHeight: 21, marginBottom: 20 }}>
        {copy.blurb}
      </Text>

      <ScrollView
        style={{ maxHeight: 280, backgroundColor: '#1D1D28', borderRadius: 12, padding: 14 }}
      >
        <Text selectable style={{ color: '#FF8A9B', fontSize: 13, fontWeight: '600' }}>
          {error?.name ?? 'Error'}: {error?.message ?? 'no message'}
        </Text>
        {__DEV__ && !!error?.stack && (
          <Text selectable style={{ color: '#8A8A9A', fontSize: 11, marginTop: 10 }}>
            {error.stack}
          </Text>
        )}
      </ScrollView>

      <Pressable
        onPress={retry}
        style={{
          marginTop: 20,
          backgroundColor: '#FF4D67',
          borderRadius: 12,
          paddingVertical: 15,
          alignItems: 'center',
        }}
      >
        <Text style={{ color: '#FFFFFF', fontSize: 16, fontWeight: '600' }}>{copy.retry}</Text>
      </Pressable>

      <Text style={{ color: '#6A6A7A', fontSize: 12, marginTop: 14, textAlign: 'center' }}>
        {copy.copyHint}
      </Text>
    </View>
  );
}
