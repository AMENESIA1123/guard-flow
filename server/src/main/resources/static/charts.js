const COLORS = {
  muted: '#94a3b8',
  border: '#1f2937',
  passed: '#38bdf8',
  rejected: '#f472b6',
  level: '#22c55e',
};
const FONT = 'system-ui, -apple-system, "Segoe UI", "Microsoft YaHei", sans-serif';
const AXIS_FONT = `11px ${FONT}`;
const LEGEND_FONT = `12px ${FONT}`;
const VALUE_FONT = `14px ${FONT}`;
const EMPTY_FONT = `13px ${FONT}`;
const PAD = { left: 48, right: 16, top: 12, bottom: 24 };
const SLOTS = 300;
const LEVEL_LABELS = {
  fixed_window: '窗口占用',
  sliding_window: '窗口占用',
  token_bucket: '令牌余量',
  leaky_bucket: '队列水位',
};
const patternCache = new WeakMap();

function fit(ctx, width, height) {
  const dpr = window.devicePixelRatio || 1;
  const pixelWidth = Math.max(1, Math.round(width * dpr));
  const pixelHeight = Math.max(1, Math.round(height * dpr));
  if (ctx.canvas.width !== pixelWidth || ctx.canvas.height !== pixelHeight) {
    ctx.canvas.width = pixelWidth;
    ctx.canvas.height = pixelHeight;
  }
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, width, height);
  ctx.textBaseline = 'middle';
}

function drawXLabels(ctx, width, height) {
  const plotWidth = width - PAD.left - PAD.right;
  ctx.font = AXIS_FONT;
  ctx.fillStyle = COLORS.muted;
  for (let i = 1; i <= 6; i++) {
    const x = PAD.left + (plotWidth * i) / 6;
    ctx.textAlign = i === 6 ? 'right' : 'center';
    ctx.fillText(`${(i - 6) * 5}s`, x, height - PAD.bottom + 12);
  }
}

function drawEmpty(ctx, width, height) {
  ctx.font = EMPTY_FONT;
  ctx.fillStyle = COLORS.muted;
  ctx.textAlign = 'center';
  ctx.fillText('点击开始实验', width / 2, height / 2);
}

function rejectedPattern(ctx) {
  const cached = patternCache.get(ctx.canvas);
  if (cached) return cached;
  const tile = document.createElement('canvas');
  tile.width = 8;
  tile.height = 8;
  const paint = tile.getContext('2d');
  paint.fillStyle = 'rgba(244,114,182,0.35)';
  paint.fillRect(0, 0, 8, 8);
  paint.strokeStyle = COLORS.rejected;
  paint.lineWidth = 1;
  paint.beginPath();
  paint.moveTo(-2, 10);
  paint.lineTo(10, -2);
  paint.stroke();
  paint.beginPath();
  paint.moveTo(2, 10);
  paint.lineTo(10, 2);
  paint.stroke();
  const pattern = ctx.createPattern(tile, 'repeat');
  patternCache.set(ctx.canvas, pattern);
  return pattern;
}

function ceilingToFive(value) {
  return Math.max(5, Math.ceil(value / 5) * 5);
}

export function drawDecisionChart(ctx, samples, opts) {
  const width = opts.width;
  const height = opts.height;
  fit(ctx, width, height);
  const plotWidth = width - PAD.left - PAD.right;
  const plotHeight = height - PAD.top - PAD.bottom;
  const observedMax = samples.reduce((max, sample) => Math.max(max, sample.offered), 0);
  const yMax = ceilingToFive(Math.max(observedMax, (opts.qps || 0) * 0.12));

  ctx.font = AXIS_FONT;
  ctx.fillStyle = COLORS.muted;
  ctx.strokeStyle = COLORS.border;
  ctx.lineWidth = 1;
  for (let i = 0; i < 4; i++) {
    const y = PAD.top + plotHeight - (plotHeight * i) / 3;
    ctx.beginPath();
    ctx.moveTo(PAD.left, y);
    ctx.lineTo(width - PAD.right, y);
    ctx.stroke();
    ctx.textAlign = 'right';
    ctx.fillText(String(Math.round((yMax * i) / 3)), PAD.left - 8, y);
  }
  ctx.textAlign = 'left';
  ctx.fillText('请求数/100ms', PAD.left + 4, 10);
  drawXLabels(ctx, width, height);

  const slot = plotWidth / SLOTS;
  const barWidth = Math.max(1, slot - 1);
  const offset = SLOTS - samples.length;
  const base = PAD.top + plotHeight;
  const pattern = rejectedPattern(ctx);
  for (let i = 0; i < samples.length; i++) {
    const sample = samples[i];
    const x = PAD.left + (offset + i) * slot;
    const passedHeight = (plotHeight * sample.passed) / yMax;
    const rejectedHeight = (plotHeight * sample.rejected) / yMax;
    if (sample.passed > 0) {
      ctx.fillStyle = COLORS.passed;
      ctx.fillRect(x, base - passedHeight, barWidth, passedHeight);
    }
    if (sample.rejected > 0) {
      ctx.fillStyle = pattern;
      ctx.fillRect(x, base - passedHeight - rejectedHeight, barWidth, rejectedHeight);
    }
  }
  if (!samples.length) drawEmpty(ctx, width, height);
}

export function drawLevelChart(ctx, samples, opts) {
  const width = opts.width;
  const height = opts.height;
  fit(ctx, width, height);
  const plotWidth = width - PAD.left - PAD.right;
  const plotHeight = height - PAD.top - PAD.bottom;

  ctx.font = AXIS_FONT;
  ctx.fillStyle = COLORS.muted;
  ctx.strokeStyle = COLORS.border;
  ctx.lineWidth = 1;
  for (let i = 0; i < 3; i++) {
    const y = PAD.top + plotHeight - (plotHeight * i) / 2;
    ctx.beginPath();
    ctx.moveTo(PAD.left, y);
    ctx.lineTo(width - PAD.right, y);
    ctx.stroke();
    ctx.textAlign = 'right';
    ctx.fillText(`${i * 50}%`, PAD.left - 8, y);
  }

  ctx.font = LEGEND_FONT;
  ctx.textAlign = 'left';
  ctx.fillText(LEVEL_LABELS[opts.algorithm] || '水位', PAD.left + 4, 10);
  drawXLabels(ctx, width, height);

  if (!samples.length) {
    drawEmpty(ctx, width, height);
    return;
  }

  const slot = plotWidth / SLOTS;
  const offset = SLOTS - samples.length;
  const base = PAD.top + plotHeight;
  const points = samples.map((sample, index) => ({
    x: PAD.left + (offset + index + 0.5) * slot,
    y: PAD.top + plotHeight * (1 - Math.min(1, Math.max(0, sample.level))),
  }));
  ctx.beginPath();
  ctx.moveTo(points[0].x, base);
  points.forEach((point) => ctx.lineTo(point.x, point.y));
  ctx.lineTo(points[points.length - 1].x, base);
  ctx.closePath();
  ctx.fillStyle = 'rgba(34,197,94,0.12)';
  ctx.fill();

  ctx.beginPath();
  points.forEach((point, index) => (index ? ctx.lineTo(point.x, point.y) : ctx.moveTo(point.x, point.y)));
  ctx.strokeStyle = COLORS.level;
  ctx.lineWidth = 2;
  ctx.stroke();

  ctx.font = VALUE_FONT;
  ctx.fillStyle = COLORS.level;
  ctx.textAlign = 'right';
  ctx.fillText(`${Math.round(samples[samples.length - 1].level * 100)}%`, width - PAD.right - 4, 12);
}

export function createChartScheduler(decisionCanvas, levelCanvas, getState) {
  const decisionCtx = decisionCanvas.getContext('2d');
  const levelCtx = levelCanvas.getContext('2d');
  let frame = null;

  const draw = () => {
    frame = null;
    const state = getState();
    const decisionRect = decisionCanvas.getBoundingClientRect();
    const levelRect = levelCanvas.getBoundingClientRect();
    const last = state.samples[state.samples.length - 1];
    drawDecisionChart(decisionCtx, state.samples, {
      width: decisionRect.width,
      height: decisionRect.height,
      qps: last ? last.offered * 10 : 20,
      algorithm: state.algorithm,
    });
    drawLevelChart(levelCtx, state.samples, {
      width: levelRect.width,
      height: levelRect.height,
      algorithm: state.algorithm,
    });
  };

  const schedule = () => {
    if (frame === null) frame = requestAnimationFrame(draw);
  };
  window.addEventListener('resize', schedule);
  return schedule;
}
