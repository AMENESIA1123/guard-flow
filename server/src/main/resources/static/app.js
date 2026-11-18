const ORDER = ['fixed_window', 'sliding_window', 'token_bucket', 'leaky_bucket'];
const REASONS = ['WINDOW_LIMIT', 'QUEUE_FULL', 'NO_TOKEN', 'PASSED'];
const $ = (id) => document.getElementById(id);
const state = {
  algorithms: [], algorithm: null, speed: 10, status: 'idle', id: null, stream: null,
  busy: false, needsNew: true, totals: { offered: 0, passed: 0, rejected: 0 },
  reasons: { WINDOW_LIMIT: 0, QUEUE_FULL: 0, NO_TOKEN: 0, PASSED: 0 }, samples: [], level: 0,
};
async function api(path, options = {}) {
  const response = await fetch(path, { ...options, headers: { 'Content-Type': 'application/json', ...(options.headers || {}) } });
  const text = await response.text();
  const body = text ? JSON.parse(text) : null;
  if (!response.ok) throw new Error(body?.error?.message || `HTTP ${response.status}`);
  return body;
}
function toast(message) { const el = $('toast'); el.textContent = message; el.classList.add('show'); clearTimeout(toast.timer); toast.timer = setTimeout(() => el.classList.remove('show'), 3000); }
function renderAlgorithms() {
  $('algorithm-list').innerHTML = state.algorithms.map((item) => `<button type="button" class="algorithm-card${item.id === state.algorithm ? ' selected' : ''}" data-id="${item.id}" role="radio" aria-checked="${item.id === state.algorithm}"><span class="algorithm-name">${item.displayName}</span><span class="algorithm-summary">${item.summary}</span></button>`).join('');
  $('algorithm-list').querySelectorAll('.algorithm-card').forEach((card) => card.addEventListener('click', () => {
    if (state.algorithm === card.dataset.id) return;
    state.algorithm = card.dataset.id;
    state.needsNew = true;
    renderAlgorithms();
    renderParams();
    syncButtons();
  }));
}
function readParams() {
  const values = {};
  let valid = true;
  $('param-form').querySelectorAll('.field').forEach((field) => {
    const input = field.querySelector('input');
    const value = Number(input.value), min = Number(input.min), max = Number(input.max);
    const isInt = input.dataset.type === 'int';
    const ok = input.value !== '' && Number.isFinite(value) && value >= min && value <= max && (!isInt || Number.isInteger(value));
    field.querySelector('.field-error').textContent = ok ? '' : `需在 ${min} ~ ${max} 之间${isInt ? '（整数）' : ''}`;
    input.classList.toggle('invalid', !ok);
    if (ok) values[field.dataset.key] = value; else valid = false;
  });
  return { valid, values };
}
function renderParams() {
  const meta = state.algorithms.find((item) => item.id === state.algorithm);
  $('param-form').innerHTML = (meta?.params || []).map((param) => `<div class="field" data-key="${param.key}"><div class="field-label"><span>${param.label}</span><span class="unit">${param.unit || ''}</span></div><input type="number" min="${param.min}" max="${param.max}" step="${param.step}" value="${param.default}" data-type="${param.type}"><div class="field-error"></div></div>`).join('');
  $('param-form').querySelectorAll('input').forEach((input) => input.addEventListener('input', () => { readParams(); syncButtons(); state.needsNew = true; }));
  readParams();
}
const MODE_FIELDS = {
  constant: `<div class="field"><div class="field-label"><span>QPS</span><span class="unit">请求/秒</span></div><div class="range-row"><input id="mode-qps" type="range" min="1" max="200" value="20"><output id="mode-qps-value">20</output></div></div>`,
  step: `<div class="field"><div class="field-label"><span>阶跃点</span><span class="unit">ms:QPS</span></div><input id="mode-step" type="text" placeholder="1000:10,3000:50"></div>`,
  spike: `<div class="field"><div class="field-label"><span>基础 QPS</span></div><input id="mode-qps" type="number" min="1" max="500" value="20"></div><div class="field"><div class="field-label"><span>尖峰大小</span></div><input id="mode-burst" type="number" min="1" max="500" value="50"></div><div class="field"><div class="field-label"><span>周期 ms</span></div><input id="mode-every" type="number" min="100" step="100" value="1000"></div>`,
  poisson: `<div class="field"><div class="field-label"><span>QPS</span></div><input id="mode-qps" type="number" min="1" max="500" value="20"></div><div class="field"><div class="field-label"><span>seed</span></div><input id="mode-seed" type="number" step="1" value="42"></div>`,
};
function renderModeFields() {
  const container = $('mode-fields');
  container.innerHTML = MODE_FIELDS[$('mode-select').value] + '<div class="field-error mode-error"></div>';
  container.querySelectorAll('input').forEach((input) => input.addEventListener('input', () => {
    if (input.id === 'mode-qps' && $('mode-qps-value')) $('mode-qps-value').textContent = input.value;
    syncButtons();
    state.needsNew = true;
  }));
  syncButtons();
}
function parseSegments(text) {
  const segments = text.split(',').map((part) => part.trim()).filter(Boolean).map((part) => {
    const [atMillis, qps] = part.split(':').map(Number);
    if (!Number.isFinite(atMillis) || !Number.isFinite(qps)) throw new Error('格式应为 毫秒:QPS，用逗号分隔');
    return { atMillis, qps };
  });
  if (!segments.length) throw new Error('至少需要一个阶跃点');
  segments.sort((a, b) => a.atMillis - b.atMillis);
  segments.forEach((segment, index) => {
    if (segment.atMillis < 0 || segment.qps < 1 || segment.qps > 500) throw new Error('阶跃点需满足 atMillis >= 0、QPS 1~500');
    if (index && segment.atMillis === segments[index - 1].atMillis) throw new Error('阶跃点时间不能重复');
  });
  return segments;
}
function validateInput() {
  const mode = $('mode-select').value;
  const errorEl = $('mode-fields').querySelector('.mode-error');
  if (!errorEl) return { valid: false, input: null };
  const fail = (message) => { errorEl.textContent = message; return { valid: false, input: null }; };
  errorEl.textContent = '';
  if (mode === 'constant') {
    const qps = Number($('mode-qps').value);
    return qps >= 1 && qps <= 200 ? { valid: true, input: { mode, qps } } : fail('QPS 需在 1 ~ 200');
  }
  if (mode === 'step') {
    try { return { valid: true, input: { mode, segments: parseSegments($('mode-step').value) } }; }
    catch (error) { return fail(error.message); }
  }
  if (mode === 'spike') {
    const qps = Number($('mode-qps').value), burstSize = Number($('mode-burst').value), everyMillis = Number($('mode-every').value);
    if (qps < 1 || qps > 500) return fail('QPS 需在 1 ~ 500');
    if (burstSize < 1 || burstSize > 500) return fail('尖峰大小需在 1 ~ 500');
    if (everyMillis < 100) return fail('周期需 >= 100ms');
    return { valid: true, input: { mode, qps, burstSize, everyMillis } };
  }
  const qps = Number($('mode-qps').value), seed = Number($('mode-seed').value);
  if (qps < 1 || qps > 500) return fail('QPS 需在 1 ~ 500');
  if (!Number.isInteger(seed)) return fail('seed 需为整数');
  return { valid: true, input: { mode, qps, seed } };
}
function renderSpeed() {
  $('speed-control').innerHTML = [1, 5, 10, 20, 50].map((speed) => `<button type="button" class="speed-btn${speed === state.speed ? ' selected' : ''}" data-speed="${speed}">${speed}x</button>`).join('');
  $('speed-control').querySelectorAll('button').forEach((button) => button.addEventListener('click', () => {
    state.speed = Number(button.dataset.speed);
    state.needsNew = true;
    renderSpeed();
  }));
}
function render() {
  const last = state.samples[state.samples.length - 1];
  $('kpi-qps').textContent = last ? String(last.offered * 10) : '0';
  $('kpi-offered').textContent = String(state.totals.offered);
  $('kpi-passed').textContent = String(state.totals.passed);
  $('kpi-rejected').textContent = String(state.totals.rejected);
  $('kpi-level').textContent = `${(state.level * 100).toFixed(1)}%`;
  $('sample-preview').textContent = JSON.stringify(state.samples.slice(-3), null, 2);
  $('reason-chips').innerHTML = REASONS.map((reason) => `<span class="chip${state.reasons[reason] ? '' : ' zero'}"><span class="chip-name">${reason}</span> <span class="chip-count">${state.reasons[reason]}</span></span>`).join('');
}
function clearData() {
  state.totals = { offered: 0, passed: 0, rejected: 0 };
  state.reasons = { WINDOW_LIMIT: 0, QUEUE_FULL: 0, NO_TOKEN: 0, PASSED: 0 };
  state.samples = [];
  state.level = 0;
  render();
}
function syncButtons() {
  const valid = readParams().valid && validateInput().valid;
  $('start-btn').disabled = state.busy || state.status === 'running' || !valid;
  $('pause-btn').disabled = state.busy || state.status !== 'running';
  $('reset-btn').disabled = state.busy || state.status === 'idle';
}
function setConnection(connected) {
  $('backend-badge').classList.toggle('disconnected', !connected);
  $('backend-text').textContent = connected ? 'local' : '连接断开';
}
function closeStream() {
  if (state.stream) { state.stream.close(); state.stream = null; }
  setConnection(true);
}
function openStream() {
  if (!state.id || state.stream) return;
  const stream = new EventSource(`/api/experiments/${state.id}/stream`);
  stream.addEventListener('snapshot', (event) => applySnapshot(JSON.parse(event.data)));
  stream.addEventListener('sample', (event) => applySample(JSON.parse(event.data)));
  stream.onopen = () => setConnection(true);
  stream.onerror = () => setConnection(false);
  state.stream = stream;
}
function applySnapshot(view) {
  state.status = view.status;
  state.totals = { ...view.totals };
  state.level = view.level;
  state.samples = (view.samples || []).slice(-3);
  state.reasons = { WINDOW_LIMIT: 0, QUEUE_FULL: 0, NO_TOKEN: 0, PASSED: view.totals.passed };
  (view.samples || []).forEach((sample) => {
    if (sample.rejected > 0 && sample.reason !== 'PASSED' && state.reasons[sample.reason] !== undefined) state.reasons[sample.reason] += sample.rejected;
  });
  render();
  syncButtons();
}
function applySample(sample) {
  if (state.status !== 'running') return;
  state.totals.offered += sample.offered;
  state.totals.passed += sample.passed;
  state.totals.rejected += sample.rejected;
  state.reasons.PASSED += sample.passed;
  if (sample.rejected > 0 && sample.reason !== 'PASSED' && state.reasons[sample.reason] !== undefined) state.reasons[sample.reason] += sample.rejected;
  state.level = sample.level;
  state.samples.push(sample);
  if (state.samples.length > 3) state.samples.shift();
  render();
}
async function start() {
  if (state.busy) return;
  const params = readParams(), input = validateInput();
  if (!params.valid || !input.valid) { toast('请先修正参数'); return; }
  state.busy = true;
  syncButtons();
  try {
    if (state.id && state.needsNew) {
      if (state.status === 'running') await api(`/api/experiments/${state.id}/control`, { method: 'POST', body: JSON.stringify({ action: 'pause' }) }).catch(() => {});
      await api(`/api/experiments/${state.id}`, { method: 'DELETE' }).catch(() => {});
      closeStream();
      state.id = null;
      clearData();
    }
    if (!state.id) {
      const created = await api('/api/experiments', { method: 'POST', body: JSON.stringify({ algorithm: state.algorithm, params: params.values, input: input.input, speed: state.speed }) });
      state.id = created.id;
      clearData();
    }
    await api(`/api/experiments/${state.id}/control`, { method: 'POST', body: JSON.stringify({ action: 'start' }) });
    state.status = 'running';
    state.needsNew = false;
    openStream();
  } catch (error) {
    toast(error.message);
  } finally {
    state.busy = false;
    syncButtons();
  }
}
async function pause() {
  if (state.busy || !state.id || state.status !== 'running') return;
  state.busy = true;
  syncButtons();
  try {
    await api(`/api/experiments/${state.id}/control`, { method: 'POST', body: JSON.stringify({ action: 'pause' }) });
    state.status = 'paused';
  } catch (error) { toast(error.message); }
  finally { state.busy = false; syncButtons(); }
}
async function reset() {
  if (state.busy) return;
  if (!state.id) { clearData(); state.status = 'idle'; syncButtons(); return; }
  state.busy = true;
  syncButtons();
  try {
    await api(`/api/experiments/${state.id}/control`, { method: 'POST', body: JSON.stringify({ action: 'reset' }) });
    state.status = 'idle';
    state.needsNew = false;
    clearData();
  } catch (error) { toast(error.message); }
  finally { state.busy = false; syncButtons(); }
}
function bind() {
  $('start-btn').addEventListener('click', () => void start());
  $('pause-btn').addEventListener('click', () => void pause());
  $('reset-btn').addEventListener('click', () => void reset());
  $('mode-select').addEventListener('change', () => { renderModeFields(); state.needsNew = true; });
}
async function init() {
  bind();
  renderSpeed();
  try {
    state.algorithms = (await api('/api/algorithms')).sort((a, b) => ORDER.indexOf(a.id) - ORDER.indexOf(b.id));
    if (!state.algorithms.length) throw new Error('没有可用算法');
    state.algorithm = state.algorithms[0].id;
    renderAlgorithms();
    renderParams();
    renderModeFields();
    clearData();
    syncButtons();
    setConnection(true);
  } catch (error) { toast(error.message); }
}
init();
