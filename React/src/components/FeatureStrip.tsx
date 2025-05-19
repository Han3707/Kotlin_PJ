import React from 'react';
import styled, { keyframes } from 'styled-components';

const StripContainer = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0;
  padding: 100px 0;
  background-color: rgba(15, 15, 30, 0.7);
  position: relative;
  border-top: 1px solid rgba(0, 170, 255, 0.1);
  border-bottom: 1px solid rgba(0, 170, 255, 0.1);
  
  &:before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background: radial-gradient(ellipse at center, rgba(0, 170, 255, 0.1) 0%, transparent 70%);
    z-index: 0;
  }
  
  @media (min-width: ${props => props.theme.breakpoints.tablet}) {
    flex-direction: row;
    gap: 0;
  }
`;

const FeatureItem = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  max-width: 300px;
  padding: 30px;
  position: relative;
  z-index: 1;
  margin: 10px;
  background-color: rgba(10, 10, 25, 0.5);
  border-radius: 16px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.3);
  border: 1px solid rgba(0, 170, 255, 0.1);
  transition: all 0.3s ease;
  
  &:hover {
    transform: translateY(-5px);
    box-shadow: 0 15px 40px rgba(0, 0, 0, 0.4), 0 0 20px rgba(0, 170, 255, 0.3);
    border-color: rgba(0, 170, 255, 0.3);
  }
  
  @media (max-width: ${props => props.theme.breakpoints.tablet}) {
    margin-bottom: 20px;
  }
`;

const IconWrapper = styled.div`
  width: 70px;
  height: 70px;
  margin-bottom: 1.5rem;
  color: ${props => props.theme.colors.primary};
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 2.5rem;
  background-color: rgba(0, 170, 255, 0.1);
  border-radius: 50%;
  box-shadow: 0 0 20px rgba(0, 170, 255, 0.3);
  position: relative;
  
  &:before {
    content: '';
    position: absolute;
    width: 100%;
    height: 100%;
    border-radius: 50%;
    background: radial-gradient(circle, rgba(0, 170, 255, 0.3) 0%, rgba(0, 170, 255, 0) 70%);
    animation: pulse 3s infinite alternate;
  }
  
  @keyframes pulse {
    0% { opacity: 0.5; transform: scale(0.9); }
    100% { opacity: 1; transform: scale(1.1); }
  }
`;

const FeatureHeading = styled.h3`
  font-size: 22px;
  font-weight: 600;
  color: ${props => props.theme.colors.text};
  margin-bottom: 1rem;
  text-shadow: 0 0 8px rgba(0, 170, 255, 0.3);
`;

const FeatureText = styled.p`
  font-size: 16px;
  line-height: 1.6;
  color: ${props => props.theme.colors.textSecondary};
  margin: 0;
  word-break: keep-all;
`;

const bulbBlinkAnimation = keyframes`
  0%, 100% { 
    opacity: 0.6; 
    box-shadow: 0 0 8px 2px rgba(255, 223, 186, 0.4); 
  }
  50% { 
    opacity: 1; 
    box-shadow: 0 0 18px 6px rgba(255, 223, 186, 0.95), 0 0 10px 3px rgba(255, 255, 255, 0.7);
  }
`;

const RoundBulbIcon = styled.div`
  width: 38px;
  height: 38px;
  background-color: #FFDFBA; 
  border-radius: 50%;
  position: relative;
  margin-right: 15px;
  animation: ${bulbBlinkAnimation} 2.2s infinite ease-in-out;
  box-shadow: 0 0 10px 3px rgba(255, 223, 186, 0.6); // Slightly stronger base shadow

  &:before {
    content: '';
    position: absolute;
    bottom: -5px;
    left: 50%;
    transform: translateX(-50%);
    width: 16px;
    height: 8px;
    background-color: #E0E0E0;
    border-radius: 3px 3px 0 0;
  }
`;

const SectionTitleContainer = styled.div`
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 60px;
  background-color: rgba(10, 10, 25, 0.5);
  border-radius: 16px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.3);
  border: 1px solid rgba(0, 170, 255, 0.1);
  padding: 32px 48px;
  z-index: 1;
`;

const SectionTitle = styled.h2`
  font-size: 48px;
  text-align: center;
  color: ${props => props.theme.colors.white};
`;

const FeatureStrip: React.FC = () => {
  return (
    <>
      <SectionTitleContainer>
        <RoundBulbIcon style={{ marginRight: 20 }} />
        <SectionTitle>랜턴 주요 기능</SectionTitle>
      </SectionTitleContainer>
      <StripContainer id="features">
        <FeatureItem>
          <IconWrapper>
            <span style={{ color: '#00AAFF' }}>📱</span>
          </IconWrapper>
          <FeatureHeading>오프라인 메시징</FeatureHeading>
          <FeatureText>인터넷 연결 없이도 주변 기기와 메시지를 주고받을 수 있어 어떤 상황에서도 연결을 유지할 수 있습니다.</FeatureText>
        </FeatureItem>
        
        <FeatureItem>
          <IconWrapper>
            <span style={{ color: '#00AAFF' }}>🔄</span>
          </IconWrapper>
          <FeatureHeading>자동 메쉬 네트워크</FeatureHeading>
          <FeatureText>주변 기기 간 자동으로 연결을 구성하여 통신 범위를 확장하고 안정적인 네트워크를 구축합니다.</FeatureText>
        </FeatureItem>
        
        <FeatureItem>
          <IconWrapper>
            <span style={{ color: '#00AAFF' }}>🔒</span>
          </IconWrapper>
          <FeatureHeading>AES 암호화 보안</FeatureHeading>
          <FeatureText>종단간 암호화 기술로 메쉬 네트워크 환경에서도 메시지의 프라이버시와 보안을 보장합니다.</FeatureText>
        </FeatureItem>
      </StripContainer>
    </>
  );
};

export default FeatureStrip;