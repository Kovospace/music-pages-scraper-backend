// Remembers a language picked in the switcher, so index.html stops auto-redirecting by browser language.
document.querySelectorAll('.lang a[data-lang]').forEach(function (a) {
  a.addEventListener('click', function () {
    try { localStorage.setItem('lang', a.getAttribute('data-lang')); } catch (e) { /* storage blocked */ }
  });
});
