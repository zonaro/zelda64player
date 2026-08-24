#!/usr/bin/env python3
"""Generate the original Switch-style UI sound effects for Zelda 64 Player.

All clips are synthesized from scratch using only the Python standard library
(wave, math, struct), so there are no licensing concerns. Output is 44.1 kHz,
mono, 16-bit PCM WAV written into ``app/src/main/res/raw/``.

Run from the repository root:

    python3 tools/gen_switch_sfx.py
"""

import math
import os
import struct
import wave

SAMPLE_RATE = 44100
# Resolve the res/raw directory relative to this script (tools/ -> repo root).
OUT_DIR = os.path.normpath(
    os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")
)


def write_wav(filename, samples):
    """Write a list of float samples in [-1, 1] as a 16-bit mono WAV."""
    os.makedirs(OUT_DIR, exist_ok=True)
    path = os.path.join(OUT_DIR, filename)
    with wave.open(path, "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SAMPLE_RATE)
        frames = bytearray()
        for s in samples:
            s = max(-1.0, min(1.0, s))
            frames += struct.pack("<h", int(s * 32767))
        w.writeframes(bytes(frames))
    print("wrote", path)


def tone_sweep(duration_ms, f0, f1, amp=0.5, attack_ms=3.0, decay_ms=None):
    """Linear-frequency sine sweep with a simple attack/decay envelope."""
    n = int(SAMPLE_RATE * duration_ms / 1000.0)
    decay_ms = decay_ms if decay_ms is not None else duration_ms * 0.6
    attack_n = max(1, int(SAMPLE_RATE * attack_ms / 1000.0))
    decay_n = max(1, int(SAMPLE_RATE * decay_ms / 1000.0))
    out = []
    for i in range(n):
        t = i / SAMPLE_RATE
        frac = i / max(1, n - 1)
        freq = f0 + (f1 - f0) * frac
        # Phase integration for a clean linear sweep.
        phase = 2.0 * math.pi * (f0 * t + 0.5 * (f1 - f0) * t * frac)
        if i < attack_n:
            env = i / attack_n
        elif i > n - decay_n:
            env = max(0.0, (n - i) / decay_n)
        else:
            env = 1.0
        out.append(amp * env * math.sin(phase))
    return out


def tone_decay(duration_ms, f0, amp=0.5, attack_ms=2.0, decay_exp=9.0):
    """Fixed-frequency sine with an exponential decay (the soft "toc" tick)."""
    n = int(SAMPLE_RATE * duration_ms / 1000.0)
    attack_n = max(1, int(SAMPLE_RATE * attack_ms / 1000.0))
    out = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = math.exp(-decay_exp * t)
        if i < attack_n:
            env *= i / attack_n
        out.append(amp * env * math.sin(2.0 * math.pi * f0 * t))
    return out


def main():
    # focus move: very short soft tick
    write_wav("sfx_focus_move.wav", tone_decay(40, 1200, amp=0.35, decay_exp=9.0))
    # select: pleasant blip up
    write_wav("sfx_select.wav", tone_sweep(90, 880, 1320, amp=0.45))
    # back: lower blip down
    write_wav("sfx_back.wav", tone_sweep(90, 660, 440, amp=0.45))
    # panel open: quick swoosh up with fade
    write_wav("sfx_panel_open.wav", tone_sweep(180, 300, 900, amp=0.4))
    # panel close: quick swoosh down with fade
    write_wav("sfx_panel_close.wav", tone_sweep(180, 900, 300, amp=0.4))


if __name__ == "__main__":
    main()
