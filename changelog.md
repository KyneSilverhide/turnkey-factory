## Fixes

- **Lamplighter, Turret Base, and both turret weapons couldn't be mined properly.** They were missing from the pickaxe mineable tag, so no tool ever counted as "correct" for them — breaking fell back to the very slow no-tool speed (felt unbreakable) and the drop was silently skipped even once broken.
- **Lamplighter skipped placement spots covered by grass, ferns, flowers, or dead bush.** Those small plants aren't vanilla-replaceable, so any lamp post whose base landed on one was silently dropped from the build. It now crushes that vegetation (with a normal drop) instead of skipping the spot — tree canopy is still left untouched.

Builds on the terrain-obstruction fixes from 1.0.2 (Texturizer and Lamplighter no longer stall when something's sitting on the ground they're working on).
