const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
function run(active, candidates) {
 const document = {activeElement: active, querySelectorAll: () => candidates};
 const code = fs.readFileSync(path.join(__dirname, '../app/src/main/assets/focus-input.js'), 'utf8');
 return {result: vm.runInNewContext(code, {document}), document};
}
function element(tagName, type = '') {
 return {tagName, type, disabled: false, readOnly: false, isContentEditable: false,
 getClientRects: () => [{}], getAttribute: () => null,
 focus() { this.focusCount = (this.focusCount || 0) + 1; }};
}
test('keeps active textarea on repeated operations', () => {
 const a = element('TEXTAREA'); const other = element('INPUT', 'text');
 for (let i = 0; i < 10; i++) assert.equal(run(a, [other, a]).result, true);
 assert.equal(a.focusCount, 10); assert.equal(other.focusCount, undefined);
});
test('prefers textarea over header search', () => {
 const search = element('INPUT', 'search'); const chat = element('TEXTAREA');
 assert.equal(run(element('BUTTON'), [search, chat]).result, true);
 assert.equal(chat.focusCount, 1); assert.equal(search.focusCount, undefined);
});
test('does not target password, hidden, disabled or readonly input', () => {
 const password = element('INPUT','password'); const hidden = element('TEXTAREA'); hidden.getClientRects = () => [];
 const disabled = element('TEXTAREA'); disabled.disabled = true;
 const readonly = element('TEXTAREA'); readonly.readOnly = true;
 assert.equal(run(password, [password, hidden, disabled, readonly]).result, false);
});
test('contenteditable and ordinary text input supported', () => {
 const edit = element('DIV'); edit.isContentEditable = true;
 assert.equal(run(null, [edit]).result, true);
 assert.equal(run(null, [element('INPUT','text')]).result, true);
});
