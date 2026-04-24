/**
 * Digital Store Theme — Main JavaScript
 * Salla Twilight | Core interactions & behaviors
 */

(function () {
  'use strict';

  /* =========================================================
     1. Scroll-based header
  ========================================================= */
  function initHeader() {
    const header = document.getElementById('site-header');
    if (!header) return;

    const onScroll = () => {
      header.classList.toggle('scrolled', window.scrollY > 50);
    };

    window.addEventListener('scroll', onScroll, { passive: true });
    onScroll();
  }

  /* =========================================================
     2. Animate-on-scroll (Intersection Observer)
  ========================================================= */
  function initAnimations() {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      document.querySelectorAll('[data-animate]').forEach(el => el.classList.add('animated'));
      return;
    }

    const elements = document.querySelectorAll('[data-animate]');
    if (!elements.length) return;

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach(entry => {
          if (entry.isIntersecting) {
            entry.target.classList.add('animated');
            observer.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.12, rootMargin: '0px 0px -40px 0px' }
    );

    elements.forEach(el => observer.observe(el));
  }

  /* =========================================================
     3. Typing effect for Hero
  ========================================================= */
  function initTypingEffect() {
    const el = document.getElementById('hero-typing-target');
    if (!el) return;

    const phrases = el.dataset.phrases
      ? el.dataset.phrases.split('|')
      : [el.textContent.trim()];

    if (phrases.length <= 1) return;

    let phraseIndex = 0;
    let charIndex   = 0;
    let isDeleting  = false;

    function type() {
      const current = phrases[phraseIndex];

      if (isDeleting) {
        el.textContent = current.slice(0, --charIndex);
      } else {
        el.textContent = current.slice(0, ++charIndex);
      }

      let delay = isDeleting ? 60 : 100;

      if (!isDeleting && charIndex === current.length) {
        delay = 2200;
        isDeleting = true;
      } else if (isDeleting && charIndex === 0) {
        isDeleting  = false;
        phraseIndex = (phraseIndex + 1) % phrases.length;
        delay       = 400;
      }

      setTimeout(type, delay);
    }

    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    setTimeout(type, 1200);
  }

  /* =========================================================
     4. Product image thumbnails (product page)
  ========================================================= */
  function initProductThumbs() {
    const thumbs   = document.querySelectorAll('.product-thumb');
    const mainImg  = document.getElementById('product-main-img');
    if (!thumbs.length || !mainImg) return;

    thumbs.forEach(thumb => {
      thumb.addEventListener('click', () => {
        thumbs.forEach(t => t.classList.remove('active'));
        thumb.classList.add('active');
        mainImg.src = thumb.dataset.img;
      });
    });
  }

  /* =========================================================
     5. Product page tabs
  ========================================================= */
  function initProductTabs() {
    const tabBtns = document.querySelectorAll('.product-tabs-nav .tab-btn');
    if (!tabBtns.length) return;

    tabBtns.forEach(btn => {
      btn.addEventListener('click', () => {
        const target = btn.dataset.tab;

        tabBtns.forEach(b => {
          b.classList.remove('active');
          b.setAttribute('aria-selected', 'false');
        });

        document.querySelectorAll('.tab-panel').forEach(p => {
          p.classList.remove('active');
          p.hidden = true;
        });

        btn.classList.add('active');
        btn.setAttribute('aria-selected', 'true');

        const panel = document.getElementById('tab-' + target);
        if (panel) { panel.classList.add('active'); panel.hidden = false; }
      });
    });
  }

  /* =========================================================
     6. FAQ Accordion
  ========================================================= */
  function initFaq() {
    const questions = document.querySelectorAll('.faq-question');
    questions.forEach(q => {
      q.addEventListener('click', () => {
        const answer   = q.nextElementSibling;
        const expanded = q.getAttribute('aria-expanded') === 'true';

        /* Close others */
        questions.forEach(other => {
          if (other !== q) {
            other.setAttribute('aria-expanded', 'false');
            const ans = other.nextElementSibling;
            if (ans) ans.hidden = true;
          }
        });

        q.setAttribute('aria-expanded', String(!expanded));
        if (answer) answer.hidden = expanded;
      });
    });
  }

  /* =========================================================
     7. Testimonials Carousel
  ========================================================= */
  function initTestimonials() {
    const slider   = document.getElementById('testimonials-slider');
    const prevBtn  = document.getElementById('testimonials-prev');
    const nextBtn  = document.getElementById('testimonials-next');
    const dotsWrap = document.getElementById('testimonials-dots');
    if (!slider) return;

    const cards    = Array.from(slider.children);
    const perPage  = window.innerWidth >= 1024 ? 3 : window.innerWidth >= 640 ? 2 : 1;
    let current    = 0;
    const total    = Math.ceil(cards.length / perPage);
    let autoTimer;

    /* Build dots */
    if (dotsWrap) {
      for (let i = 0; i < total; i++) {
        const dot = document.createElement('button');
        dot.className = 'slider-dot' + (i === 0 ? ' active' : '');
        dot.setAttribute('aria-label', 'انتقل للشريحة ' + (i + 1));
        dot.addEventListener('click', () => goTo(i));
        dotsWrap.appendChild(dot);
      }
    }

    function updateDots() {
      if (!dotsWrap) return;
      dotsWrap.querySelectorAll('.slider-dot').forEach((d, i) => {
        d.classList.toggle('active', i === current);
      });
    }

    function goTo(index) {
      current = (index + total) % total;
      const offset = current * perPage;
      cards.forEach((card, i) => {
        card.style.display = (i >= offset && i < offset + perPage) ? '' : 'none';
      });
      updateDots();
    }

    function next() { goTo(current + 1); }
    function prev() { goTo(current - 1); }

    if (prevBtn) prevBtn.addEventListener('click', () => { clearInterval(autoTimer); prev(); });
    if (nextBtn) nextBtn.addEventListener('click', () => { clearInterval(autoTimer); next(); });

    goTo(0);

    /* Auto-play */
    autoTimer = setInterval(next, 4500);
    slider.addEventListener('mouseenter', () => clearInterval(autoTimer));
    slider.addEventListener('mouseleave', () => { autoTimer = setInterval(next, 4500); });
  }

  /* =========================================================
     8. Collection Page — View Toggle
  ========================================================= */
  function initViewToggle() {
    const btns      = document.querySelectorAll('.view-btn');
    const container = document.getElementById('products-container');
    if (!btns.length || !container) return;

    btns.forEach(btn => {
      btn.addEventListener('click', () => {
        const view = btn.dataset.view;
        btns.forEach(b => { b.classList.remove('active'); b.setAttribute('aria-pressed', 'false'); });
        btn.classList.add('active');
        btn.setAttribute('aria-pressed', 'true');
        container.className = container.className.replace(/(grid|list)-view/g, '');
        container.classList.add(view + '-view');
        localStorage.setItem('ds-view', view);
      });
    });

    const saved = localStorage.getItem('ds-view');
    if (saved) {
      const btn = document.querySelector(`.view-btn[data-view="${saved}"]`);
      if (btn) btn.click();
    }
  }

  /* =========================================================
     9. Collection Page — Sidebar Toggle (mobile)
  ========================================================= */
  function initFilterSidebar() {
    const toggle  = document.getElementById('filter-toggle');
    const sidebar = document.getElementById('filters-sidebar');
    if (!toggle || !sidebar) return;

    toggle.addEventListener('click', () => {
      const open = sidebar.classList.toggle('open');
      toggle.setAttribute('aria-expanded', String(open));

      /* Backdrop */
      let backdrop = document.getElementById('sidebar-backdrop');
      if (open) {
        if (!backdrop) {
          backdrop = document.createElement('div');
          backdrop.id = 'sidebar-backdrop';
          backdrop.style.cssText = 'position:fixed;inset:0;background:rgba(0,0,0,0.6);z-index:calc(var(--z-overlay) - 1);backdrop-filter:blur(4px);';
          backdrop.addEventListener('click', () => toggle.click());
          document.body.appendChild(backdrop);
        }
        backdrop.hidden = false;
      } else if (backdrop) {
        backdrop.hidden = true;
      }
    });
  }

  /* =========================================================
     10. Collection Page — AJAX Filters
  ========================================================= */
  function initAjaxFilters() {
    const applyBtn   = document.getElementById('apply-filters');
    const clearBtn   = document.getElementById('clear-filters');
    const sortSelect = document.getElementById('sort-select');
    if (!applyBtn && !sortSelect) return;

    function showLoading() {
      const overlay = document.getElementById('loading-overlay');
      if (overlay) overlay.hidden = false;
    }

    function hideLoading() {
      const overlay = document.getElementById('loading-overlay');
      if (overlay) overlay.hidden = true;
    }

    function buildUrl() {
      const url    = new URL(window.location.href);
      const sort   = sortSelect ? sortSelect.value : '';
      const priceMin = document.getElementById('price-min');
      const priceMax = document.getElementById('price-max');
      const catRadio = document.querySelector('input[name="category"]:checked');

      if (sort) { const [k, v] = sort.split('-'); url.searchParams.set('sort_by', k); url.searchParams.set('order', v); }
      if (priceMin?.value) url.searchParams.set('price_min', priceMin.value);
      if (priceMax?.value) url.searchParams.set('price_max', priceMax.value);
      if (catRadio?.value && catRadio.value !== 'all') url.searchParams.set('category', catRadio.value);
      return url.toString();
    }

    async function applyFilters() {
      showLoading();
      const newUrl = buildUrl();
      history.pushState({}, '', newUrl);

      try {
        const res  = await fetch(newUrl + (newUrl.includes('?') ? '&' : '?') + 'view=ajax');
        if (!res.ok) throw new Error('Network error');
        const html = await res.text();
        const parser  = new DOMParser();
        const doc     = parser.parseFromString(html, 'text/html');
        const newGrid = doc.getElementById('products-container');
        const container = document.getElementById('products-container');
        if (newGrid && container) {
          container.innerHTML = newGrid.innerHTML;
          const count = container.querySelectorAll('.product-card').length;
          const shown = document.getElementById('products-shown');
          if (shown) shown.textContent = count;
        }
      } catch {
        /* Fallback: reload */
        window.location.href = buildUrl();
      } finally {
        hideLoading();
        /* Re-init animations on new items */
        initAnimations();
      }
    }

    if (applyBtn)   applyBtn.addEventListener('click', applyFilters);
    if (sortSelect) sortSelect.addEventListener('change', applyFilters);
    if (clearBtn)   clearBtn.addEventListener('click', () => { window.location.href = window.location.pathname; });
  }

  /* =========================================================
     11. Homepage Products Tabs
  ========================================================= */
  function initProductsTabs() {
    const tabs = document.querySelectorAll('.products-tabs .tab-btn');
    if (!tabs.length) return;

    tabs.forEach(tab => {
      tab.addEventListener('click', () => {
        tabs.forEach(t => t.classList.remove('active'));
        tab.classList.add('active');
        /* Salla handles filtering via data attributes or URL */
      });
    });
  }

  /* =========================================================
     12. Mobile Menu
  ========================================================= */
  function initMobileMenu() {
    const toggle = document.getElementById('mobile-menu-toggle');
    const menu   = document.getElementById('mobile-menu');
    if (!toggle || !menu) return;

    toggle.addEventListener('click', () => {
      const open = menu.hidden;
      menu.hidden = !open;
      toggle.setAttribute('aria-expanded', String(open));
      document.body.style.overflow = open ? 'hidden' : '';
    });
  }

  /* =========================================================
     13. Newsletter Form
  ========================================================= */
  function initNewsletterForms() {
    const forms = document.querySelectorAll('.newsletter-form');
    forms.forEach(form => {
      form.addEventListener('submit', e => {
        e.preventDefault();
        const input = form.querySelector('input[type="email"]');
        if (!input?.value) return;
        const btn = form.querySelector('button[type="submit"]');
        if (btn) { btn.textContent = 'تم الاشتراك!'; btn.disabled = true; }
        /* Delegate actual submission to Salla's newsletter component */
        document.dispatchEvent(new CustomEvent('digital-store:newsletter', { detail: { email: input.value } }));
      });
    });
  }

  /* Expose for inline handlers */
  window.handleNewsletterSubmit = function (e) {
    e.preventDefault();
    const input = e.target.querySelector('input[type="email"]');
    const btn   = e.target.querySelector('button[type="submit"]');
    if (!input?.value) return;
    if (btn) { btn.textContent = 'تم!'; btn.disabled = true; }
    document.dispatchEvent(new CustomEvent('digital-store:newsletter', { detail: { email: input.value } }));
  };

  /* =========================================================
     14. Lazy image loading fallback
  ========================================================= */
  function initLazyImages() {
    if ('loading' in HTMLImageElement.prototype) return;
    const imgs = document.querySelectorAll('img[loading="lazy"]');
    if (!imgs.length) return;
    const obs = new IntersectionObserver(entries => {
      entries.forEach(e => {
        if (e.isIntersecting) {
          const img = e.target;
          if (img.dataset.src) img.src = img.dataset.src;
          obs.unobserve(img);
        }
      });
    });
    imgs.forEach(img => obs.observe(img));
  }

  /* =========================================================
     15. Smooth anchor scroll
  ========================================================= */
  function initSmoothScroll() {
    document.querySelectorAll('a[href^="#"]').forEach(link => {
      link.addEventListener('click', e => {
        const id = link.getAttribute('href').slice(1);
        const target = document.getElementById(id);
        if (!target) return;
        e.preventDefault();
        target.scrollIntoView({ behavior: 'smooth', block: 'start' });
      });
    });
  }

  /* =========================================================
     Init all
  ========================================================= */
  function init() {
    initHeader();
    initAnimations();
    initTypingEffect();
    initProductThumbs();
    initProductTabs();
    initFaq();
    initTestimonials();
    initViewToggle();
    initFilterSidebar();
    initAjaxFilters();
    initProductsTabs();
    initMobileMenu();
    initNewsletterForms();
    initLazyImages();
    initSmoothScroll();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
