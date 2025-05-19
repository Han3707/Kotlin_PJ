import 'styled-components';

declare module 'styled-components' {
  export interface DefaultTheme {
    colors: {
      primary: string;
      secondary: string;
      accent: string;
      background: string;
      backgroundSecondary: string;
      text: string;
      textSecondary: string;
      white: string;
      black: string;
      glow: string;
      overlay: string;
    },
    fonts: {
      primary: string;
    },
    breakpoints: {
      mobile: string;
      tablet: string;
      desktop: string;
      large: string;
    },
    spacing: {
      xs: string;
      sm: string;
      md: string;
      lg: string;
      xl: string;
    }
  }
}
