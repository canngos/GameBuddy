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
module.exports = ({ config }) => ({
  ...config,
  android: {
    ...config.android,
    googleServicesFile: process.env.GOOGLE_SERVICES_JSON ?? config.android.googleServicesFile,
  },
});
