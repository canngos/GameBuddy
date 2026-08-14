#!/usr/bin/env node
/**
 * Re-enables Crashlytics on the EAS builder.
 *
 * `package.json` carries `expo.autolinking.android.exclude` for the two Firebase packages,
 * because a local Windows build cannot compile them: React Native's CMake step shells out to
 * `ninja`, which is capped at Windows' 260-character `MAX_PATH`, and the file Crashlytics'
 * codegen emits is 283 characters at this repo's location. The machine's `LongPathsEnabled`
 * key is already `1` and does not help — it only lifts the limit for executables that
 * declare `longPathAware`, which `ninja` does not — and a `subst` drive does not help either,
 * because CMake resolves it back to the real path.
 *
 * EAS builds on Linux and has no such limit, so the exclusion is wrong there and this removes
 * it. Run as the `eas-build-pre-install` npm script, which EAS invokes on the builder before
 * installing dependencies — early enough that autolinking never sees the exclusion.
 *
 * The switch has to live in `package.json` rather than somewhere conditional: Expo reads
 * autolinking config from static JSON, so there is no `process.env` to branch on at that
 * point. Hence a script that edits the file instead. `app.config.js` branches on the same
 * `EAS_BUILD` variable for the config plugins, and the two must stay in step — the plugin
 * without the native module configures Firebase for something that is not there, and the
 * native module without the plugin has no `google-services.json` wired in.
 *
 * Deliberately not conditional on `EAS_BUILD` itself. This script only ever runs from the
 * hook, and a developer who runs it by hand has said what they want; refusing would only be
 * confusing. It is idempotent and a no-op when there is nothing to remove.
 */
const fs = require('fs');
const path = require('path');

const file = path.join(__dirname, '..', 'package.json');
const pkg = JSON.parse(fs.readFileSync(file, 'utf8'));

const exclude = pkg.expo?.autolinking?.android?.exclude;
if (!exclude) {
  console.log('[crashlytics] no autolinking exclusion to remove');
  process.exit(0);
}

const kept = exclude.filter((name) => !name.startsWith('@react-native-firebase/'));
if (kept.length) {
  pkg.expo.autolinking.android.exclude = kept;
} else {
  delete pkg.expo.autolinking.android.exclude;
  if (Object.keys(pkg.expo.autolinking.android).length === 0) delete pkg.expo.autolinking.android;
  if (Object.keys(pkg.expo.autolinking).length === 0) delete pkg.expo.autolinking;
  if (Object.keys(pkg.expo).length === 0) delete pkg.expo;
}

fs.writeFileSync(file, `${JSON.stringify(pkg, null, 2)}\n`);
console.log('[crashlytics] autolinking exclusion removed; Crashlytics will be built');
