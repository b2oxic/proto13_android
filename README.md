# Proto13 Android

Kotlin Android prototype for Pixel 7a card capture and on-device text recognition.

## Project structure

- `app/`: Android application (to be generated with Android Studio).
- `docs/`: design, setup, and operating instructions.
- `benchmark/images/`: labeled benchmark photos.
- `benchmark/results/`: generated recognition reports (ignored by Git).
- `TODO.md`: implementation checklist.

The application is not scaffolded yet. Next, create a Kotlin Android app with Android Studio, then add ML Kit text recognition and CameraX capture.

The sibling `../proto13_ai` project preserves the Python reference implementation and archived recognition results. Its best saved approach scored 14/15 on the shared photos and 16/21 on the latest full batch. Compare the Android implementation against the same labeled photos before adding capture automation.
