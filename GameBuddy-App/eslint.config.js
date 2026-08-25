const expoConfig = require('eslint-config-expo/flat');

module.exports = [
  ...expoConfig,
  {
    ignores: ['dist/**', 'android/**', '.expo/**', 'node_modules/**'],
  },
  {
    rules: {
      // Reanimated's entire API is writing to shared values (`sv.value = ...`);
      // the compiler-based rule flags every one of the ~30 animation sites.
      'react-hooks/immutability': 'off',
      // The flagged effects are deliberate sync-on-open / reset-on-change
      // patterns, each documented in place. Warn, don't block.
      'react-hooks/set-state-in-effect': 'warn',
    },
  },
];
