const { withProjectBuildGradle } = require('expo/config-plugins');

/**
 * Pins the Android build to Kotlin 2.3.
 *
 * Not a preference — without it the build fails outright. `react-native-google-mobile-ads`
 * pins `com.google.android.gms:play-services-ads:25.4.0`, and that artifact ships Kotlin
 * **2.3.0** metadata. Expo SDK 57 compiles at 2.1.0, and a Kotlin compiler cannot read
 * metadata from a newer one, so `:react-native-google-mobile-ads:compileDebugKotlin` fails
 * with a hundred lines of "Module was compiled with an incompatible version of Kotlin" and
 * nothing else in the log to say which dependency caused it.
 *
 * Two things about the fix are non-obvious, and both cost an attempt each:
 *
 * 1. **`ext.kotlinVersion` alone does not do it.** `ExpoRootProjectPlugin` reads that
 *    (`setIfNotExist("kotlinVersion")`), and setting it does change what Expo reports in
 *    its startup banner — but by then the root `buildscript {}` block has already resolved
 *    `kotlin-gradle-plugin` with no version, and a subproject cannot swap out a plugin its
 *    parent already loaded. The classpath entry itself has to carry the version. Both are
 *    set below: the classpath is what actually binds, and the `ext` is what third-party
 *    modules read — `react-native-google-mobile-ads` resolves its own Kotlin plugin
 *    through `rootProject.ext.kotlinVersion`, defaulting to 1.8.22 if it is missing.
 *
 * 2. **It has to be a config plugin.** `android/` is gitignored here — the native
 *    directories are prebuild artifacts, so an edit to `android/build.gradle` survives
 *    exactly until the next `expo prebuild`. Same reasoning as `withAndroidJdk17.js` next
 *    door, which learned it the hard way.
 *
 * Raising Kotlin rather than holding the ads SDK back is the right direction: a newer
 * compiler reads older metadata without complaint, so this unblocks the one module that
 * needs 2.3 without asking every other module to move. Revisit when Expo's own default
 * reaches 2.3 — at that point this file is dead weight and should go.
 */
const KOTLIN_VERSION = '2.3.0';

module.exports = function withKotlinVersion(config) {
  return withProjectBuildGradle(config, (cfg) => {
    if (cfg.modResults.language !== 'groovy') {
      throw new Error(
        'withKotlinVersion: expected a Groovy android/build.gradle, got ' +
          cfg.modResults.language,
      );
    }

    let contents = cfg.modResults.contents;

    // Idempotent: prebuild can run repeatedly, and appending a second pin would leave two
    // definitions of the same thing with no telling which wins.
    if (contents.includes('kotlin-gradle-plugin:')) {
      return cfg;
    }

    // The classpath entry Expo writes carries no version. Give it one.
    const versionless = "classpath('org.jetbrains.kotlin:kotlin-gradle-plugin')";
    if (!contents.includes(versionless)) {
      throw new Error(
        'withKotlinVersion: could not find the versionless kotlin-gradle-plugin classpath ' +
          'in android/build.gradle. Expo may have changed its template — re-check the fix ' +
          'rather than letting the build silently fall back to an unpinned Kotlin.',
      );
    }
    contents = contents.replace(
      versionless,
      `classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${KOTLIN_VERSION}")`,
    );

    // And the ext that third-party modules read. Placed just before expo-root-project so
    // its `setIfNotExist` finds a value already there.
    contents = contents.replace(
      'apply plugin: "expo-root-project"',
      `ext.kotlinVersion = "${KOTLIN_VERSION}"\n\napply plugin: "expo-root-project"`,
    );

    cfg.modResults.contents = contents;
    return cfg;
  });
};
