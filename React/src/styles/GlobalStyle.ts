import { createGlobalStyle } from 'styled-components';

export const GlobalStyle = createGlobalStyle`
  /* SF Pro 폰트 사용 (CDN을 통해) */
  /* @import url('https://fonts.cdnfonts.com/css/sf-pro-display'); */ /* Removed, now in index.html */
  
  * {
    box-sizing: border-box;
    margin: 0;
    padding: 0;
  }
  
  html, body {
    font-family: ${props => props.theme.fonts.primary};
    background-color: ${props => props.theme.colors.background};
    color: ${props => props.theme.colors.text};
    /* -webkit-font-smoothing: antialiased;  */ /* Temporarily commented out */
    /* -moz-osx-font-smoothing: grayscale; */ /* Temporarily commented out */
    /* text-rendering: optimizeLegibility; */ /* Temporarily commented out */
    overflow-x: hidden;
    line-height: 1.5;
    background-image: url('https://images.unsplash.com/photo-1462331940025-496dfbfc7564?q=100&w=2400&auto=format&fit=crop'); 
    background-size: cover;
    background-position: center;
    background-attachment: fixed;
    background-blend-mode: normal;
    min-height: 100vh;
  }
  
  body {
    min-height: 100vh;
  }
  
  h1, h2, h3, h4, h5, h6 {
    font-weight: 600;
    letter-spacing: -0.015em;
  }
  
  button {
    cursor: pointer;
    font-family: inherit;
    border: none;
    outline: none;
    border-radius: 980px; /* 애플 버튼 스타일 */
    transition: all 0.3s ease;
  }
  
  a {
    text-decoration: none;
    color: ${props => props.theme.colors.primary};
    transition: opacity 0.2s ease;
    
    &:hover {
      opacity: 0.8;
    }
  }
  
  /* 애플 스타일 스크롤바 */
  ::-webkit-scrollbar {
    width: 8px;
    height: 8px;
  }
  
  ::-webkit-scrollbar-track {
    background: ${props => props.theme.colors.background};
  }
  
  ::-webkit-scrollbar-thumb {
    background: #d1d1d6;
    border-radius: 4px;
  }
  
  ::-webkit-scrollbar-thumb:hover {
    background: #a1a1a6;
  }
`;