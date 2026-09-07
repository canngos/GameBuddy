#!/usr/bin/env node
/**
 * Refuses to pass while a dependency exists that nobody has classified for Play's Data
 * safety form.
 *
 * Google's Data safety declaration covers "data collected and handled through any
 * third-party libraries or SDKs used in their apps", and Play Console runs automated checks
 * against the uploaded binary: a data type an SDK touches and the form does not declare is
 * flagged before a human sees it. The declaration is also required to match the privacy
 * policy, so an undeclared SDK is simultaneously a Play violation and a GDPR one.
 *
 * That failure is invisible at the moment it is introduced. `npm install some-analytics-sdk`
 * is one command, it does not change a screen, and the consequence surfaces weeks later in a
 * review rejection — by which time nobody remembers which commit added it. This is the check
 * that moves the discovery back to the install.
 *
 * The answers live in `store-listing/PLAY_DATA_SAFETY.md` section 5. This file is the same
 * list in executable form, and the two are meant to be edited together.
 *
 * Run: npm run check
 */

const { dependencies } = require('../package.json');

/**
 * Every dependency, and what it sends off the device.
 *
 * `collects: false` means the package cannot move personal data off the device on its own —
 * UI, navigation, styling, state, or something that only reads locally. On-device-only
 * processing is exempt from disclosure, which is why those are safe to mark inert.
 *
 * `collects: true` means it drives at least one row of the form, named in `rows`. Removing
 * one of those is as much a change to the declaration as adding one, so this script treats a
 * disappearance as a failure too.
 */
const CLASSIFIED = {
  'react-native-google-mobile-ads': {
    collects: true,
    sends: 'Advertising ID, IP address, account id, ad-view diagnostics — to Google, on consent',
    rows: 'User IDs (shared), Device or other IDs (shared)',
  },
  '@react-native-firebase/crashlytics': {
    collects: true,
    sends: 'Stack traces, device and OS version, account id',
    rows: 'Crash logs, Diagnostics',
  },
  '@react-native-firebase/app': {
    collects: false,
    sends: 'Nothing on its own — the container the Firebase modules need',
  },
  'react-native-purchases': {
    collects: true,
    sends: 'Account id, entitlements, purchase state — to RevenueCat, a processor',
    rows: 'Purchase history',
  },
  'expo-notifications': {
    collects: true,
    sends: 'The FCM device token, straight to Firebase — no Expo push service is involved',
    rows: 'Device or other IDs',
  },
  'expo-updates': {
    collects: true,
    sends: 'IP address and app/runtime version, to u.expo.dev',
    rows: 'None — IP alone is not a declarable type unless location is derived from it, and '
      + 'nothing here does. Disclosed in PRIVACY.md section 5 regardless',
  },
  'expo-image-picker': { collects: false, sends: 'Hands a chosen image to our own upload' },
  'expo-image-manipulator': { collects: false, sends: 'Resizes locally' },
  'expo-file-system': { collects: false, sends: 'Local filesystem access' },
  'expo-document-picker': { collects: false, sends: 'Hands a chosen file to our own upload' },
  'expo-device': { collects: false, sends: 'Reads device model locally' },
  'expo-constants': { collects: false, sends: 'Reads app config locally' },
  'expo-secure-store': { collects: false, sends: 'Keychain, on device' },
  'expo-linking': { collects: false, sends: 'Deep links, on device' },

  // Inert: UI, navigation, state, styling, media playback, fonts, build tooling.
  '@expo-google-fonts/chakra-petch': { collects: false, sends: 'Bundled font' },
  '@expo-google-fonts/poppins': { collects: false, sends: 'Bundled font' },
  '@stomp/stompjs': { collects: false, sends: 'Talks to our own server only' },
  '@tanstack/react-query': { collects: false, sends: 'Caches our own API responses' },
  'babel-preset-expo': { collects: false, sends: 'Build tooling' },
  clsx: { collects: false, sends: 'String helper' },
  expo: { collects: false, sends: 'The framework itself' },
  'expo-audio': { collects: false, sends: 'Plays bundled sound' },
  'expo-blur': { collects: false, sends: 'Visual effect' },
  'expo-font': { collects: false, sends: 'Loads bundled fonts' },
  'expo-haptics': { collects: false, sends: 'Vibration' },
  'expo-image': { collects: false, sends: 'Renders images' },
  'expo-linear-gradient': { collects: false, sends: 'Visual effect' },
  'expo-router': { collects: false, sends: 'Navigation' },
  'expo-splash-screen': { collects: false, sends: 'Launch screen' },
  // Hands the review card to the Play Store app, which then talks to Google on its own
  // account. Nothing about the gamer crosses this boundary -- the app cannot even find
  // out whether the card appeared, let alone what was written on it.
  'expo-store-review': { collects: false, sends: 'Asks the Play Store app to show its review card' },
  // Google's own sign-in, through Android's Credential Manager. It sends the account the
  // person picks -- their Google email, name and profile picture URL, inside a signed ID
  // token -- to Google, and hands the token back to us. Only on a tap, and only what the
  // `openid email profile` scopes cover; no contacts, no Drive, nothing continuous.
  'react-native-nitro-google-signin': {
    collects: true,
    sends: 'Google account email, name and photo URL to Google, when the user taps Sign in with Google',
  },
  // The bridge the package above is built on. Native plumbing; talks to nothing itself.
  'react-native-nitro-modules': { collects: false, sends: 'Native module bridge' },
  'expo-status-bar': { collects: false, sends: 'Status bar styling' },
  'expo-system-ui': { collects: false, sends: 'System UI styling' },
  'lucide-react-native': { collects: false, sends: 'Icons' },
  nativewind: { collects: false, sends: 'Styling' },
  react: { collects: false, sends: 'The framework' },
  'react-dom': { collects: false, sends: 'The framework, web target' },
  'react-native': { collects: false, sends: 'The framework' },
  'react-native-gesture-handler': { collects: false, sends: 'Touch handling' },
  'react-native-keyboard-controller': { collects: false, sends: 'Keyboard handling' },
  'react-native-reanimated': { collects: false, sends: 'Animation' },
  'react-native-safe-area-context': { collects: false, sends: 'Layout insets' },
  'react-native-screens': { collects: false, sends: 'Native navigation containers' },
  'react-native-svg': { collects: false, sends: 'Vector rendering' },
  'react-native-web': { collects: false, sends: 'Web target' },
  'react-native-worklets': { collects: false, sends: 'Animation runtime' },
  'tailwind-merge': { collects: false, sends: 'Class-name helper' },
  tailwindcss: { collects: false, sends: 'Styling' },
  zustand: { collects: false, sends: 'State' },
};

const SHEET = 'store-listing/PLAY_DATA_SAFETY.md';

const installed = Object.keys(dependencies ?? {});
const unclassified = installed.filter((name) => !(name in CLASSIFIED));
const vanished = Object.keys(CLASSIFIED).filter(
  (name) => CLASSIFIED[name].collects && !installed.includes(name),
);

if (unclassified.length === 0 && vanished.length === 0) {
  const collecting = installed.filter((name) => CLASSIFIED[name].collects);
  console.log(
    `[sdk] ${installed.length} dependencies classified, ${collecting.length} send data off the device`,
  );
  process.exit(0);
}

console.error('\n[sdk] The Data safety declaration no longer matches what is installed:\n');

for (const name of unclassified) {
  console.error(`  ✖ ${name} is installed and nobody has said what it sends off the device`);
}
for (const name of vanished) {
  console.error(
    `  ✖ ${name} is gone, and it drove: ${CLASSIFIED[name].rows}. Those rows may now be wrong`,
  );
}

console.error(
  `\nDecide what it collects, then update ${SHEET} section 5 and the list in this file\n` +
    'together. If it sends nothing off the device, say so — that is an answer, and recording\n' +
    'it is what stops the next person having to work it out again.\n',
);
process.exit(1);
