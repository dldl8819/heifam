import nextVitals from 'eslint-config-next/core-web-vitals'

// eslint-config-next 16 ships flat config of its own, so no compatibility layer is needed.
const config = [
  { ignores: ['.next/**', 'node_modules/**', 'out/**', 'next-env.d.ts'] },
  ...nextVitals,
  {
    // New with eslint-plugin-react-hooks 7 (eslint-config-next 16) and written for the React
    // Compiler, which this app does not turn on. Code from before them trips them in about sixty
    // places that work as intended; they stay off until that code is reworked on its own.
    rules: {
      'react-hooks/set-state-in-effect': 'off',
      'react-hooks/refs': 'off',
      'react-hooks/immutability': 'off',
    },
  },
]

export default config
