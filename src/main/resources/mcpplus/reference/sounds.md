# Sound guidelines

- Format: OGG Vorbis. Mono for positional sounds (blocks, mobs, items) so they attenuate with distance;
  stereo only for music and non-positional UI sounds. 44.1 kHz or 48 kHz.
- Normalize loudness to vanilla levels (peaks around -1 dB, avoid clipping); keep ambient sounds quieter.
- Provide 2-4 variations of frequent sounds (steps, hits, ambient) to avoid repetition.
- Always set a subtitle (accessibility), e.g. "Ruby Golem hurts".
- Categories: block, hostile, neutral, player, ambient, weather, record, music, voice, master.
- Name sounds by event: `entity.<mob>.ambient`, `entity.<mob>.hurt`, `entity.<mob>.death`,
  `block.<block>.break`, `item.<item>.use`.
- Convert other formats with: `ffmpeg -i input.wav -ac 1 -c:a libvorbis -q:a 5 output.ogg`
