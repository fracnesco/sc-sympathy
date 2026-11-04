# Sympathy

**Six tuned voices that ring when the sound touches them - hold a piano's pedal down and shout into it - running anywhere a UGen does, with a window.** A SuperCollider extension.

```supercollider
s.boot;
Ndef(\drums, { Sympathy.ar(Decay2.ar(Impulse.ar(2), 0.002, 0.05) * PinkNoise.ar(0.6 ! 2), \sympathy) }).play;

y = Sympathy(Ndef(\drums), \sympathy);
y.gui;
y.root = 48; y.chord = \min9;
y.decay = 6; y.bright = 0.7;
y.tune(1, 7, 0.8);                // the second voice a fifth above the root, at 80%
y.preset = 'gamelan';
```

Every effect of this family lives in the same three places. Write `Sympathy.ar` into a graph of your own and drive it from the object, as above; give it a node of its own on a bus, `Sympathy.onBus(0, 2).enabled_(true)`; or put it in a chain, `FxChain.master.add(Sympathy())`. If SuperDirtGUI is installed, it is an insert on an orbit too: `~sdg.addInsert(0, \sympathy)`.

It is built on [FxKit](../sc%20fx%20kit), which is built on [ParaEQ](../sc%20EQ), and the class library will not compile without them; the installer links both when the folders sit side by side. It uses SuperCollider's own UGens, not sc3-plugins.

Written for SuperCollider 3.13 on macOS. Nothing in it is platform specific apart from the symlink in `install.scd`, which Linux shares and Windows does not.

## Install

### As a quark

```supercollider
Quarks.install("https://github.com/fracnesco/sc-sympathy.git");
thisProcess.recompile;
```

The manifest names `sc-fx-kit` as a dependency, which names `sc-eq`, so `Quarks` fetches those first. The quark goes by the name of the repository, `sc-sympathy`; the class inside it is `Sympathy`.

### From a clone

Clone `sc-eq` into a folder called `sc EQ`, `sc-fx-kit` into `sc fx kit` and this into `sc sympathy`, side by side. Open `install.scd` and evaluate its first block, then recompile the class library. It links what is missing and tells you what it did.

On Windows there is no `ln -s`, so use the quark, or copy the three folders into the extensions folder by hand: `Platform.userExtensionDir.openOS` opens it.

Install it one way or the other, **not both**: two copies on the class path is a duplicate class error at compile time.

### Check that it took

```supercollider
Sympathy.presetNames;
Sympathy.onBus(0, 2).enabled_(true).gui;
```

Select `Sympathy` and press cmd/ctrl + d for the help file, and see [examples/sympathy-demo.scd](examples/sympathy-demo.scd) for a walkthrough you can evaluate a block at a time.

## What it does

Each voice is six resonators tuned to the harmonics of a note: `inharm` stretches them as a stiff string's are, and the high ones ring for less time as `bright` comes down. What comes in sets them going, and they answer in the chord.

A voice is a distance from `root` in semitones, `note1..6`, and a level, `level1..6`, which has no knob: drag it on the keyboard, or use `tune`. `chord` sets all six at once, from the names in `Sympathy.chordNames`, and is a command rather than a setting, so a preset holds the notes it made.

Their level follows the input, so a long decay on a steady input gets loud, and they are limited softly on the way out. Measured on one hit of noise, `decay` is the time to about 60 dB down.

It is 72 `Ringz` at stereo, and about 3% of one core on an M4 Pro.

## The window

`y.gui` opens the top row (on, presets, save, copy, paste), the display, and a row of knobs per section. Hover over anything and the bar at the bottom says what it does.

In the display, drag a voice along the keyboard for its note, up and down for its level (shift for quarter tones and finer); the wheel moves the root and everything with it.

## Presets

Built in: default, piano lid, long fifths, gamelan, dark room, cluster bloom, major halo, metal sheet, wobble choir.

Yours go in `Platform.userAppSupportDir/sympathy-presets.scd`: `y.saveAsPreset("mine")`, or type a name in the window and press save. `y.copySettings` and `other.pasteSettings` carry settings from one to another.

## Parameters

Every parameter is a property: `y.root`, `y.root = ...`. These also work by name, `y.set(\root, ...)`, `y.get(\root)`. Knobs clip to their range. This table is generated from the class.

| parameter | section | range | default | what it does |
|---|---|---|---|---|
| `root` | tuning | 24 .. 84 midi | 48 | the note everything is counted from |
| `note1` | tuning | -24 .. 36 st | 0 | voice 1, in semitones from the root |
| `note2` | tuning | -24 .. 36 st | 7 | voice 2, in semitones from the root |
| `note3` | tuning | -24 .. 36 st | 12 | voice 3, in semitones from the root |
| `note4` | tuning | -24 .. 36 st | 19 | voice 4, in semitones from the root |
| `note5` | tuning | -24 .. 36 st | 24 | voice 5, in semitones from the root |
| `note6` | tuning | -24 .. 36 st | 31 | voice 6, in semitones from the root |
| `glide` | tuning | 0 .. 1 s | 0.05 | how long a voice takes to reach a new note |
| `decay` | string | 0.05 .. 30 s | 3 | how long the lowest harmonic rings |
| `bright` | string | 0 .. 1 | 0.5 | how long the high harmonics ring, against the low ones |
| `inharm` | string | 0 .. 0.02 | 0 | the harmonics stretched sharp, as on a stiff string |
| `detune` | string | 0 .. 50 c | 4 | the sides apart, in cents: slow beating |
| `vibrato` | string | 0 .. 1 | 0 | how far each voice wanders, at most a semitone |
| `vibratoRate` | string | 0.05 .. 12 Hz | 3 | how quickly |
| `hiCut` | string | 200 .. 20000 Hz | 12000 | a low pass on what goes in |
| `loCut` | string | 20 .. 2000 Hz | 40 | a high pass on what goes in |
| `input` | string | -24 .. 24 dB | 0 | how hard it is played |
| `width` | string | 0 .. 2 | 1 | how far apart the sides are |
| `mix` | string | 0 .. 1 | 0.6 | how much of the effect goes out, against the dry signal |
| `output` | string | -36 .. 24 dB | 0 | the level of the whole thing, in dB |

## Files

| file | what it is |
|---|---|
| [classes/Sympathy.sc](classes/Sympathy.sc) | the parameters, the presets, the DSP |
| [classes/SympathyView.sc](classes/SympathyView.sc) | the window's display |
| [HelpSource/Classes/Sympathy.schelp](HelpSource/Classes/Sympathy.schelp) | the help file |
| [HelpSource/Classes/SympathyView.schelp](HelpSource/Classes/SympathyView.schelp) | the help file for the display |
| [examples/sympathy-demo.scd](examples/sympathy-demo.scd) | a walkthrough, one block at a time |
| [install.scd](install.scd) | the symlink, a check, an uninstall |
| [sc-sympathy.quark](sc-sympathy.quark) | the quark manifest |

## License

MIT - see [LICENSE](LICENSE).
