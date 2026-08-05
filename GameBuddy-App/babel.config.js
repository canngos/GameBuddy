// Added for NativeWind. There was no babel.config.js before — the blank template does
// not ship one, which is also why `babel-preset-expo` was not a direct dependency and
// had to be installed explicitly. Without it Metro fails to construct its transformer
// and reports a misleading "Cannot read properties of undefined (reading
// 'transformFile')" rather than the missing module.
//
// `jsxImportSource: 'nativewind'` is the load-bearing part: it routes JSX through the
// css-interop runtime so `className` means something on a React Native component.
// Without it every className is silently ignored and nothing is styled.
module.exports = function (api) {
  api.cache(true);
  return {
    presets: [
      ['babel-preset-expo', { jsxImportSource: 'nativewind' }],
      'nativewind/babel',
    ],
  };
};
