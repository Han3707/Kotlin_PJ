import React from 'react';
import styled from 'styled-components';

const HeaderContainer = styled.header`
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 16px 22px;
  width: 100%;
  position: sticky;
  top: 0;
  z-index: 100;
  background-color: rgba(15, 15, 26, 0.8);
  backdrop-filter: saturate(180%) blur(20px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  height: 44px;
`;

const Logo = styled.h1`
  font-weight: 500;
  font-size: 21px;
  letter-spacing: -0.01em;
  color: ${props => props.theme.colors.white};
  position: relative;
  
  &::after {
    content: '';
    position: absolute;
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background-color: ${props => props.theme.colors.primary};
    right: -12px;
    top: 50%;
    transform: translateY(-50%);
    box-shadow: 0 0 10px ${props => props.theme.colors.glow}, 0 0 20px ${props => props.theme.colors.glow};
  }
`;

const NavItem = styled.a`
  font-size: 12px;
  color: ${props => props.theme.colors.textSecondary};
  margin: 0 12px;
  font-weight: 400;
  transition: all 0.3s ease;
  position: relative;
  
  &:hover {
    color: ${props => props.theme.colors.primary};
    text-shadow: 0 0 8px ${props => props.theme.colors.glow};
  }
  
  &::after {
    content: '';
    position: absolute;
    bottom: -6px;
    left: 0;
    width: 0;
    height: 1px;
    background-color: ${props => props.theme.colors.primary};
    transition: width 0.3s ease;
    box-shadow: 0 0 5px ${props => props.theme.colors.glow};
  }
  
  &:hover::after {
    width: 100%;
  }
`;

const DownloadButton = styled.button`
  background: transparent;
  color: ${props => props.theme.colors.white};
  padding: 4px 11px;
  font-size: 12px;
  font-weight: 400;
  margin-left: 12px;
  border: 1px solid ${props => props.theme.colors.primary};
  box-shadow: 0 0 10px ${props => props.theme.colors.glow};
  text-shadow: 0 0 8px ${props => props.theme.colors.glow};
  transition: all 0.3s ease;
  
  &:hover {
    background-color: ${props => props.theme.colors.primary};
    box-shadow: 0 0 15px ${props => props.theme.colors.glow}, 0 0 30px ${props => props.theme.colors.glow};
  }
`;

const Header: React.FC = () => {
  return (
    <HeaderContainer>
      <Logo>Link</Logo>
      <div style={{ flex: 1 }}></div>
      <NavItem href="#features">Features</NavItem>
      <NavItem href="#privacy">Privacy</NavItem>
      <NavItem href="#support">Support</NavItem>
      <DownloadButton>Download</DownloadButton>
    </HeaderContainer>
  );
};

export default Header;