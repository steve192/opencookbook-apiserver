import js from '@eslint/js';
import react from 'eslint-plugin-react';
import globals from 'globals';
import tseslint from 'typescript-eslint';

// Replaces the previous .eslintrc.yml. eslint-config-google, which used to supply the
// style rules, is unmaintained and never gained flat config support, so the baseline is
// now the recommended sets of eslint, typescript-eslint and the react plugin.
//
// Two versions are held back by what this config needs, not by choice: eslint stays on 9
// because eslint-plugin-react accepts no more than ^9.7, and typescript stays on 5
// because typescript-eslint accepts no more than <6.1. Both move once those release.
export default tseslint.config(
    {ignores: ['node/', 'node_modules/']},
    {
      files: ['**/*.{ts,tsx}'],
      extends: [js.configs.recommended, tseslint.configs.recommended],
      languageOptions: {
        globals: globals.browser,
        parserOptions: {ecmaFeatures: {jsx: true}},
      },
      plugins: {react},
      settings: {react: {version: 'detect'}},
      rules: {
        ...react.configs.flat.recommended.rules,
        // The automatic JSX runtime does not need React in scope.
        'react/react-in-jsx-scope': 'off',
        // The props are typed, which is what prop-types would be checking.
        'react/prop-types': 'off',
      },
    },
    // The build tooling runs in node rather than in the browser.
    {
      files: ['vite.config.ts', 'scripts/**/*.mjs', 'eslint.config.js'],
      extends: [js.configs.recommended],
      languageOptions: {globals: globals.node},
    },
);
