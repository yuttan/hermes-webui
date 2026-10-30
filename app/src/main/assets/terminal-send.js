// Sends raw bytes to the Hermes dashboard chat terminal (xterm.js running the TUI).
// The dashboard enables xterm screenReaderMode, so synthetic `input` events are ignored;
// its IME composition fallback forwards compositionend data to the PTY instead.
// Returns "sent" or "no-terminal" (caller then uses a native key fallback).
(function (chunks, gapMs) {
  var ta = document.querySelector('.xterm-helper-textarea');
  if (!ta) return 'no-terminal';
  var i = 0;
  function sendNext() {
    var data = chunks[i++];
    ta.dispatchEvent(new CompositionEvent('compositionstart', { data: '', bubbles: true }));
    ta.dispatchEvent(new CompositionEvent('compositionend', { data: data, bubbles: true }));
    if (i < chunks.length) setTimeout(sendNext, gapMs);
  }
  sendNext();
  return 'sent';
})
