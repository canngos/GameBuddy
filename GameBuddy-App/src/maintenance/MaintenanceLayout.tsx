import { useFonts } from 'expo-font';
import * as SplashScreen from 'expo-splash-screen';
import { StatusBar } from 'expo-status-bar';
import { useEffect } from 'react';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { fontAssets, useIsDark, useScheme } from '../theme';
import { ScreenScaleProvider } from '../ui/useScreenScale';
import { MaintenanceScreen } from './MaintenanceScreen';

/** Only local presentation setup. No session restoration or app providers mount here. */
export function MaintenanceLayout() {
  const [fontsLoaded, fontError] = useFonts(fontAssets);
  const hydrated = useScheme((s) => s.hydrated);
  const loadScheme = useScheme((s) => s.load);
  const ready = (fontsLoaded || !!fontError) && hydrated;

  useEffect(() => {
    void loadScheme();
  }, [loadScheme]);

  useEffect(() => {
    if (ready) void SplashScreen.hideAsync();
  }, [ready]);

  if (!ready) return null;

  return (
    <SafeAreaProvider>
      <ScreenScaleProvider>
        <MaintenanceContent />
      </ScreenScaleProvider>
    </SafeAreaProvider>
  );
}

function MaintenanceContent() {
  const isDark = useIsDark();

  return (
    <>
      <StatusBar style={isDark ? 'light' : 'dark'} />
      <MaintenanceScreen />
    </>
  );
}
