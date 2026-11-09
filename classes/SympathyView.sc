// =====================================================================
// SympathyView - Sympathy's display: the strings on a keyboard, and how
// they ring out
//
// Two parts, each with its title:
//
//   strings   pitch along, a keyboard at the bottom (c2 to c9, Tidal's names),
//             the root marked under it. Each of the six strings stands at its
//             note, as tall as its level, numbered as in the string card; its
//             harmonics are the thin lines further up the keys, pulled sharp by
//             stiffness. Behind them, in blue, the output's spectrum on the same
//             keys. While it runs, a string that rings swells and shakes, as wide
//             as it is loud (the server's own reading of each string).
//   ring      a hit falling away: the level in dB down, seconds along, a line
//             per harmonic, the lowest thick - decay is how long it takes to
//             fall 60 dB, bright how much sooner the high ones go. Behind them,
//             in blue, what the strings did since the last hit. When the strings
//             build up past what the soft limit lets through, the caption says
//             by how much it holds them down.
//
//   strings:  click a string            pick it (the string card shows it)
//             drag it along             its interval (shift: fine)
//             drag it up and down       its level (the first move says which)
//             double click it           back to full level
//             click a key               the picked string to that note
//             drag the root mark        the root, every string with it; the wheel too
//   ring:     drag the decay handle     decay (the 1st harmonic's end)
//             drag the 6th's handle     bright
//             drag anywhere else        along: decay; up and down: bright
//             the wheel                 stiffness; double click: decay and bright back
// =====================================================================

SympathyView : FxParamView {
	classvar <lowNote = 24, <highNote = 108;
	var <>focusAction, <focus = 0, <>panel;
	// the layout as last measured: the two parts, the plot, the keys
	var sr, rr, plotTop = 40, plotBot = 100, kbTop = 100, kbBot = 120;
	// a drag going on: what it moves, where it started, which way it goes
	var drag, hover, hoverKeys;
	// the strings since the last hit: [seconds since, dB], and the span held while dragging
	var sweep, sweepAt = -100, sweepTop = -200, inFollow = -120, heldSpan;

	*minHeight { ^220 }
	*wantsSpectrum { ^true }

	*floorDb { ^-60 }
	// the spans the time axis keeps to, so it moves in steps (ReverbView's)
	*spans { ^#[0.1, 0.15, 0.2, 0.25, 0.3, 0.4, 0.5, 0.75, 1, 1.5, 2, 2.5, 3, 4, 5, 6, 8, 10, 12, 15, 20, 30, 45] }
	*niceSpan { |t| ^this.spans.detect { |s| s >= t } ? this.spans.last }
	*tickStep { |span, most = 5| ^#[0.01, 0.02, 0.025, 0.05, 0.1, 0.2, 0.25, 0.5, 1, 2, 2.5, 5, 10, 20].detect { |s| (span / s) <= most } ? 20 }

	// how long harmonic h (0 = the lowest) rings, as the DSP says
	*ringTime { |h, decay, bright| ^decay / (1 + (h * (1 - bright) * 3)) }
	// where harmonic h starts against the lowest, in dB
	*startDb { |h| ^(h + 1).reciprocal.ampdb }
	// harmonic h of a note, in midi, stretched by stiffness
	*harmonicNote { |note, h, inharm| var k = h + 1; ^(note.midicps * k * (1 + (inharm * k * k)).sqrt).cpsmidi }

	identity { ^Sympathy.identityColour }

	focus_ { |i| focus = i.clip(0, Sympathy.voices - 1); this.refresh }

	// what a drag going on moves (a string's index, \root, \keys, \decay, \bright, \ring), or nil
	dragging { ^drag !? { drag[\what] } }

	// ----------------------------------------------------------------- layout

	prLayout {
		var split = (bw * 0.62).round;
		sr = Rect(0, 0, split - 6, bh);
		rr = Rect(split + 6, 0, bw - split - 6, bh);
		plotTop = 44;
		kbBot = bh - 16;
		kbTop = kbBot - 22;
		plotBot = kbTop - 1;
	}

	prX { |midi| ^sr.left + 8 + ((sr.width - 16) * midi.linlin(lowNote, highNote, 0, 1, nil)) }
	prNoteAt { |x| ^(x - sr.left - 8).linlin(0, sr.width - 16, lowNote, highNote, nil) }
	prLevelY { |level| ^level.linlin(0, 1, plotBot - 4, plotTop + 10) }
	prLevelAt { |y| ^y.linlin(plotTop + 10, plotBot - 4, 1, 0, \minmax) }

	// the ring part: its plot inside the axes
	prRingRect { ^Rect(rr.left + 30, rr.top + 26, rr.width - 40, rr.height - 26 - 20) }
	prSpan { ^heldSpan ?? { this.class.niceSpan(unit.get(\decay) * 1.08) } }
	prTx { |t, r, span| ^r.left + (t / span.max(1e-3) * r.width) }
	prTAt { |x, r, span| ^(x - r.left) / r.width.max(1) * span }
	prDbY { |db, r| ^db.linlin(this.class.floorDb, 0, r.bottom, r.top, \minmax) }

	// ------------------------------------------------------------ what is known

	prLive { ^unit.isAlive and: { (unit.extras ? []).size >= (Sympathy.voices + 1) } }

	// how much string v rings now, 0..1 (nil when nothing is heard)
	prRing { |v|
		var a;
		if (this.prLive.not) { ^nil };
		a = unit.extras[v] ? 0;
		^a.max(1e-6).ampdb.linlin(-54, -6, 0, 1, \minmax)
	}

	// what the soft limit takes off now, in dB (0 when it does not act)
	limitDb {
		var x;
		if (this.prLive.not) { ^0 };
		x = unit.extras[Sympathy.voices] ? 0;
		^if (x < 1e-4) { 0 } { (x.tanh / x).ampdb }
	}

	// the strings as the display sees them: note, level, x, the head's point
	prStrings {
		^Sympathy.voices.collect { |v|
			var note = unit.voiceNote(v), level = unit.get(Sympathy.level(v));
			var x = this.prX(note.clip(lowNote, highNote));
			(v: v, note: note, level: level, x: x,
				head: x @ if (level > 0) { this.prLevelY(level) } { plotBot - 10 },
				off: (note < lowNote) or: { note > highNote })
		}
	}

	prStringAt { |x, y|
		var best, dist = 9;
		if (sr.isNil or: { y > plotBot } or: { y < (plotTop - 22) } or: { x > sr.right }) { ^nil };
		this.prStrings.do { |s|
			var d = (s[\x] - x).abs;
			// the head counts more than the string under it
			if (s[\head].dist(x @ y) < 9) { d = d - 4 };
			if ((d < dist) or: { (d == dist) and: { s[\v] == focus } }) { dist = d; best = s[\v] };
		};
		^best
	}

	prOverRoot { |x, y| ^(y > kbBot) and: { (x - this.prX(unit.get(\root))).abs < 10 } }
	prOverKeys { |x, y| ^(y >= kbTop) and: { y <= kbBot } and: { x < sr.right } }

	prRingHandles {
		var r = this.prRingRect, span = this.prSpan, decay = unit.get(\decay), bright = unit.get(\bright);
		var last = Sympathy.harmonics - 1;
		var t6 = this.class.ringTime(last, decay, bright) * ((this.class.floorDb.neg + this.class.startDb(last)) / this.class.floorDb.neg);
		^[
			(key: \decay, at: this.prTx(decay, r, span).min(r.right) @ r.bottom, t: decay),
			(key: \bright, at: this.prTx(t6, r, span).min(r.right) @ r.bottom, t: t6)
		]
	}

	prRingHandleAt { |x, y|
		var best, dist = 10;
		this.prRingHandles.do { |h|
			var d = h[\at].dist(x @ y);
			if (d < dist) { dist = d; best = h };
		};
		^best
	}

	// ------------------------------------------------------------------- draw

	prDraw {
		this.prLayout;
		this.prDrawStrings;
		Pen.strokeColor = FxPalette.fade(FxPalette.waveDim, 0.5);
		Pen.width = 1;
		Pen.line((rr.left - 6) @ 6, (rr.left - 6) @ (bh - 6));
		Pen.stroke;
		this.prDrawRing;
	}

	prDrawStrings {
		var strings = this.prStrings, col = this.identity, chord = unit.chord, root = unit.get(\root);
		var f = strings[focus], rows = [nil, nil], labels;
		Pen.push;
		Pen.addRect(sr);
		Pen.clip;
		// the readout: the tuning, and the string picked
		this.prCaption("strings  ·  root % · %".format(FxPalette.midiName(root), chord ? "own chord"), 8, 4, FxPalette.text);
		Pen.stringRightJustIn("string %  ·  %  ·  % st  ·  %".format(focus + 1, this.prNoteName(f[\note]),
				this.prSigned(unit.get(Sympathy.note(focus))), FxPalette.format(f[\level], "", \pct, nil, false, "off", ControlSpec(0, 1))),
			Rect(sr.left, 4, sr.width - 8, 12), FxPalette.smallFont, FxPalette.dim);
		this.prDrawGrid;
		this.prDrawSpectrumHere;
		this.prDrawKeys(strings);
		// the harmonics, then the strings, the picked one last (on top)
		strings.do { |s| if (s[\level] > 0) { this.prDrawHarmonics(s, col) } };
		strings.reject { |s| s[\v] == focus }.do { |s| this.prDrawString(s, col) };
		this.prDrawString(f, col);
		// the note names along the top, in two rows so neighbours do not overlap;
		// one that would need a third is left out (a cluster: the caption names the picked one)
		labels = strings.reject { |s| s[\off] }.sort { |a, b| a[\x] < b[\x] };
		labels.do { |s|
			var str = this.prNoteName(s[\note]), w = str.bounds(FxPalette.smallFont).width + 6;
			var row = [0, 1].detect { |i| (rows[i] ? -1000) < (s[\x] - (w / 2)) };
			row !? {
				rows[row] = s[\x] + (w / 2);
				Pen.stringCenteredIn(str, Rect(s[\x] - (w / 2), 18 + (row * 11), w, 12), FxPalette.smallFont,
					FxPalette.fade(if (s[\v] == focus) { FxPalette.text } { FxPalette.dim }, if (s[\level] > 0) { 1 } { 0.6 }));
			};
		};
		Pen.pop;
	}

	prNoteName { |note|
		var cents = ((note - note.round) * 100).round.asInteger;
		^FxPalette.midiName(note) ++ if (cents != 0) { (if (cents > 0) { " +" } { " " }) ++ cents ++ "c" } { "" }
	}

	prSigned { |x| ^(if (x > 0) { "+" } { "" }) ++ FxPalette.fixed(x, if (x.frac.abs < 0.05) { 0 } { 1 }) }

	prDrawGrid {
		Pen.width = 1;
		[1, 0.5].do { |l|
			var y = this.prLevelY(l);
			Pen.strokeColor = FxPalette.fade(FxPalette.waveDim, 0.35);
			Pen.line((sr.left + 40) @ y, (sr.right - 8) @ y);
			Pen.stroke;
			Pen.stringLeftJustIn((l * 100).asInteger.asString ++ " %", Rect(sr.left + 6, y - 6, 34, 12),
				FxPalette.smallFont, FxPalette.dim);
		};
		// an octave line at every c
		(lowNote, lowNote + 12 .. highNote).do { |m|
			var x = this.prX(m);
			Pen.strokeColor = FxPalette.fade(FxPalette.waveDim, if (m == lowNote) { 0 } { 0.22 });
			Pen.line(x @ plotTop, x @ plotBot);
			Pen.stroke;
		};
	}

	// the output's spectrum on the same keys, filled, behind the strings
	prDrawSpectrumHere {
		var levels, freqs, lo = ParaEQSpectrum.floorDb, hi = ParaEQSpectrum.topDb, pts;
		if (spectrum.isNil or: { spectrum.visible.not } or: { spectrum.levels.isNil }) { ^this };
		levels = spectrum.levels;
		freqs = spectrum.freqs;
		pts = levels.collect { |db, i| this.prX(freqs[i].max(1).cpsmidi) @ db.linlin(lo, hi, plotBot, plotTop, \minmax) };
		Pen.push;
		Pen.addRect(Rect.fromPoints(this.prX(lowNote) @ plotTop, this.prX(highNote + 1) @ plotBot));
		Pen.clip;
		Pen.moveTo(pts[0].x @ plotBot);
		pts.do { |p| Pen.lineTo(p) };
		Pen.lineTo(pts.last.x @ plotBot);
		Pen.fillColor = FxPalette.fade(FxPalette.wave, 0.18);
		Pen.fill;
		Pen.strokeColor = FxPalette.fade(FxPalette.wave, 0.45);
		Pen.width = 1;
		Pen.moveTo(pts[0]);
		pts.do { |p| Pen.lineTo(p) };
		Pen.stroke;
		Pen.pop;
	}

	prDrawKeys { |strings|
		var col = this.identity, h = kbBot - kbTop, root = unit.get(\root), rx = this.prX(root), lit;
		lit = strings.select { |s| s[\level] > 0 and: { s[\off].not } }.collect { |s| s[\note].round.asInteger };
		// the white keys as one strip, a line between them; the black keys over it
		Pen.fillColor = Color.grey(0.30);
		Pen.fillRect(Rect(this.prX(lowNote), kbTop, this.prX(highNote + 1) - this.prX(lowNote), h));
		(lowNote..highNote).do { |m|
			var a = this.prX(m), b = this.prX(m + 1), black = #[1, 3, 6, 8, 10].includes(m % 12);
			if (lit.includes(m)) {
				Pen.fillColor = FxPalette.fade(col, if (black) { 0.75 } { 0.9 });
				Pen.fillRect(Rect(a, kbTop, b - a, if (black) { h * 0.62 } { h }));
			} {
				if (black) {
					Pen.fillColor = Color.grey(0.12);
					Pen.fillRect(Rect(a, kbTop, b - a, h * 0.62));
				};
			};
			if (#[0, 5].includes(m % 12)) {
				Pen.fillColor = Color.grey(0.16);
				Pen.fillRect(Rect(a, kbTop, 1, h));
			};
		};
		// the octaves' names under the keys
		(lowNote, lowNote + 12 .. highNote).do { |m|
			var x = this.prX(m);
			if ((x - rx).abs > 34) {
				if ((x + 30) > sr.right) {
					Pen.stringRightJustIn(FxPalette.midiName(m), Rect(x - 30, kbBot + 2, 30, 12), FxPalette.smallFont, FxPalette.dim);
				} {
					Pen.stringLeftJustIn(FxPalette.midiName(m), Rect(x, kbBot + 2, 30, 12), FxPalette.smallFont, FxPalette.dim);
				};
			};
		};
		// the root: a mark under its key, and its name
		Pen.fillColor = if (this.dragging == \root or: { hover == \root }) { FxPalette.accent } { FxPalette.text };
		Pen.moveTo(rx @ (kbBot + 1));
		Pen.lineTo((rx - 5) @ (kbBot + 9));
		Pen.lineTo((rx + 5) @ (kbBot + 9));
		Pen.lineTo(rx @ (kbBot + 1));
		Pen.fill;
		Pen.stringLeftJustIn("root " ++ FxPalette.midiName(root), Rect(rx + 8, kbBot + 2, 60, 12), FxPalette.smallFont, FxPalette.text);
		Pen.strokeColor = FxPalette.fade(FxPalette.text, 0.25);
		Pen.line(rx @ plotTop, rx @ kbTop);
		Pen.stroke;
	}

	// a string's harmonics: a thin line at each, as tall as it is loud against the first
	prDrawHarmonics { |s, col|
		var inharm = unit.get(\inharm), ring = this.prRing(s[\v]) ? 0;
		(1 .. Sympathy.harmonics - 1).do { |h|
			var m = this.class.harmonicNote(s[\note], h, inharm), x, top;
			if (m <= highNote) {
				x = this.prX(m);
				top = this.prLevelY(s[\level] / (h + 1));
				Pen.strokeColor = FxPalette.fade(col, 0.28 + (0.4 * ring));
				Pen.width = 1;
				Pen.line(x @ top, x @ plotBot);
				Pen.stroke;
			};
		};
	}

	// one string: a line from its key up to its head, swelling and shaking while it rings
	prDrawString { |s, col|
		var x = s[\x], head = s[\head], ring = this.prRing(s[\v]), picked = s[\v] == focus;
		var on = s[\level] > 0, a = if (picked) { 1 } { 0.72 }, wide, shake, n = 14;
		if (on) {
			if (ring.notNil and: { ring > 0.01 }) {
				wide = 1 + (ring * 9);
				shake = (Main.elapsedTime * 37 + s[\v]).sin;
				// the swell: how far it moves
				Pen.fillColor = FxPalette.fade(col, 0.22 * a);
				Pen.moveTo(x @ plotBot);
				(0..n).do { |i| var f = i / n; Pen.lineTo((x + (wide * sin(pi * f))) @ plotBot.blend(head.y, f)) };
				(n..0).do { |i| var f = i / n; Pen.lineTo((x - (wide * sin(pi * f))) @ plotBot.blend(head.y, f)) };
				Pen.fill;
				// the string where it is now
				Pen.strokeColor = FxPalette.fade(col, a);
				Pen.width = 2;
				Pen.moveTo(x @ plotBot);
				(1..n).do { |i| var f = i / n; Pen.lineTo((x + (wide * shake * sin(pi * f))) @ plotBot.blend(head.y, f)) };
				Pen.stroke;
			} {
				Pen.strokeColor = FxPalette.fade(col, a);
				Pen.width = 2;
				Pen.line(x @ plotBot, head);
				Pen.stroke;
			};
		};
		// the head, numbered as in the string card
		if (on) {
			Pen.fillColor = FxPalette.fade(col, a);
			Pen.fillOval(Rect.aboutPoint(head, 7, 7));
		} {
			Pen.strokeColor = FxPalette.fade(FxPalette.dim, 0.8);
			Pen.width = 1;
			Pen.strokeOval(Rect.aboutPoint(head, 7, 7));
		};
		if (picked or: { hover == s[\v] } or: { this.dragging == s[\v] }) {
			Pen.strokeColor = if (this.dragging == s[\v]) { FxPalette.accent } { FxPalette.fade(FxPalette.handle, 0.85) };
			Pen.width = 1.5;
			Pen.strokeOval(Rect.aboutPoint(head, 10, 10));
		};
		Pen.stringCenteredIn((s[\v] + 1).asString, Rect.aboutPoint(head, 7, 7), FxPalette.smallFont,
			if (on) { FxPalette.waveBg } { FxPalette.dim });
		// a string off the keys points the way it went
		if (s[\off]) {
			Pen.stringCenteredIn(if (s[\note] < lowNote) { "<" } { ">" }, Rect(x - 6, head.y - 22, 12, 12),
				FxPalette.smallFont, FxPalette.text);
		};
	}

	prDrawRing {
		var r = this.prRingRect, span = this.prSpan, col = this.identity;
		var decay = unit.get(\decay), bright = unit.get(\bright), limit = this.limitDb, handles;
		Pen.push;
		Pen.addRect(rr);
		Pen.clip;
		this.prCaption("ring  ·  decay %  ·  bright %".format(FxPalette.format(decay, "s"),
			FxPalette.format(bright, "", \pct)), rr.left + 2, 4, FxPalette.text);
		if (limit < -0.3) {
			Pen.stringRightJustIn("limit % dB".format(FxPalette.fixed(limit, 1)), Rect(rr.left, 4, rr.width - 4, 12),
				FxPalette.smallFont, FxPalette.gain);
		};
		// the axes: dB up the left, seconds along the bottom
		[0, -20, -40, -60].do { |db|
			var y = this.prDbY(db, r);
			Pen.strokeColor = FxPalette.fade(FxPalette.waveDim, if (db == -60) { 0.6 } { 0.28 });
			Pen.width = 1;
			Pen.line(r.left @ y, r.right @ y);
			Pen.stroke;
			Pen.stringRightJustIn(if (db == 0) { "0 dB" } { db.asString }, Rect(rr.left - 6, y - 6, 32, 12), FxPalette.smallFont, FxPalette.dim);
		};
		this.prTimeAxis(r, span);
		// what the strings did since the last hit
		this.prDrawSweep(r, span);
		// a fall per harmonic, the lowest last and thickest
		(Sympathy.harmonics - 1 .. 0).do { |h|
			var t = this.class.ringTime(h, decay, bright), db0 = this.class.startDb(h);
			var tEnd = t * ((this.class.floorDb.neg + db0) / this.class.floorDb.neg);
			var p0 = this.prTx(0, r, span) @ this.prDbY(db0, r), p1;
			// cut at the right edge where it has not fallen yet
			p1 = if (tEnd > span) {
				r.right @ this.prDbY(db0 - (60 * span / t), r)
			} { this.prTx(tEnd, r, span) @ r.bottom };
			Pen.strokeColor = FxPalette.fade(col, if (h == 0) { 1 } { 0.75 - (h * 0.08) });
			Pen.width = if (h == 0) { 2 } { 1.2 };
			Pen.line(p0, p1);
			Pen.stroke;
		};
		// the handles: decay at the 1st's end, bright at the 6th's
		handles = this.prRingHandles;
		handles.do { |hd|
			var hot = (this.dragging == hd[\key]) or: { hover == hd[\key] }, p = hd[\at];
			if (hd[\key] == \decay) {
				Pen.fillColor = if (this.dragging == \decay) { FxPalette.accent } { col };
				Pen.fillOval(Rect.aboutPoint(p, 5, 5));
			} {
				Pen.fillColor = FxPalette.waveBg;
				Pen.fillOval(Rect.aboutPoint(p, 5, 5));
				Pen.strokeColor = if (this.dragging == \bright) { FxPalette.accent } { col };
				Pen.width = 1.5;
				Pen.strokeOval(Rect.aboutPoint(p, 5, 5));
			};
			if (hot) {
				Pen.strokeColor = FxPalette.fade(FxPalette.handle, 0.85);
				Pen.width = 1;
				Pen.strokeOval(Rect.aboutPoint(p, 8, 8));
			};
		};
		// their names, the 6th above the 1st so they do not collide
		this.prTag("1st " ++ FxPalette.format(decay, "s"), handles[0][\at] + (0 @ -14), r, col);
		this.prTag("6th " ++ FxPalette.format(handles[1][\t], "s"), handles[1][\at] + (0 @ -28), r, FxPalette.fade(col, 0.8));
		Pen.pop;
	}

	// a small label at p, kept inside r
	prTag { |str, p, r, colour|
		var w = str.bounds(FxPalette.smallFont).width + 6, x = (p.x - (w / 2)).clip(r.left, r.right - w);
		Pen.fillColor = FxPalette.fade(FxPalette.waveBg, 0.8);
		Pen.fillRect(Rect(x, p.y - 6, w, 12));
		Pen.stringCenteredIn(str, Rect(x, p.y - 6, w, 12), FxPalette.smallFont, colour);
	}

	prTimeAxis { |r, span|
		var step = this.class.tickStep(span, (r.width / 60).clip(2, 5)), n = (span / step + 1e-6).floor.asInteger;
		(n + 1).do { |i|
			var t = i * step, x = this.prTx(t, r, span), str = this.class.timeText(t), lr;
			Pen.strokeColor = FxPalette.fade(FxPalette.waveDim, if (i == 0) { 0.6 } { 0.22 });
			Pen.width = 1;
			Pen.line(x @ r.top, x @ r.bottom);
			Pen.stroke;
			lr = case
			{ i == 0 } { Rect(x, r.bottom + 4, 50, 12) }
			{ (x + 25) > rr.right } { Rect(x - 50, r.bottom + 4, 50, 12) }
			{ true } { Rect(x - 25, r.bottom + 4, 50, 12) };
			case
			{ i == 0 } { Pen.stringLeftJustIn(str, lr, FxPalette.smallFont, FxPalette.dim) }
			{ (x + 25) > rr.right } { Pen.stringRightJustIn(str, lr, FxPalette.smallFont, FxPalette.dim) }
			{ true } { Pen.stringCenteredIn(str, lr, FxPalette.smallFont, FxPalette.dim) };
		};
	}

	*timeText { |t| ^if (t == 0) { "0" } { FxPalette.format(t, "s") } }

	prDrawSweep { |r, span|
		var pts;
		if (sweep.isNil or: { sweep.size < 2 } or: { sweepTop < -70 }) { ^this };
		pts = sweep.select { |p| p[0] <= span }.collect { |p|
			this.prTx(p[0], r, span) @ this.prDbY((p[1] - sweepTop).max(this.class.floorDb), r)
		};
		if (pts.size < 2) { ^this };
		Pen.moveTo(pts[0].x @ r.bottom);
		pts.do { |p| Pen.lineTo(p) };
		Pen.lineTo(pts.last.x @ r.bottom);
		Pen.fillColor = FxPalette.fade(FxPalette.wave, 0.14);
		Pen.fill;
		Pen.strokeColor = FxPalette.fade(FxPalette.wave, 0.85);
		Pen.width = 1.5;
		Pen.moveTo(pts[0]);
		pts.do { |p| Pen.lineTo(p) };
		Pen.stroke;
	}

	// --------------------------------------------------------------- the live part

	// every meter reply: after a hit (the input jumping up), the strings' level from that moment on
	levelsArrived {
		var now = Main.elapsedTime, inDb, ringDb, span;
		if (unit.isNil) { ^this };
		inDb = (unit.inLevel ? 0).max(1e-6).ampdb;
		ringDb = if (this.prLive) { unit.extras[Sympathy.voices] } { unit.outLevel ? 0 };
		ringDb = ringDb.max(1e-6).ampdb;
		span = this.prSpan;
		if ((inDb > -50) and: { inDb > (inFollow + 9) } and: { (now - sweepAt) > 0.15 }) {
			this.prNewSweep(now);
		} {
			if ((now - sweepAt) > span) { this.prNewSweep(now) };
		};
		inFollow = max(inDb, inFollow - 1.5);
		sweep.add([now - sweepAt, ringDb]);
		sweepTop = max(sweepTop, ringDb);
	}

	prNewSweep { |now|
		sweep = List.new;
		sweepAt = now;
		sweepTop = -200;
	}

	// for a check: the readings drawn now, and their loudest
	sweep { ^sweep }
	sweepTop { ^sweepTop }

	// ----------------------------------------------------------------- mouse

	prMouseDown { |x, y, mod, count|
		var v, h;
		this.prLayout;
		drag = nil;
		if (x < rr.left) {
			case
			{ this.prOverRoot(x, y) } {
				if (count == 2) { unit.set(\root, Sympathy.param(\root)[\default]) } {
					drag = (what: \root, x: x, from: unit.get(\root))
				}
			}
			{ (v = this.prStringAt(x, y)).notNil } {
				this.prPick(v);
				if (count == 2) { unit.set(Sympathy.level(v), 1) } {
					drag = (what: v, x: x, y: y, axis: nil, note: unit.get(Sympathy.note(v)),
						level: unit.get(Sympathy.level(v)))
				}
			}
			{ this.prOverKeys(x, y) } {
				unit.set(Sympathy.note(focus), (this.prNoteAt(x).floor - unit.get(\root).round).clip(-24, 36));
				drag = (what: \keys);
			};
		} {
			heldSpan = this.prSpan;
			if (count == 2) {
				unit.set(\decay, Sympathy.param(\decay)[\default]);
				unit.set(\bright, Sympathy.param(\bright)[\default]);
				heldSpan = nil;
			} {
				h = this.prRingHandleAt(x, y);
				drag = (what: h !? { h[\key] } ? \ring, x: x, y: y, decay: unit.get(\decay), bright: unit.get(\bright));
			};
		};
		this.refresh;
	}

	prMouseMove { |x, y, mod|
		var d = drag, fine = mod.isShift, r, span, v;
		if (d.isNil) { ^this };
		r = this.prRingRect;
		span = this.prSpan;
		switch (d[\what],
			\root, {
				v = d[\from] + ((x - d[\x]) / (this.prX(lowNote + 1) - this.prX(lowNote)) * if (fine) { 0.1 } { 1 });
				unit.set(\root, v.round(if (fine) { 0.1 } { 1 }).clip(24, 84));
			},
			\keys, {
				if (this.prOverKeys(x, y)) {
					unit.set(Sympathy.note(focus), (this.prNoteAt(x).floor - unit.get(\root).round).clip(-24, 36));
				};
			},
			\decay, {
				unit.set(\decay, this.prTAt(x, r, span).clip(0.05, 30));
			},
			\bright, {
				// the 6th's end is where the pointer is: its ring time, then bright from it
				v = this.prTAt(x, r, span) / ((this.class.floorDb.neg + this.class.startDb(Sympathy.harmonics - 1)) / this.class.floorDb.neg);
				unit.set(\bright, (1 - (((unit.get(\decay) / v.max(1e-3)) - 1) / ((Sympathy.harmonics - 1) * 3))).clip(0, 1));
			},
			\ring, {
				if (d[\axis].isNil and: { ((x - d[\x]).abs > 4) or: { (y - d[\y]).abs > 4 } }) {
					d[\axis] = if ((x - d[\x]).abs >= (y - d[\y]).abs) { \x } { \y };
				};
				switch (d[\axis],
					\x, { unit.set(\decay, (d[\decay] * (2 ** ((x - d[\x]) / 80 * if (fine) { 0.1 } { 1 }))).clip(0.05, 30)) },
					\y, { unit.set(\bright, (d[\bright] + ((d[\y] - y) / 150 * if (fine) { 0.1 } { 1 })).clip(0, 1)) }
				);
			},
			{
				// a string: the first move says which way
				v = d[\what];
				if (d[\axis].isNil and: { ((x - d[\x]).abs > 4) or: { (y - d[\y]).abs > 4 } }) {
					d[\axis] = if ((x - d[\x]).abs >= (y - d[\y]).abs) { \x } { \y };
				};
				switch (d[\axis],
					\x, {
						unit.set(Sympathy.note(v), (d[\note] + ((this.prNoteAt(x) - this.prNoteAt(d[\x])) * if (fine) { 0.1 } { 1 }))
							.round(if (fine) { 0.1 } { 1 }).clip(-24, 36));
					},
					\y, {
						unit.set(Sympathy.level(v), if (fine) {
							(d[\level] + ((d[\y] - y) / 1500)).clip(0, 1)
						} { this.prLevelAt(y) });
					}
				);
			}
		);
		this.refresh;
	}

	prMouseUp { drag = nil; heldSpan = nil; this.refresh }

	prWheel { |x, y, delta|
		var step;
		this.prLayout;
		if (delta == 0) { ^this };
		if (x < rr.left) {
			unit.set(\root, (unit.get(\root) + delta.sign).clip(24, 84));
		} {
			step = Sympathy.param(\inharm)[\spec].maxval / 100;
			unit.set(\inharm, (unit.get(\inharm) + (delta.sign * step)).clip(0, Sympathy.param(\inharm)[\spec].maxval));
		};
	}

	prPick { |v|
		focus = v;
		focusAction.value(v);
	}

	// ----------------------------------------------------------------- hints

	hint { |x, y|
		var v, h, str, keys = [], was = hover;
		if (unit.isNil) { ^"" };
		this.prLayout;
		hover = nil;
		str = case
		{ x.isNil } { "the strings on a keyboard, and how they ring out" }
		{ x < rr.left } {
			case
			{ this.prOverRoot(x, y) } {
				hover = \root;
				keys = [\root];
				"root %  -  drag it along, or turn the wheel over the strings: every string moves with it; double click: back to %"
					.format(FxPalette.midiName(unit.get(\root)), FxPalette.midiName(Sympathy.param(\root)[\default]))
			}
			{ (v = this.prStringAt(x, y)).notNil } {
				hover = v;
				keys = [Sympathy.note(v), Sympathy.level(v)];
				"string %  %  % st  %  -  drag it along for its note (shift: fine), up and down for its level; double click: full"
					.format(v + 1, this.prNoteName(unit.voiceNote(v)), this.prSigned(unit.get(Sympathy.note(v))),
						FxPalette.format(unit.get(Sympathy.level(v)), "", \pct, nil, false, "off", ControlSpec(0, 1)))
			}
			{ this.prOverKeys(x, y) } {
				"%  -  click to tune string % there".format(FxPalette.midiName(this.prNoteAt(x).floor), focus + 1)
			}
			{ true } {
				"the strings on the keys, the output's spectrum behind  -  click a string to pick it; the wheel moves the root"
			}
		}
		{ true } {
			h = this.prRingHandleAt(x, y);
			case
			{ h.isNil } {
				hover = \ring;
				keys = [\decay, \bright];
				"how a hit rings out, a line per harmonic, and in blue what the strings did since the last  -  drag along: decay, up and down: bright; the wheel: stiffness; double click: both back"
			}
			{ h[\key] == \decay } {
				hover = \decay;
				keys = [\decay];
				"decay %  -  how long the lowest harmonic rings, to -60 dB: drag it along".format(FxPalette.format(unit.get(\decay), "s"))
			}
			{ true } {
				hover = \bright;
				keys = [\bright];
				"bright %  -  the 6th harmonic rings for %: drag it along; the darker, the sooner the high ones go"
					.format(FxPalette.format(unit.get(\bright), "", \pct), FxPalette.format(h[\t], "s"))
			}
		};
		this.prHighlight(keys);
		if (hover != was) { this.refresh };
		^str
	}

	// the knobs of what is under the pointer, lit in their colour
	prHighlight { |keys|
		if (keys == hoverKeys) { ^this };
		hoverKeys = keys;
		panel !? { |p|
			p.widgets.keysValuesDo { |k, w| w.tryPerform(\highlight_, keys.includes(k)) };
		};
	}
}


// =====================================================================
// SympathyPanel - FxDevicePanel, with the chord menu saying which chord the
// strings make now ("own" when none), and the display lighting the knobs of
// what is under the pointer
// =====================================================================

SympathyPanel : FxDevicePanel {
	var chordChoice;

	init { |argUnit, parent|
		super.init(argUnit, parent);
		display !? { |d| d.tryPerform(\panel_, this) };
	}

	prActionCell { |e, colour|
		var v = super.prActionCell(e, colour);
		if (e[\choice].notNil) { chordChoice = actions.last[\widget] };
		^v
	}

	prRefreshActive {
		super.prRefreshActive;
		this.prShowChord;
	}

	prShowChord {
		var i;
		if (chordChoice.isNil or: { unit.isNil }) { ^this };
		i = unit.chord !? { |name| cls.chordNames.indexOf(name) };
		chordChoice.override = if (i.isNil) { "own" };
		chordChoice.value = i ? chordChoice.value;
	}

	// the chord menu, for a check
	chordChoice { ^chordChoice }
}
