/**
 * Lets the Firebase config arrive from EAS instead of from the repository.
 *
 * `app.json` stays the source of truth for everything static — this file receives it as
 * `config` and changes exactly one field.
 *
 * `google-services.json` is gitignored (see the workspace root `.gitignore`, which
 * excludes every Firebase and service-account file). EAS Build uploads only what git
 * tracks, so the builder never receives it and the build fails with:
 *
 *     "google-services.json" is missing, make sure that the file exists.
 *
 * A `.easignore` cannot fix that: it can only *exclude* files from the upload, never
 * include one git does not track.
 *
 * So the file is stored as an EAS **file environment variable**, which EAS writes to a
 * temporary path on the builder and exposes as `GOOGLE_SERVICES_JSON`. Locally the
 * variable is unset and the path in `app.json` is used, so `expo prebuild` and
 * `expo run:android` keep working with no setup.
 *
 * To upload it, once:
 *
 *   eas env:set --scope project --name GOOGLE_SERVICES_JSON \
 *     --type file --value ./google-services.json \
 *     --visibility sensitive --environment production --environment preview
 */
/**
 * Swaps the AdMob test app ids for the real ones when the environment supplies them.
 *
 * `app.json` holds Google's published **test** ids, and that is deliberate rather than a
 * placeholder: they are valid ids, so a build made without any AdMob environment set still
 * runs and still shows adverts — test ones, which pay nobody and cannot get the account
 * banned for invalid traffic.
 *
 * A `${VAR}` in `app.json` would not have worked. That file is static JSON with no
 * interpolation, so the literal string would reach `AndroidManifest.xml`, and the Google
 * Mobile Ads SDK **crashes the app at launch** on a malformed application id — which is
 * exactly the failure mode a placeholder is supposed to avoid.
 *
 * **The app id and the rewarded unit id must belong to the same AdMob account.** They are
 * set in two different places — this variable writes the manifest, and
 * `EXPO_PUBLIC_ADMOB_REWARDED_UNIT_ID` is read at runtime by `src/ads/rewarded.ts` — and
 * for a while only the second of them was in `eas.json`. Every store build therefore asked
 * for our live unit while identifying itself as Google's sample app, which AdMob refuses:
 * the load fails, and testers reported the advert simply never opening. Local builds hid it
 * because `.env` supplies this id and `__DEV__` forces the test unit, so the pair matched
 * on the one machine anybody was looking at.
 *
 * A real app id with Google's *test* unit is fine and is what the `gate` and `lan` profiles
 * inherit — test units are not tied to an account. It is only the live unit that has to be
 * asked for by the app that owns it.
 *
 * **This package is also why `app.json` pins `platforms` to Android and iOS.** Left unset,
 * Expo exports every platform it supports, web included, and a web bundle fails outright:
 * `react-native-google-mobile-ads` reaches for `codegenNativeComponent`, and importing React
 * Native internals is not supported on web. The lazy `require` in `src/ads/sdk.ts` does not
 * help — Metro resolves a literal `require` at build time whether or not it ever runs.
 *
 * Nothing is lost by dropping web: this app has never been served from a browser, and
 * GameBuddy's actual website is the separate Astro project in `GameBuddy-Web`. The `web`
 * favicon key left in `app.json` is scaffolding. Without the pin, `eas update` fails on the
 * web bundle before it can publish anything for the platforms that are shipped.
 */
function adMobPlugin(plugins, androidAppId, iosAppId) {
  return plugins.map((plugin) => {
    if (!Array.isArray(plugin) || plugin[0] !== 'react-native-google-mobile-ads') {
      return plugin;
    }
    return [
      plugin[0],
      {
        ...plugin[1],
        androidAppId: androidAppId ?? plugin[1].androidAppId,
        iosAppId: iosAppId ?? plugin[1].iosAppId,
      },
    ];
  });
}

/**
 * Whether this build can compile Crashlytics.
 *
 * True on EAS, false on a local Windows machine, and that split is not a preference — it is
 * a hard limit. React Native's CMake build shells out to `ninja`, which is capped at
 * Windows' 260-character `MAX_PATH` because it does not declare `longPathAware` in its
 * manifest, and the file Crashlytics' codegen emits is 283 characters at this repo's
 * location:
 *
 *   …/node_modules/@react-native-firebase/crashlytics/android/src/main/java/io/invertase/
 *   firebase/crashlytics/generated/jni/react/renderer/components/
 *   RNFBCrashlyticsTurboModules/RNFBCrashlyticsTurboModulesJSI-generated.cpp
 *
 * The machine's `LongPathsEnabled` registry key is already `1` and makes no difference,
 * because that setting only lifts the limit for executables that opt in. Mapping the repo to
 * a short drive with `subst` makes no difference either: CMake resolves the virtual drive
 * back to its real target. EAS builds on Linux, which has no such limit.
 *
 * So the store build gets crash reporting and the local development build does not. The cost
 * of that is real and worth stating: the two builds no longer contain the same native
 * modules, so a fault in Crashlytics' own initialisation would first appear on EAS. It is
 * accepted here because the alternative is shipping with no way at all to see the native
 * crash this exists to catch — see QA_FINDINGS.md #6.
 *
 * **This is only half the switch, and the other half is not optional.** Autolinking finds a
 * native module from `package.json` alone, so dropping the config plugin here changes
 * nothing on its own — that was verified the expensive way, with a build that still failed.
 * `package.json` carries `expo.autolinking.android.exclude` for the same two packages, and
 * `scripts/eas-enable-crashlytics.js` removes it on the builder via the
 * `eas-build-pre-install` hook. Change one and you must change the other: the plugin without
 * the module configures Firebase for something that is not there, and the module without the
 * plugin has no `google-services.json` wired in.
 *
 * `EAS_BUILD` is set to "true" by the EAS builder itself.
 */
const crashReportingBuildable = process.env.EAS_BUILD === 'true';

const CRASHLYTICS_PLUGINS = ['@react-native-firebase/app', '@react-native-firebase/crashlytics'];

/**
 * Whether iOS has what Firebase needs.
 *
 * Android is configured: `google-services.json` is uploaded to EAS as a file environment
 * variable and referenced through `android.googleServicesFile`. **iOS is not.** There is no
 * `GoogleService-Info.plist` and no `ios.googleServicesFile`, and the two are not
 * interchangeable — a Firebase project needs an iOS app registered separately, which
 * produces a different file with a different bundle id inside it.
 *
 * Without it the `@react-native-firebase/app` plugin does not skip iOS quietly, it throws:
 *
 *     Path to GoogleService-Info.plist is not defined.
 *     Please specify the `expo.ios.googleServicesFile` field in app.json.
 *
 * — so an iOS build would fail outright rather than simply ship without crash reporting.
 * This check turns that into a warning and an iOS build that still works, because the
 * failure would otherwise land on whoever first runs `eas build --platform ios`, long after
 * the reason was fresh in anyone's mind.
 *
 * To finish it: register an iOS app in the Firebase console under the bundle id
 * `com.findgamebuddy.app`, download `GoogleService-Info.plist`, upload it to EAS the same way as
 * the Android one, and set `ios.googleServicesFile`. Android-first is the plan, so this is a
 * task for the Apple release rather than a gap in this one.
 */
function iosFirebaseConfigured(config) {
  return Boolean(process.env.GOOGLE_SERVICES_INFO_PLIST || config.ios?.googleServicesFile);
}

module.exports = ({ config }) => {
  const buildingForIos = process.env.EAS_BUILD_PLATFORM === 'ios';
  const crashReporting =
    crashReportingBuildable && !(buildingForIos && !iosFirebaseConfigured(config));

  if (crashReportingBuildable && !crashReporting) {
    console.warn(
      '[crashlytics] iOS has no GoogleService-Info.plist, so crash reporting is being left ' +
        'out of this build. Register an iOS app in the Firebase console for bundle id ' +
        'com.findgamebuddy.app, then set expo.ios.googleServicesFile. See app.config.js.',
    );
  }

  return {
    ...config,
    plugins: [
      ...adMobPlugin(
        config.plugins,
        process.env.ADMOB_ANDROID_APP_ID,
        process.env.ADMOB_IOS_APP_ID,
      ),
      ...(crashReporting ? CRASHLYTICS_PLUGINS : []),
    ],
    android: {
      ...config.android,
      googleServicesFile: process.env.GOOGLE_SERVICES_JSON ?? config.android.googleServicesFile,
    },
    ios: {
      ...config.ios,
      ...(process.env.GOOGLE_SERVICES_INFO_PLIST
        ? { googleServicesFile: process.env.GOOGLE_SERVICES_INFO_PLIST }
        : {}),
    },
  };
};
