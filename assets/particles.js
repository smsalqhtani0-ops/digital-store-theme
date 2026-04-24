/**
 * Digital Store Theme — Particle Animation
 * Lightweight canvas-based particles for Hero section
 */

(function () {
  'use strict';

  const canvas = document.getElementById('hero-particles');
  if (!canvas) return;

  const ctx = canvas.getContext('2d');
  let particles = [];
  let animationId;
  let W, H;

  const CONFIG = {
    count:      80,
    minRadius:  1,
    maxRadius:  2.5,
    minSpeed:   0.2,
    maxSpeed:   0.6,
    colors:     ['rgba(124,58,237,', 'rgba(59,130,246,', 'rgba(157,111,255,'],
    lineDistance: 130,
    lineOpacity:  0.15,
  };

  function resize() {
    W = canvas.width  = canvas.offsetWidth;
    H = canvas.height = canvas.offsetHeight;
  }

  function random(min, max) {
    return Math.random() * (max - min) + min;
  }

  function createParticle() {
    const color = CONFIG.colors[Math.floor(Math.random() * CONFIG.colors.length)];
    return {
      x:      random(0, W),
      y:      random(0, H),
      vx:     random(-CONFIG.maxSpeed, CONFIG.maxSpeed) || CONFIG.minSpeed,
      vy:     random(-CONFIG.maxSpeed, CONFIG.maxSpeed) || CONFIG.minSpeed,
      r:      random(CONFIG.minRadius, CONFIG.maxRadius),
      alpha:  random(0.3, 0.8),
      color,
    };
  }

  function init() {
    particles = [];
    for (let i = 0; i < CONFIG.count; i++) {
      particles.push(createParticle());
    }
  }

  function drawParticle(p) {
    ctx.beginPath();
    ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2);
    ctx.fillStyle = p.color + p.alpha + ')';
    ctx.fill();
  }

  function drawLine(p1, p2, dist) {
    const opacity = (1 - dist / CONFIG.lineDistance) * CONFIG.lineOpacity;
    ctx.beginPath();
    ctx.moveTo(p1.x, p1.y);
    ctx.lineTo(p2.x, p2.y);
    ctx.strokeStyle = 'rgba(124,58,237,' + opacity + ')';
    ctx.lineWidth = 0.5;
    ctx.stroke();
  }

  function update() {
    particles.forEach(p => {
      p.x += p.vx;
      p.y += p.vy;
      if (p.x < -10) p.x = W + 10;
      if (p.x > W + 10) p.x = -10;
      if (p.y < -10) p.y = H + 10;
      if (p.y > H + 10) p.y = -10;
    });
  }

  function draw() {
    ctx.clearRect(0, 0, W, H);

    for (let i = 0; i < particles.length; i++) {
      for (let j = i + 1; j < particles.length; j++) {
        const dx   = particles[i].x - particles[j].x;
        const dy   = particles[i].y - particles[j].y;
        const dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < CONFIG.lineDistance) {
          drawLine(particles[i], particles[j], dist);
        }
      }
      drawParticle(particles[i]);
    }
  }

  function loop() {
    update();
    draw();
    animationId = requestAnimationFrame(loop);
  }

  function start() {
    resize();
    init();
    loop();
  }

  /* Pause when tab hidden to save resources */
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) {
      cancelAnimationFrame(animationId);
    } else {
      loop();
    }
  });

  /* Debounced resize */
  let resizeTimer;
  window.addEventListener('resize', () => {
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(() => {
      resize();
      init();
    }, 200);
  });

  /* Reduced motion: skip animation */
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    canvas.style.display = 'none';
    return;
  }

  /* Start after DOM ready */
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start);
  } else {
    start();
  }
})();
