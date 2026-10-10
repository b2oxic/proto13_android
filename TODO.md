# Android recognition prototype TODO

## Preserve the Python baseline

- [ ] Commit and push the remaining README results links and saved automated test output.
- [ ] Retain the reviewed test photos, expected labels, recognition archive, and local reference database.
- [ ] Record the baseline to beat: title panels with fuzzy lookup identified 14/15 shared photos correctly and 16/21 in the latest full batch.

## Create the Android project

- [ ] Create a separate Kotlin Android project for the Pixel 7a.
- [ ] Add a simple screen to choose an existing photo and display recognition results.
- [ ] Integrate on-device ML Kit text recognition.
- [ ] Preserve original photos and handle their orientation metadata.

## Compare recognition on the same photos

- [ ] Import the existing labeled photo set without exposing filenames to recognition or lookup.
- [ ] Run text recognition on the original image first, saving text, bounding boxes, and confidence where supplied.
- [ ] Connect recognized text to the local reference lookup, recording exact and fuzzy matches separately.
- [ ] Review missing reference entries and distinguish database coverage failures from OCR failures.
- [ ] Record per-card elapsed time, image transformation count, OCR invocation count, and lookup result.
- [ ] Score correct matches, expected unmatched results, wrong matches, and missed matches against reviewed labels.
- [ ] Export JSON and CSV plus an HTML review with expandable per-card diagnostics.
- [ ] Compare accuracy and work counts against the Python baseline on the same photos; report phone and desktop timing separately.
- [ ] Add regression checks for reviewed cases before changing cropping or retry behavior.

## Add camera capture

- [ ] Integrate CameraX preview and still capture.
- [ ] Test focus, lighting, camera distance, and glare with the Pixel mounted above the cards.
- [ ] Capture a new labeled batch and measure recognition accuracy.
- [ ] Experiment with title-region selection or cropping only if the baseline results show a need.

## Connect capture to the sorter

- [ ] Define an MQTT capture request containing a unique request ID.
- [ ] Define capture acknowledgements, errors, timeouts, and duplicate-request handling.
- [ ] Capture one photo per accepted request and upload it to the server over HTTP with the same request ID.
- [ ] Define whether recognition and lookup run on the phone or server based on the benchmark results.
- [ ] Add broker and server configuration plus authentication.
- [ ] Test reconnects, upload retries, and capture behavior while the mounted phone is in its intended operating mode.
- [ ] Test the complete capture-to-sort cycle before adding more image-processing experiments.

## Completion criteria

- [ ] Recognition accuracy is measured on the same reviewed photos used by Python.
- [ ] Every result includes enough text and diagnostics to explain a wrong or missed match.
- [ ] The capture workflow handles failures without losing requests or duplicating sorter actions.
- [ ] Setup, launch, benchmark, and export instructions are documented in the Android project.
