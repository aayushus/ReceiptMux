# ReceiptMux — Scanning Improvement Backlog

Prioritized work for the **scan → detect → crop/deskew → enhance → save** pipeline.
OCR-related work is intentionally excluded at this stage.

> Timing instrumentation is already in place: `ReceiptFrameAnalyzer` logs
> `Analyzer timing res=...x... avg=..ms max=..ms fps=..` every 2s (tag `ReceiptMuxScanner`).
> Use this to decide P4.

---

## P1 — Manual corner-adjustment screen after capture
- [ ] Add a post-capture screen to drag the 4 detected corners and re-warp.
- Detection already returns `orderedCorners` + skew; reuse `OpenCvReceiptToolkit.warpReceipt`.
- Rationale: self-contained, no measurement needed; rescues every near-miss of auto-detect.

## P0 — Quality metric + labeled sample set (prerequisite for P2)
- [ ] Collect a small set of receipt photos with ground-truth corners.
- [ ] Add a metric (corner IoU / crop coverage) via Robolectric or instrumentation golden tests.
- Rationale: detection/crop/enhancement currently has zero tests; makes P2 measurable.

## P2 — Detection robustness on hard cases
- [ ] Handle white-on-white, busy/textured backgrounds, and edges running off-frame.
- [ ] Option A: augment classical CV (saturation/color delta from border + Hough/LSD line segments).
- [ ] Option B: integrate ML Kit Document Scanner behind an injected detector interface.
- Rationale: highest impact on crop quality; higher effort — do P0 first.

## P3 — Output: modes + PDF export
- [ ] Add Color / Grayscale / B&W document output modes (stop always binarizing).
- [ ] Add multi-page PDF export.
- Rationale: feature gap for "scan to FTP"; affects usability of uploaded files.

## P4 — Realtime detection cost (gated on timing data)
- [ ] Read `Analyzer timing` logs on-device during a real scan session.
- [ ] If `analyze()` spikes / device heats up: reuse luma `ByteArray` buffer + modest resolution cap.
- [ ] If cheap and cool: skip.
- Note: lowering analysis resolution too far hurts detection.

## P5 — Recycle intermediate Bitmaps in `BitmapReceiptProcessor.process()`
- [ ] Recycle intermediate full-res `ARGB_8888` bitmaps (rotate → warp/crop → tighten → trim → enhance).
- Rationale: cuts peak memory / OOM risk on high-MP sensors.

## P6 — Capture-UX polish
- [ ] Temporal smoothing (EMA/Kalman) on the detected box to reduce overlay jitter.
- [ ] Live coaching hints ("move closer / too dark / hold steady / flatten") from existing signals
      (`boxArea`, `luminance`, `stabilityScore`, `geometrySkew`).
- [ ] Shutter/haptic cue on auto-capture.
- [ ] Gate `Log.d` behind `BuildConfig.DEBUG`.

---

### Suggested order
P1 (immediate win) → P0 → P2 (measurable detection improvement) → P3 → P4/P5/P6 as polish.
