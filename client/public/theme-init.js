try {
  var theme = localStorage.getItem('gyanwire-theme');
  document.documentElement.dataset.theme = theme === 'light' ? 'light' : 'dark';
} catch (e) {
  document.documentElement.dataset.theme = 'dark';
}
