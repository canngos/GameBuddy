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

module.exports = ({ config }) => ({
  ...config,
  plugins: adMobPlugin(
    config.plugins,
    process.env.ADMOB_ANDROID_APP_ID,
    process.env.ADMOB_IOS_APP_ID,
  ),
  android: {
    ...config.android,
    googleServicesFile: process.env.GOOGLE_SERVICES_JSON ?? config.android.googleServicesFile,
  },
});
