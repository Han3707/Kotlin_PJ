import React, { useEffect, useRef } from 'react';
import styled from 'styled-components';

const BackgroundCanvas = styled.canvas`
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 0;
  opacity: 0.5;
  pointer-events: none;
`;

const BackgroundOverlay = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: linear-gradient(to bottom, rgba(15, 15, 26, 0.4) 0%, rgba(15, 15, 26, 0.7) 100%);
  z-index: 1;
  pointer-events: none;
`;

interface Star {
  x: number;
  y: number;
  radius: number;
  opacity: number;
  pulse: number;
  pulseSpeed: number;
}

const MeshBackground: React.FC = () => {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    
    const resizeCanvas = () => {
      canvas.width = window.innerWidth;
      canvas.height = window.innerHeight;
    };
    
    window.addEventListener('resize', resizeCanvas);
    resizeCanvas();
    
    // Create stars
    const starCount = Math.min(Math.floor(window.innerWidth / 3), 300);
    const stars: Star[] = [];
    
    for (let i = 0; i < starCount; i++) {
      stars.push({
        x: Math.random() * canvas.width,
        y: Math.random() * canvas.height,
        radius: Math.random() * 1.5,
        opacity: Math.random() * 0.6 + 0.2,
        pulse: Math.random() * Math.PI,
        pulseSpeed: (Math.random() * 0.005) + 0.001
      });
    }
    
    // Create a bright center point (lantern)
    const centerX = canvas.width / 2;
    const centerY = canvas.height / 2;
    
    // Animation loop
    const animate = () => {
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      
      // Draw stars
      for (let i = 0; i < stars.length; i++) {
        const star = stars[i];
        
        // Update pulse
        star.pulse += star.pulseSpeed;
        if (star.pulse > Math.PI * 2) star.pulse = 0;
        
        // Calculate pulsing opacity
        const pulsingOpacity = star.opacity * (0.6 + 0.4 * Math.sin(star.pulse));
        
        // Draw star
        ctx.beginPath();
        ctx.arc(star.x, star.y, star.radius, 0, Math.PI * 2);
        ctx.fillStyle = `rgba(255, 255, 255, ${pulsingOpacity})`;
        ctx.fill();
        
        // Add glow effect
        ctx.beginPath();
        ctx.arc(star.x, star.y, star.radius * 3, 0, Math.PI * 2);
        const gradient = ctx.createRadialGradient(
          star.x, star.y, star.radius,
          star.x, star.y, star.radius * 3
        );
        gradient.addColorStop(0, `rgba(0, 170, 255, ${pulsingOpacity * 0.6})`);
        gradient.addColorStop(1, 'rgba(0, 170, 255, 0)');
        ctx.fillStyle = gradient;
        ctx.fill();
      }
      
      // Draw lantern glow effect
      const glowRadius = 150 + Math.sin(Date.now() / 1000) * 20;
      const gradient = ctx.createRadialGradient(
        centerX, centerY, 0,
        centerX, centerY, glowRadius
      );
      gradient.addColorStop(0, 'rgba(0, 170, 255, 0.5)');
      gradient.addColorStop(0.6, 'rgba(0, 170, 255, 0.1)');
      gradient.addColorStop(1, 'rgba(0, 170, 255, 0)');
      
      ctx.beginPath();
      ctx.arc(centerX, centerY, glowRadius, 0, Math.PI * 2);
      ctx.fillStyle = gradient;
      ctx.fill();
      
      requestAnimationFrame(animate);
    };
    
    animate();
    
    return () => {
      window.removeEventListener('resize', resizeCanvas);
    };
  }, []);
  
  return (
    <>
      <BackgroundOverlay />
      <BackgroundCanvas ref={canvasRef} />
    </>
  );
};

export default MeshBackground;