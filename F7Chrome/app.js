// vim: ts=2:sw=2:expandtab
chrome.app.runtime.onLaunched.addListener(function() {
  chrome.app.window.create('start.html', {
    'bounds': {
      'width': 800,
      'height': 600
    }
  });
});
