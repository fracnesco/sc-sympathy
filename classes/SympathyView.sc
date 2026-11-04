// =====================================================================
// SympathyView - Sympathy's display: the keyboard its voices sit on
//
// The output's spectrum over a keyboard from the bottom of the range to the top
// of the top voice, on the same scale. A mark per voice, as tall as its level,
// and the first harmonics of each as ticks on the spectrum.
//
//   drag a mark along      that voice to another note
//   drag a mark up, down   its level
//   the wheel              every voice together: the root
// =====================================================================

SympathyView : FxParamView {
	classvar <lowNote = 24, <highNote = 108;
	var dragging, keyHeight = 28;

	*wantsSpectrum { ^true }

	prX { |midi| ^FxParamView.freqToX(midi.midicps, bw, lowNote.midicps, highNote.midicps) }
	prNoteAt { |x| ^FxParamView.xToFreq(x, bw, lowNote.midicps, highNote.midicps).cpsmidi }

	prDraw {
		var kb = bh - keyHeight;
		Pen.fillColor = FxPalette.bg;
		Pen.fillRect(Rect(0, 0, bw, bh));
		this.prDrawSpectrumHere(kb);
		(lowNote..highNote).do { |m|
			var a = this.prX(m), b = this.prX(m + 1);
			var black = #[1, 3, 6, 8, 10].includes(m % 12);
			Pen.fillColor = if (black) { Color.grey(0.1) } { Color.grey(0.6) };
			Pen.fillRect(Rect(a, kb, (b - a - 1).max(1), keyHeight));
		};
		Sympathy.voices.do { |v|
			var note = unit.voiceNote(v), x = this.prX(note), level = unit.get(Sympathy.level(v));
			var colour = FxPalette.eqBand(v);
			Pen.strokeColor = colour.copy.alpha_(0.35);
			Pen.width = 1;
			Sympathy.harmonics.do { |h|
				var k = h + 1;
				var hx = FxParamView.freqToX(note.midicps * k * (1 + (unit.inharm * k * k)).sqrt, bw, lowNote.midicps, highNote.midicps);
				Pen.line(hx @ (kb * (1 - (level / k))), hx @ kb);
				Pen.stroke;
			};
			Pen.fillColor = colour;
			Pen.fillRect(Rect(x - 3, kb - (level * (kb - 16)), 6, level * (kb - 16)));
			Pen.fillOval(Rect(x - 5, kb - (level * (kb - 16)) - 5, 10, 10));
		};
		this.prCaption("root % - decay % s".format(unit.root.round(0.1), unit.decay.round(0.01)));
	}

	prDrawSpectrumHere { |height|
		var levels, freqs, lo = ParaEQSpectrum.floorDb, hi = ParaEQSpectrum.topDb;
		if (spectrum.isNil or: { spectrum.visible.not } or: { spectrum.levels.isNil }) { ^this };
		levels = spectrum.levels;
		freqs = spectrum.freqs;
		Pen.fillColor = FxPalette.wave.copy.alpha_(0.3);
		Pen.moveTo(0 @ height);
		levels.do { |db, i|
			Pen.lineTo(FxParamView.freqToX(freqs[i], bw, lowNote.midicps, highNote.midicps) @ db.linlin(lo, hi, height, 0, \minmax));
		};
		Pen.lineTo(bw @ height);
		Pen.fill;
	}

	prMouseDown { |x, y, mod, count|
		var best;
		Sympathy.voices.do { |v|
			var d = (this.prX(unit.voiceNote(v)) - x).abs;
			if (d < 10 and: { best.isNil or: { d < best[1] } }) { best = [v, d] };
		};
		dragging = best !? { |b| b[0] };
	}

	prMouseMove { |x, y, mod|
		if (dragging.notNil) {
			unit.set(Sympathy.note(dragging), (this.prNoteAt(x) - unit.root).round(if (mod.isShift) { 0.1 } { 1 }));
			if (y < (bh - keyHeight)) {
				unit.set(Sympathy.level(dragging), y.linlin(16, bh - keyHeight, 1, 0, \minmax));
			};
		};
	}

	prMouseUp { dragging = nil }

	prWheel { |x, y, delta| unit.root = unit.root + delta.sign }

	hint { ^"the voices on a keyboard: drag one along for its note, up and down for its level - the wheel moves the root, and them all with it" }
}
