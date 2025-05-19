import React, { useState, useRef, useEffect } from 'react';
import styled, { keyframes } from 'styled-components';

const HeroContainer = styled.section`
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  padding: 0 8vw;
  position: relative;
  overflow: hidden;
  min-height: 100vh;
  width: 100%;
  z-index: 5;
  
  @media (max-width: ${props => props.theme.breakpoints.tablet}) {
    flex-direction: column;
    padding: 8vh 5vw;
    text-align: center;
  }
`;

const centralGlowAnimation = keyframes`
  0%, 100% {
    transform: translate(-50%, -50%) scale(0.95);
    opacity: 0.7;
  }
  50% {
    transform: translate(-50%, -50%) scale(1);
    opacity: 1;
  }
`;

const CentralGlowOrb = styled.div`
  position: absolute;
  top: 50%;
  left: 50%;
  width: clamp(400px, 60vw, 800px); 
  height: clamp(400px, 60vw, 800px);
  background: radial-gradient(circle, rgba(255, 255, 255, 0.12) 0%, rgba(255, 255, 255, 0) 65%);
  border-radius: 50%;
  z-index: 2; 
  pointer-events: none;
  animation: ${centralGlowAnimation} 8s ease-in-out infinite alternate;
  box-shadow: 0 0 100px 30px rgba(255, 255, 255, 0.08), 0 0 60px 15px rgba(255, 255, 255, 0.1);
`;

const TextContent = styled.div`
  max-width: 550px;
  z-index: 10;
  opacity: 0;
  transform: translateY(20px);
  animation: fadeInUp 1s forwards 0.5s;
  text-align: left;
  
  @keyframes fadeInUp {
    to {
      opacity: 1;
      transform: translateY(0);
    }
  }
  position: relative;
  
  @media (max-width: ${props => props.theme.breakpoints.tablet}) {
    margin-bottom: 60px;
    text-align: center;
    max-width: 100%;
  }
`;

const Headline = styled.h1`
  font-size: clamp(3.5rem, 8vw, 7rem);
  font-weight: 800;
  color: ${props => props.theme.colors.white};
  letter-spacing: -2px;
  margin-bottom: 1rem;
  line-height: 1.1;
  text-align: center;
  position: relative;
  text-shadow: none; 

  span {
    display: block;
    font-size: 40%;
    font-weight: 500;
    margin-top: 0.5rem;
    opacity: 1;
    text-transform: none;
    color: ${props => props.theme.colors.textSecondary};
  }
  
  &:after {
    content: '';
    position: absolute;
    bottom: -20px;
    left: 50%;
    transform: translateX(-50%);
    width: 150%;
    height: 100px;
    background: radial-gradient(ellipse at center, ${props => props.theme.colors.glow} 0%, transparent 70%);
    opacity: 0.15;
    filter: blur(20px);
    z-index: -1;
    pointer-events: none;
  }
  
  @media (max-width: ${props => props.theme.breakpoints.tablet}) {
    &:after {
      margin: 1.5rem auto 0;
    }
  }
`;

const SubHeadline = styled.p`
  font-size: clamp(18px, 1.8vw, 22px);
  margin-bottom: 40px;
  color: ${props => props.theme.colors.text};
  font-weight: 500;
  line-height: 1.5;
  max-width: 550px;
  letter-spacing: 0.2px;
  text-shadow: none;
  
  @media (max-width: ${props => props.theme.breakpoints.tablet}) {
    margin-left: auto;
    margin-right: auto;
  }
`;

interface PrimaryButtonProps {
  emphasized?: boolean;
}

const PrimaryButton = styled.button<PrimaryButtonProps>`
  background-color: ${props => 
    props.emphasized 
      ? props.theme.colors.primary 
      : 'rgba(60, 70, 85, 0.6)'}; 
  color: ${props => props.theme.colors.white}; 
  font-size: 16px;
  font-weight: 600; 
  padding: 14px 28px;
  min-width: 160px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  border: 1px solid ${props => 
    props.emphasized 
      ? props.theme.colors.primary 
      : 'rgba(100, 110, 125, 0.8)'}; 
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.25); 
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer; 
  
  svg {
    width: 18px;
    height: 18px;
    fill: ${props => props.theme.colors.white}; 
  }
  
  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: -100%;
    width: 100%;
    height: 100%;
    background: linear-gradient(
      90deg, 
      transparent, 
      rgba(255, 255, 255, 0.15), 
      transparent
    );
    transition: 0.6s;
  }
  
  &:hover {
    transform: translateY(-2px);
    background-color: ${props => 
      props.emphasized 
        ? props.theme.colors.primary 
        : 'rgba(75, 85, 100, 0.75)'}; 
    border-color: ${props => 
      props.emphasized 
        ? props.theme.colors.primary 
        : 'rgba(120, 130, 145, 0.9)'}; 
    filter: ${props => props.emphasized ? 'brightness(1.1)' : 'none'};
    box-shadow: 0 8px 20px ${props => props.emphasized ? 'rgba(0, 170, 255, 0.4)' : 'rgba(200, 220, 255, 0.2)'}; 
    svg {
      fill: ${props => props.theme.colors.white};
    }
    &::before {
      left: 100%;
    }
  }
  
  &:active {
    transform: translateY(1px);
    background-color: ${props => 
      props.emphasized 
        ? props.theme.colors.primary 
        : 'rgba(50, 60, 75, 0.5)'}; 
    filter: ${props => props.emphasized ? 'brightness(0.9)' : 'none'};
    box-shadow: 0 4px 15px ${props => props.emphasized ? 'rgba(0, 170, 255, 0.3)' : 'rgba(180, 200, 235, 0.15)'}; 
  }
`;

const textBlinkAnimation = keyframes`
  0%, 100% {
    color: rgba(255, 255, 255, 0.7);
    text-shadow: 0 0 5px rgba(255, 255, 255, 0.5);
    opacity: 0.7;
  }
  50% {
    color: rgba(255, 255, 255, 1);
    text-shadow: 0 0 10px rgba(255, 255, 255, 0.8);
    opacity: 1;
  }
`;

const BleIconSvg = styled.svg`
  width: 60px; 
  height: 60px; 
  fill: rgba(255, 255, 255, 0.8);
  position: relative;
  z-index: 1;
`;

const bleGlowAnimation = keyframes`
  0%, 100% {
    box-shadow: 0 0 20px 5px rgba(255, 255, 255, 0.3), 0 0 30px 10px rgba(255, 255, 255, 0.2);
    transform: scale(1);
    opacity: 0.7;
  }
  50% {
    box-shadow: 0 0 30px 10px rgba(255, 255, 255, 0.5), 0 0 45px 15px rgba(255, 255, 255, 0.3);
    transform: scale(1.05);
    opacity: 1;
  }
`;

const BleIconContainer = styled.div`
  width: 100px; 
  height: 100px;
  border-radius: 50%;
  display: flex;
  justify-content: center;
  align-items: center;
  animation: ${bleGlowAnimation} 3s ease-in-out infinite alternate;
`;

const MockupAnimationContainer = styled.div`
  width: 100%;
  height: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: rgba(20, 30, 45, 0.85);
  border-radius: inherit;
  overflow: hidden;
  position: relative;
  pointer-events: none;
  &:after {
    content: '';
    position: absolute;
    width: 150px;
    height: 150px;
    background: radial-gradient(circle, rgba(255, 165, 0, 0.15) 0%, transparent 70%);
    filter: blur(15px);
    pointer-events: none;
  }
`;

const StatusTextContainer = styled.div`
  position: absolute;
  top: 50%; 
  left: 50%;
  transform: translate(-50%, -50%); 
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center; 
  z-index: 11; 
  width: 80%; 
  text-align: center; 
`;

const BlinkingStatusText = styled.div`
  font-size: 24px; 
  font-weight: 600;
  color: ${props => props.theme.colors.white};
  text-align: center;
  position: relative; 
  z-index: 1; 
  animation: ${textBlinkAnimation} 2.5s ease-in-out infinite alternate; 
  text-shadow: 0 0 8px rgba(255, 255, 255, 0.5); 
  margin-top: 15px; 
`;

const StarfieldCanvas = styled.canvas`
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 0;
  border-radius: inherit;
`;

const ButtonContainer = styled.div`
  display: flex;
  gap: 1rem;
  margin-top: 40px;

  @media (max-width: ${props => props.theme.breakpoints.tablet}) {
    justify-content: center;
    margin-top: 30px;
  }
`;

const DeviceFrame = styled.div`
  position: absolute;
  width: 380px;
  height: 780px;
  border-radius: 40px;
  background: linear-gradient(145deg, rgba(40, 40, 45, 0.7), rgba(20, 20, 25, 0.9));
  box-shadow: 
    0 50px 100px -20px rgba(0, 0, 0, 0.3),
    0 30px 60px -30px rgba(0, 0, 0, 0.5),
    inset 0 -2px 6px 0 rgba(255, 255, 255, 0.2);
  display: flex;
  justify-content: center;
  align-items: center;
  overflow: hidden;
  z-index: 5;
  transform: perspective(1000px) rotateX(10deg);

  &:before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background: linear-gradient(145deg, rgba(255, 255, 255, 0.05) 0%, rgba(255, 255, 255, 0) 80%);
    border-radius: inherit;
    opacity: 0.4;
  }
`;

const DeviceScreen = styled.div`
  position: relative;
  width: 94%;
  height: 98%;
  overflow: hidden;
  border-radius: 34px;
  background-color: #000;
  z-index: 2;
`;

const ScreenReflection = styled.div`
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 20;
  pointer-events: none;
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.02) 0%, rgba(255, 255, 255, 0) 80%);
  border-radius: 34px;
  
  &:after {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background: radial-gradient(circle at 70% 20%, rgba(255,255,255,0.05) 0%, rgba(255,255,255,0) 60%);
  }
`;

const ScreensCarousel = styled.div`
  position: relative;
  width: 45%;
  max-width: 600px;
  height: 700px;
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 8;
  opacity: 0;
  transform: translateY(30px);
  animation: fadeInUp 1.2s forwards 0.8s;
  
  @media (max-width: ${props => props.theme.breakpoints.tablet}) {
    width: 100%;
    margin-top: 40px;
  }
  
  &:before {
    content: '';
    position: absolute;
    width: 100%;
    height: 100%;
    border-radius: 30px;
    background: radial-gradient(circle, rgba(0, 170, 255, 0.3) 0%, rgba(0, 170, 255, 0) 70%);
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    filter: blur(50px);
    animation: lantern-glow 6s ease-in-out infinite alternate;
    z-index: -1;
  }
  
  @keyframes lantern-glow {
    0% { opacity: 0.5; width: 280px; height: 280px; filter: blur(40px); }
    100% { opacity: 0.8; width: 320px; height: 320px; filter: blur(60px); }
  }
  
  @media (max-width: ${props => props.theme.breakpoints.mobile}) {
    height: 400px;
    max-width: 90%;
  }
`;

const BackgroundGradient = styled.div`
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 150%;
  height: 100%;
  background: radial-gradient(ellipse at center, rgba(0, 170, 255, 0.05) 0%, transparent 70%);
  z-index: 3;
  pointer-events: none;
`;

const LanternEffect = styled.div`
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 800px;
  height: 800px;
  background: radial-gradient(
    circle,
    rgba(0, 170, 255, 0.4) 0%,
    rgba(0, 170, 255, 0.2) 20%,
    rgba(0, 170, 255, 0.05) 40%,
    rgba(0, 170, 255, 0.01) 60%,
    transparent 80%
  );
  z-index: 1;
  filter: blur(1px);
  opacity: 0.05;
  pointer-events: none;
`;

const Hero: React.FC = () => {
  const heroRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let animationFrameId: number;
    const stars: { x: number; y: number; radius: number; alpha: number; dAlpha: number }[] = [];
    const numStars = 100;

    for (let i = 0; i < numStars; i++) {
      stars.push({
        x: Math.random() * canvas.width,
        y: Math.random() * canvas.height,
        radius: Math.random() * 1.5 + 0.5,
        alpha: Math.random() * 0.5 + 0.5,
        dAlpha: Math.random() * 0.01 - 0.005
      });
    }

    const drawStars = () => {
      if (!ctx || !canvas) return;
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      ctx.fillStyle = 'rgba(255, 255, 255, 0.8)';

      stars.forEach(star => {
        ctx.beginPath();
        ctx.arc(star.x, star.y, star.radius, 0, Math.PI * 2);
        ctx.globalAlpha = star.alpha;
        ctx.fill();

        star.alpha += star.dAlpha;
        if (star.alpha <= 0.3 || star.alpha >= 1) {
          star.dAlpha *= -1;
        }
        star.y -= 0.1;
        if (star.y < 0) {
          star.y = canvas.height;
          star.x = Math.random() * canvas.width;
        }
      });
      ctx.globalAlpha = 1;
      animationFrameId = requestAnimationFrame(drawStars);
    };

    const resizeCanvas = () => {
      if (canvas && canvas.parentElement) {
        canvas.width = canvas.parentElement.offsetWidth;
        canvas.height = canvas.parentElement.offsetHeight;
        stars.length = 0;
        for (let i = 0; i < numStars; i++) {
          stars.push({
            x: Math.random() * canvas.width,
            y: Math.random() * canvas.height,
            radius: Math.random() * 1.5 + 0.5,
            alpha: Math.random() * 0.5 + 0.5,
            dAlpha: Math.random() * 0.01 - 0.005
          });
        }
      }
    };

    resizeCanvas();
    drawStars();

    window.addEventListener('resize', resizeCanvas);

    return () => {
      cancelAnimationFrame(animationFrameId);
      window.removeEventListener('resize', resizeCanvas);
    };
  }, []);

  return (
    <HeroContainer ref={heroRef}>
      <BackgroundGradient />
      <LanternEffect />
      <CentralGlowOrb /> 
      <TextContent>
        <Headline>
          LANTERN
          <span>어둠 속의 빛</span>
        </Headline>
        <SubHeadline>
          재난 상황에서도 끊김 없는 통신을 위한 혁신적인 메신저. 블루투스 메쉬 네트워킹으로 서로 연결되어 일반 통신망이 불가능한 상황에서도 소통이 가능합니다.
        </SubHeadline>
        <ButtonContainer>
          <PrimaryButton emphasized onClick={() => window.open('/path-to-your-apk/app.apk', '_blank')}>
            Download apk
          </PrimaryButton>
          <PrimaryButton onClick={() => console.log('Learn More Button Clicked!')}>
            Learn More
          </PrimaryButton>
        </ButtonContainer>
      </TextContent>
      <ScreensCarousel>
        <DeviceFrame>
          <DeviceScreen>
            <MockupAnimationContainer>
              <StarfieldCanvas ref={canvasRef} />
              <StatusTextContainer>
                <BleIconContainer> 
                  <BleIconSvg viewBox="0 0 24 24">
                    <path d="M6.965 17.035a.75.75 0 001.06-1.06L4.061 12l3.964-3.975a.75.75 0 10-1.06-1.06L2.47 11.47a.75.75 0 000 1.06l4.495 4.505zM17.035 6.965a.75.75 0 00-1.06 1.06L19.939 12l-3.964 3.975a.75.75 0 101.06 1.06L21.53 12.53a.75.75 0 000-1.06L17.035 6.965zM8.75 11.25h6.5a.75.75 0 000-1.5h-6.5a.75.75 0 000 1.5z" />
                  </BleIconSvg>
                </BleIconContainer>
                <BlinkingStatusText>Connecting...</BlinkingStatusText>
              </StatusTextContainer>
            </MockupAnimationContainer>
            <ScreenReflection />
          </DeviceScreen>
        </DeviceFrame>
      </ScreensCarousel>
    </HeroContainer>
  );
};

export default Hero;