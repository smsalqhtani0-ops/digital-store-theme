/**
 * Digital Store Theme — Counter Animation
 * Animates numbers from 0 to target when element enters viewport
 */

(function () {
  'use strict';

  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

  function easeOutExpo(t) {
    return t === 1 ? 1 : 1 - Math.pow(2, -10 * t);
  }

  function animateCounter(el) {
    const target   = parseInt(el.dataset.counter, 10);
    const suffix   = el.dataset.suffix || '';
    const duration = parseInt(el.dataset.duration, 10) || 2000;
    let start      = null;

    function step(timestamp) {
      if (!start) start = timestamp;
      const elapsed  = timestamp - start;
      const progress = Math.min(elapsed / duration, 1);
      const value    = Math.floor(easeOutExpo(progress) * target);

      el.textContent = value.toLocaleString('ar-SA') + suffix;

      if (progress < 1) {
        requestAnimationFrame(step);
      } else {
        el.textContent = target.toLocaleString('ar-SA') + suffix;
      }
    }

    requestAnimationFrame(step);
  }

  function initCounters() {
    const counters = document.querySelectorAll('[data-counter]');
    if (!counters.length) return;

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach(entry => {
          if (entry.isIntersecting) {
            animateCounter(entry.target);
            observer.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.3 }
    );

    counters.forEach(el => observer.observe(el));
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initCounters);
  } else {
    initCounters();
  }
})();
