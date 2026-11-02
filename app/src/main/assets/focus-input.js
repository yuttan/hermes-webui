(function() {
    function eligible(el) {
        if (!el || el.disabled || el.readOnly || el.getClientRects().length === 0) return false;
        if (el.tagName === 'INPUT') return ['', 'text', 'search'].indexOf((el.type || '').toLowerCase()) !== -1;
        return el.tagName === 'TEXTAREA' || el.isContentEditable;
    }
    var active = document.activeElement;
    if (eligible(active)) {
        active.focus({preventScroll: true});
        return true;
    }
    var all = Array.prototype.slice.call(document.querySelectorAll('textarea, [contenteditable], input'));
    var target = all.find(function(el) { return el.tagName === 'TEXTAREA' && eligible(el); }) ||
        all.find(function(el) { return el.isContentEditable && eligible(el); }) ||
        all.find(function(el) { return el.tagName === 'INPUT' && eligible(el); });
    if (!target) return false;
    target.focus({preventScroll: true});
    return true;
})();
