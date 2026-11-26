# GRK Digital ATOMS / GRK Immersive Audio v0.4.0

Independent Android audio project. Local 16-bit PCM WAV stereo/5.1/mapped 5.1.2 DSP downmixes to stereo with crossover, individual trims, virtual height, crossfeed and limiter. Background playback uses a foreground service. Separate local player uses native Android playback-session effects where supported. No licensed Dolby Atmos, guaranteed external app processing or physical 5.1.2 output.

## Build
GitHub Actions builds an installable debug artifact on main branch pushes. For a signed release candidate, follow RELEASE_BUILD.md. The app is not certified production-ready until real-device validation passes. Run bash tests/run-host-tests.sh for host-side regression.

The test WAV recordings are not included in this source upload; the existing release archive includes them.
