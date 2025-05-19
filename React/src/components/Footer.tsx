import React from 'react';
import styled from 'styled-components';

const FooterContainer = styled.footer`
  display: flex;
  flex-direction: column;
  padding: 20px 0 10px;
  font-size: 12px;
  color: ${props => props.theme.colors.textSecondary};
  border-top: 1px solid #d2d2d7;
  margin-top: 40px;
  background-color: #f5f5f7;
`;

const FooterSection = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 10px 0;
  width: 100%;
  max-width: 980px;
  margin: 0 auto;
  flex-wrap: wrap;
`;

const FooterLinks = styled.div`
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 20px;
  margin-bottom: 20px;
  width: 100%;
`;

const FooterLink = styled.a`
  color: ${props => props.theme.colors.textSecondary};
  text-decoration: none;
  font-size: 12px;
  
  &:hover {
    text-decoration: underline;
  }
`;

const ChecksumText = styled.span`
  margin-right: 1rem;
  font-family: monospace;
  font-size: 11px;
`;

const VerifyLink = styled.a`
  color: ${props => props.theme.colors.primary};
  text-decoration: none;
  
  &:hover {
    text-decoration: underline;
  }
`;

const Copyright = styled.p`
  text-align: center;
  font-size: 12px;
  color: #86868b;
  width: 100%;
  margin-top: 10px;
`;

const Footer: React.FC = () => {
  const currentYear = new Date().getFullYear();
  
  return (
    <FooterContainer>
      <FooterLinks>
        <FooterLink href="#privacy">Privacy Policy</FooterLink>
        <FooterLink href="#terms">Terms of Use</FooterLink>
        <FooterLink href="#support">Support</FooterLink>
        <FooterLink href="#about">About</FooterLink>
      </FooterLinks>
      
      <FooterSection>
        <ChecksumText>SHA-256: e7c8f0a7b0d1c4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0</ChecksumText>
        <VerifyLink href="#">Verify signature</VerifyLink>
      </FooterSection>
      
      <Copyright>Copyright {currentYear} Lantern App. All rights reserved.</Copyright>
    </FooterContainer>
  );
};

export default Footer;