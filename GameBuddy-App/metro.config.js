const { getDefaultConfig } = require('expo/metro-config');
const { withNativeWind } = require('nativewind/metro');

// NativeWind compiles global.css through Tailwind at bundle time and injects the
// result, so the stylesheet has to be named here rather than imported for its side
// effects alone.
const config = getDefaultConfig(__dirname);

module.exports = withNativeWind(config, { input: './global.css' });
