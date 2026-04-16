# Screenshots

Drop PNG captures from the Android app here using these exact filenames so the top-level `README.md` picks them up:

| Filename        | What to capture                                                    |
| --------------- | ------------------------------------------------------------------ |
| `dashboard.png` | Dashboard screen — records, level card, "Start session" button.    |
| `quiz.png`      | A question with 4 options, before selecting one.                   |
| `correct.png`   | Post-answer state after a correct choice (confetti + explanation). |
| `wrong.png`     | Post-answer state after a wrong choice (red X's + explanation).    |

Suggested resolution: 1080×2400 (Pixel 8 / Galaxy A-series default) or a 2× cropped region; keep file size under 500 KB each by exporting as PNG-8 or running through `pngquant`.

Capture on-device with **Power + Volume Down**, or from the emulator via Android Studio's *Take screenshot* button in the emulator toolbar. Pull off a physical device with:

```bash
adb exec-out screencap -p > dashboard.png
```
