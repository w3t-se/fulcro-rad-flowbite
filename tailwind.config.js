const defaultTheme = require('tailwindcss/defaultTheme')

module.exports = {
  darkMode: "class",
  // in prod look at shadow-cljs output file in dev look at runtime, which will change files that are actually compiled; postcss watch should be a whole lot faster
  content: process.env.NODE_ENV == 'production' ? ["./node_modules/flowbite/**/*.{js,jsx,ts,tsx}", "./respources/public/js/main.js"] : ["./node_modules/flowbite/**/*.{js,jsx,ts,tsx}", "./resources/public/js/main/cljs-runtime/*.{js,jsx,ts,tsx}"],
  theme: {
    extend: {
      fontFamily: {
        sans: ["Inter var", ...defaultTheme.fontFamily.sans],
      },
    },
  },
  plugins: [
    require('@tailwindcss/forms'),
    require('flowbite/plugin')
  ],
}
