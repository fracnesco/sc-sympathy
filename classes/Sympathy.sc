// =====================================================================
// Sympathy - six tuned voices that ring when the sound touches them
//
// Hold the pedal of a piano down and shout into it. Each voice is a set of
// resonators tuned to a note: six harmonics, stretched by `inharm` the way a
// stiff string's are, each ringing for `decay` seconds and the high ones less,
// by `bright`. What comes in sets them going, and they answer in the chord.
//
//   Ndef(\drums, { Sympathy.ar(Decay2.ar(Impulse.ar(2), 0.002, 0.05) * PinkNoise.ar(0.6 ! 2), \sympathy) }).play;
//   y = Sympathy(Ndef(\drums), \sympathy);
//   y.gui;
//   y.root = 48; y.chord = \min9;
//   y.decay = 6; y.bright = 0.7;
//   y.preset = 'gamelan';
//   y.tune(2, 7, 0.8);                    // voice 2 a fifth above the root, at 80%
//
// It rings for as long as it is told to: 72 resonators at stereo, about what an
// eq costs. Their level follows the input, so a long decay on a steady input
// gets loud; they are limited softly on the way out.
// =====================================================================

Sympathy : FxParamUnit {
	classvar <>voices = 6, <>harmonics = 6;

	*controlName { ^\sympathy }
	*hasTail { ^true }
	*displayClass { ^SympathyView }
	*windowSize { ^Point(1000, 580) }

	// the device window (sc-fx-ui): the frame of the family, a resonator's face
	*guiClass { ^FxDeviceGUI }
	*panelClass { ^SympathyPanel }
	*identityColour { ^FxPalette.identity(\resonator) }

	// a voice's distance from the root, in semitones, and whether it sounds
	*chords {
		^(
			octaves: [0, 12, 24, 36, -12, -24],
			fifth: [0, 7, 12, 19, 24, 31],
			major: [0, 4, 7, 12, 16, 19],
			minor: [0, 3, 7, 12, 15, 19],
			sus: [0, 5, 7, 12, 17, 19],
			min7: [0, 3, 7, 10, 12, 19],
			maj7: [0, 4, 7, 11, 12, 19],
			min9: [0, 3, 7, 10, 14, 19],
			whole: [0, 2, 4, 6, 8, 10],
			cluster: [0, 1, 2, 3, 4, 5],
			quartal: [0, 5, 10, 15, 20, 25]
		)
	}
	*chordNames { ^#[\octaves, \fifth, \major, \minor, \sus, \min7, \maj7, \min9, \whole, \cluster, \quartal] }

	// what a Tidal pattern may move through SuperDirtGUI, event by event
	*tidalParams { ^#[\root, \note1, \note2, \note3, \note4, \note5, \note6, \decay, \bright, \inharm, \detune, \vibrato, \vibratoRate, \hiCut, \loCut, \mix] }

	*params {
		^[
			this.knob(\root, 24, 84, \lin, 48, "midi", "root",
				"the note every string is counted from - type a name (e3) or a number",
				box: \note, lag: 0.02, section: \tuning, dec: 1, size: \big),
			6.collect { |i|
				var k = i + 1;
				[
					this.knob(("note" ++ k).asSymbol, -24, 36, \lin, #[0, 7, 12, 19, 24, 31][i], "st", "interval",
						"string %, in semitones from the root".format(k), lag: 0.02, section: ("string " ++ k).asSymbol, dec: 1),
					this.knob(("level" ++ k).asSymbol, 0, 1, \lin, 1, "", "level",
						"how loud string % rings - at 0 it is off".format(k), box: \pct, section: nil, floorText: "off")
				]
			},
			this.knob(\glide, 0, 1, \lin, 0.05, "s", "glide", "how long a string takes to reach a new note",
				lag: 0, section: \tuning, dec: 2, size: \small),

			this.knob(\decay, 0.05, 30, \exp, 3, "s", "decay", "how long the lowest harmonic rings, to -60 dB",
				section: \ring, size: \big),
			this.knob(\bright, 0, 1, \lin, 0.5, "", "bright",
				"how long the high harmonics ring against the low ones: dark, they die first",
				box: \pct, section: \ring, size: \big),
			this.knob(\inharm, 0, 0.02, \lin, 0, "", "stiffness",
				"the harmonics stretched sharp, as on a stiff string: at 1 % the 6th is nearly a minor third sharp",
				box: \pct, section: \ring, dec: 2, floorText: "off")
				.put(\menuItems, [["piano", 0.0012], ["bell", 0.012], ["metal", 0.02]]),
			this.knob(\detune, 0, 50, \lin, 4, "c", "detune", "the sides apart, in cents: slow beating",
				section: \wobble, dec: 1),
			this.knob(\vibrato, 0, 1, \lin, 0, "", "wander",
				"how far each string drifts off its note, slowly and at random: at most a semitone",
				box: \pct, section: \wobble, floorText: "off"),
			this.knob(\vibratoRate, 0.05, 12, \exp, 3, "Hz", "rate", "how quickly the strings wander",
				section: \wobble),
			this.knob(\hiCut, 200, 20000, \exp, 12000, "Hz", "hi cut",
				"a low pass on what goes in: what is above it does not reach the strings", section: \excite, dec: 0),
			this.knob(\loCut, 20, 2000, \exp, 40, "Hz", "lo cut",
				"a high pass on what goes in: what is below it does not reach the strings", section: \excite, dec: 0),
			this.knob(\input, -24, 24, \lin, 0, "dB", "input", "how hard the strings are played", section: \excite, dec: 1),
			this.knob(\width, 0, 2, \lin, 1, "", "width", "how far apart the sides are", box: \pct, section: \output),
			this.mixParams(\output, 0.6)
		]
	}

	// the window's pages (FxDevicePanel): the strings first, then what plays them and how they move
	*devicePages {
		^[
			\strings -> [
				(title: "tuning", keys: [
					(choice: "chord", items: this.chordNames.collect(_.asString), prompt: "chord...",
						action: { |unit, item| unit.chord = item.asSymbol },
						hint: "tunes the six strings to a chord on the root, every one at full level"),
					\root, \glide
				]),
				(title: "string", count: voices, colour: \identity,
					keys: { |i| [this.note(i), this.level(i)] }),
				(title: "ring", keys: [\decay, \bright, \inharm], colour: \identity)
			],
			\character -> [
				(title: "excite", keys: [\input, \loCut, \hiCut]),
				\wobble
			]
		]
	}

	*isActive { |key, unit|
		var k = key.asString;
		if (key == \vibratoRate) { ^unit.get(\vibrato) > 0 };
		if ("^note[1-6]$".matchRegexp(k)) { ^unit.get(("level" ++ k.last).asSymbol) > 0 };
		^true
	}

	*note { |i| ^("note" ++ (i + 1)).asSymbol }
	*level { |i| ^("level" ++ (i + 1)).asSymbol }

	*chordSettings { |name, levels|
		var d = ();
		this.chords[name].do { |st, i|
			d[this.note(i)] = st;
			levels !? { d[this.level(i)] = levels.wrapAt(i) };
		};
		^d
	}

	*presetList {
		var with = { |name, more| this.chordSettings(name).putAll(more) };
		^[
			\default, (),
			'piano lid', with.(\min9, (root: 48, decay: 4, bright: 0.65, inharm: 0.0012, mix: 0.5)),
			'long fifths', with.(\fifth, (root: 36, decay: 14, bright: 0.4, detune: 7, mix: 0.7)),
			'gamelan', with.(\whole, (root: 60, decay: 5, bright: 0.9, inharm: 0.012, detune: 18, mix: 0.7)),
			'dark room', with.(\octaves, (root: 36, decay: 8, bright: 0.1, hiCut: 2500, mix: 0.6)),
			'cluster bloom', with.(\cluster, (root: 72, decay: 2, bright: 0.8, detune: 10, vibrato: 0.05, mix: 0.8)),
			'major halo', with.(\major, (root: 60, decay: 6, bright: 0.7, loCut: 300, mix: 0.45)),
			'metal sheet', with.(\quartal, (root: 50, decay: 3, bright: 1, inharm: 0.02, detune: 25, mix: 0.8)),
			'wobble choir', with.(\maj7, (root: 55, decay: 9, bright: 0.45, vibrato: 0.12, vibratoRate: 4.5, mix: 0.6))
		]
	}

	// set the six voices to a chord by name
	chord_ { |name|
		var notes = this.class.chords[name.asSymbol];
		if (notes.isNil) {
			("Sympathy: chord is one of %".format(this.class.chordNames)).warn;
			^this
		};
		notes.do { |st, i| values[this.class.note(i)] = st; values[this.class.level(i)] = 1 };
		this.prPush;
		this.prChanged(\settings);
	}

	tune { |voice, semitones, level|
		semitones !? { this.set(this.class.note(voice), semitones) };
		level !? { this.set(this.class.level(voice), level) };
	}

	// what a voice sounds at, in midi
	voiceNote { |i| ^values[\root] + values[this.class.note(i)] }

	// the chord the six strings are tuned to now, by name, or nil when they make none of them
	chord {
		var notes = this.class.voices.collect { |i| values[this.class.note(i)] };
		^this.class.chordNames.detect { |name| this.class.chords[name] == notes }
	}

	*process { |sig, p, ctx|
		var n = sig.size, x, strings, wet, scale;
		var nyquist = SampleRate.ir * 0.45;
		x = HPF.ar(LPF.ar(sig * p[\input].dbamp, p[\hiCut]), p[\loCut]);
		// per side, the six strings apart (the display hears each)
		strings = n.collect { |c|
			// the left goes flat and the right sharp, by half the detune each
			var cents = p[\detune] * (((c % 2) * 2 - 1) * 0.5) / 100;
			voices.collect { |v|
				var note = Lag.kr(p[\root] + p[this.note(v)] + cents, p[\glide])
					+ (LFNoise2.kr(p[\vibratoRate]) * p[\vibrato]);
				var f = note.midicps;
				harmonics.collect { |h|
					var k = h + 1;
					var fk = f * k * (1 + (p[\inharm] * k * k)).sqrt;
					var ring = p[\decay] / (1 + (h * (1 - p[\bright]) * 3));
					var amp = p[this.level(v)] * (0.5 + (0.5 * p[\bright])) / k * BinaryOpUGen('<', fk, nyquist);
					Ringz.ar(x[c], fk.min(nyquist), ring, amp)
				}.sum
			}
		};
		// Ringz's gain at its note grows with its decay, and it is a long way up
		scale = 0.015 / p[\decay].sqrt;
		wet = strings.collect { |s| s.sum } * 0.015 / p[\decay].sqrt;
		// for the display only: how much each string rings, and the strings before the soft limit
		ctx[\extras] = voices.collect { |v|
			strings.collect { |s| Amplitude.kr(s[v], 0.01, 0.1) }.reduce(\max) * scale
		} ++ [wet.collect { |w| Amplitude.kr(w, 0.01, 0.1) }.reduce(\max)];
		wet = wet.tanh;
		^FxKit.width(LeakDC.ar(wet), p[\width])
	}
}
