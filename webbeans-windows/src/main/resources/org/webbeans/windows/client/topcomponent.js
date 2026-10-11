/* wb.TopComponent — a panel (same layouts as wb.Panel) whose tab properties are shown by the enclosing wb.Dock. */
wb.register('wb.TopComponent', {
  create: function (ctx) { var el = wb.renderers['wb.Panel'].create(ctx); el.classList.add('wb-tc'); return el; },
  update: function (el, props, changed, ctx) {
    wb.renderers['wb.Panel'].update(el, props, changed, ctx);
    if (changed.has('tcId')) el.dataset.tcId = props.tcId || '';
    var tabProps = ['displayName', 'htmlDisplayName', 'icon', 'closable', 'busy', 'modified', 'tooltip'];
    if (el.dock && tabProps.some(function (p) { return changed.has(p); })) el.dock.refreshTab(el);
  },
  insertChild: function (el, child, i, ctx, node) { wb.renderers['wb.Panel'].insertChild(el, child, i, ctx, node); },
  removeChild: function (el, child, ctx, node) { wb.renderers['wb.Panel'].removeChild(el, child, ctx, node); }
});
