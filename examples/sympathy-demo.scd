// Sympathy - a walkthrough, one block at a time
//
// Evaluate each ( ... ) block in turn with the cursor inside it. This is
// an editor document, not a file to .load.

// ---- 1. a sound, and Sympathy driven from its Ndef
(
s.boot;
)

(
Ndef(\drums, { Sympathy.ar(Decay2.ar(Impulse.ar(2), 0.002, 0.05) * PinkNoise.ar(0.6 ! 2), \sympathy) }).play;
~y = Sympathy(Ndef(\drums), \sympathy);
~y.gui;
)

// ---- 2. settings: each is a property, each moves the window
(
~y.root = 48; ~y.chord = \min9;
~y.decay = 6; ~y.bright = 0.7;
~y.tune(1, 7, 0.8);                // the second voice a fifth above the root, at 80%
~y.preset = 'gamelan';
)

// ---- 3. every preset; evaluate one at a time
~y.preset = \default;
~y.preset = 'piano lid';
~y.preset = 'long fifths';
~y.preset = \gamelan;
~y.preset = 'dark room';
~y.preset = 'cluster bloom';
~y.preset = 'major halo';
~y.preset = 'metal sheet';
~y.preset = 'wobble choir';

// ---- 4. settings kept, and carried
(
~y.saveAsPreset("demo");
~y.copySettings;
Sympathy.presetNames.postln;
Sympathy.removePreset("demo");
)

// ---- 5. the same effect with a node of its own, on the main out
(
~own = Sympathy.onBus(0, 2);
~own.pasteSettings;
~own.enabled = true;
~own.gui;
)

(
~own.free;
)

// ---- 6. tidy up
(
~y.free;
Ndef(\drums).clear;
)
