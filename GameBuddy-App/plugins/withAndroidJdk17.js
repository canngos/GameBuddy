const { withGradleProperties } = require('expo/config-plugins');

/**
 * Pins the Android build to JDK 17.
 *
 * Not a preference — without it the build fails outright on this machine. The Android
 * Gradle plugin runs Prefab in a forked JVM and treats *any* stderr from it as the task
 * having failed; on JDK 25 that JVM prints a "restricted method in java.lang.System has
 * been called" warning, so every native module (worklets, screens, reanimated) failed to
 * configure with that warning as its error message. Nothing was actually wrong.
 *
 * Written as a config plugin rather than edited into `android/gradle.properties` because
 * `expo prebuild` regenerates that file from its template and silently drops the setting
 * — which it did, once, turning a working build back into the same confusing failure.
 * A plugin runs as part of prebuild, so the property is put back every time.
 *
 * The JDK path is read from `GAMEBUDDY_ANDROID_JDK` when set, so this does not hard-code
 * one developer's machine into the repository. The default is where Gradle's own
 * toolchain provisioning puts the JDK it downloads, which is why nothing needed
 * installing here.
 */
const DEFAULT_JDK = 'C:/Users/canba/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2';

module.exports = function withAndroidJdk17(config) {
  // Nothing to fix anywhere but this Windows machine, and pinning a *Windows* path on a
  // Linux builder is worse than not pinning at all — EAS Build would write
  // `org.gradle.java.home=C:/Users/...` into gradle.properties and fail before it
  // compiled a line. EAS images already run a supported JDK, so the right behaviour off
  // Windows is to leave the property alone.
  //
  // An explicit GAMEBUDDY_ANDROID_JDK still wins on any platform: somebody who has set it
  // has said what they want.
  const explicit = process.env.GAMEBUDDY_ANDROID_JDK;
  if (!explicit && process.platform !== 'win32') {
    return config;
  }

  return withGradleProperties(config, (cfg) => {
    // Forward slashes, always. gradle.properties is a Java properties file, where a
    // backslash is an escape character — a Windows path written literally arrives as
    // "C:Userscanba.gradle..." and Gradle rejects it as an invalid Java home. Java itself
    // accepts forward slashes on Windows, so this is the honest spelling rather than a
    // workaround.
    const jdk = (process.env.GAMEBUDDY_ANDROID_JDK || DEFAULT_JDK).replace(/\\/g, '/');

    // Replace rather than append: prebuild may already have written one, and two
    // definitions of the same property is a coin toss over which wins.
    cfg.modResults = cfg.modResults.filter(
      (item) => !(item.type === 'property' && item.key === 'org.gradle.java.home'),
    );
    cfg.modResults.push({
      type: 'comment',
      value:
        'Android builds run on JDK 17. See plugins/withAndroidJdk17.js — on JDK 25 the ' +
        'Prefab step fails with a JVM warning it mistakes for an error.',
    });
    cfg.modResults.push({
      type: 'property',
      key: 'org.gradle.java.home',
      value: jdk,
    });
    return cfg;
  });
};
