const { withAndroidManifest } = require('expo/config-plugins');

/**
 * Allows plain HTTP, but only when the build is actually pointed at a plain-HTTP backend.
 *
 * Android has blocked cleartext traffic by default since API 28. Expo's template turns it
 * back on in the **debug** manifest only, which is right — and which is why a release APK
 * installed on a phone cannot reach `http://192.168.x.x:8080` and fails with a network
 * error that says nothing about why.
 *
 * Rather than switching cleartext on for every build and hoping nobody ships that, this
 * reads `EXPO_PUBLIC_API_URL` — the same variable that decides where the app sends its
 * requests. If it is `http://`, cleartext is enabled, because the build would be useless
 * without it. If it is `https://` or unset, the flag is left alone and the default
 * protection stands.
 *
 * The consequence worth stating plainly: a production build must use `https://`. If it
 * does, this plugin does nothing at all, which is the point.
 */
module.exports = function withLanCleartext(config) {
  return withAndroidManifest(config, (cfg) => {
    const apiUrl = process.env.EXPO_PUBLIC_API_URL ?? '';
    if (!apiUrl.startsWith('http://')) {
      return cfg;
    }

    const application = cfg.modResults.manifest.application?.[0];
    if (!application) return cfg;

    application.$['android:usesCleartextTraffic'] = 'true';
    // eslint-disable-next-line no-console
    console.log(`[withLanCleartext] EXPO_PUBLIC_API_URL is ${apiUrl} — enabling cleartext HTTP.`);

    return cfg;
  });
};
