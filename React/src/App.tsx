import React from 'react';
import styled, { ThemeProvider } from 'styled-components';
import { GlobalStyle } from './styles/GlobalStyle';
import { theme } from './styles/theme';
// Header 제거
import Hero from './components/Hero';
import FeatureStrip from './components/FeatureStrip';
import MeshBackground from './components/MeshBackground';

const AppContainer = styled.div`
  position: relative;
  width: 100%;
  height: 100vh; /* 스크롤 스냅 컨테이너는 명확한 높이 필요 */
  background-color: ${props => props.theme.colors.background};
  overflow-x: hidden; 
  overflow-y: scroll; /* 스크롤 가능하도록 변경 */
  scroll-snap-type: y mandatory; /* 세로 방향 스크롤 스냅 강제 */
`;

const Section = styled.div`
  position: relative;
  width: 100%;
  height: 100vh; /* 각 섹션이 전체 뷰포트 높이를 차지하도록 */
  padding: 0 clamp(1rem, 5vw, 3rem);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  box-sizing: border-box; 
  scroll-snap-align: start; /* 섹션의 시작 부분이 스냅되도록 */

  /* Hero가 아닌 다른 일반 섹션에 대한 스타일 조정 불필요 (모든 섹션 100vh) */
  /* &:not(:first-child) {
    min-height: auto; 
    padding-top: 5rem; 
    padding-bottom: 5rem;
  } */
`;

const App: React.FC = () => {
  return (
    <ThemeProvider theme={theme}>
      <GlobalStyle />
      <AppContainer>
        <MeshBackground />
        
        <Section>
          <Hero />
        </Section>

        <Section>
          <FeatureStrip />
        </Section>

      </AppContainer>
    </ThemeProvider>
  );
};

export default App;
