# Proto13 Card Recognition and Sorting System Roadmap

## Executive summary

The Android OCR/title-extraction work is complete enough to support the next phase: integrating the card scanner into the broader sorting and inventory workflow. The recognition layer has been validated with live sample data, the footer logic is tuned for real card captures, and the app is ready to be connected to external automation via MQTT.

## Current status

### Completed
- Android project stabilized and buildable
- Camera capture implemented and working
- OCR text extraction integrated using ML Kit
- Footer detection tuned from live card sample feedback
- Title extraction logic implemented using the opposite edge of the card
- Exact match lookup against the local MTG reference catalog added
- Retry logic limited to cases with no exact match
- Debug/mock scaffolding removed
- Regression tests passing

### Verified evidence
- Command run: .\gradlew :app:testDebugUnitTest --console=plain --rerun-tasks
- Result: BUILD SUCCESSFUL in 20s

## Product goal

The system will identify a card from a photo, publish the result over MQTT, and have the downstream logic route the card to the correct pile based on set criteria. The system will support both:

1. Sort-only mode
2. Inventory + sort mode

## Phase 1: Recognition layer (done)

### Deliverables
- Card photo capture
- OCR text extraction
- Footer identification
- Title extraction
- Exact match against reference data
- Retry logic for failed recognition
- Production cleanup and unit coverage

### Outcome
The OCR app is ready to act as the card-recognition service for the rest of the system.

## Phase 2: MQTT integration

### Objective
Connect the Android app to an external trigger and return result messages over MQTT.

### Tasks
- Define MQTT topic structure for:
  - trigger / capture request
  - recognition result
  - status / diagnostic messages
  - heartbeat / online status
- Implement request listener from broker
- Publish result payload to broker
- Define result schema for card recognized + confidence + retry state
- Add error and timeout handling
- Add reconnection and offline fallback behavior

### Expected outcome
The recognition app can be triggered by another system and return a structured result without requiring manual operation.

## Phase 3: Sorting orchestration

### Objective
Use the recognized card result to send the card to the correct pile via the EV3 logic.

### Tasks
- Define pile routing rules per set / card criteria
- Implement EV3 message consumption contract
- Map recognized result to sort destination
- Support sort-only path
- Support inventory + sort path
- Track counts and current inventory state

### Expected outcome
The EV3 receives a card decision and places the card in the right pile automatically.

## Phase 4: Redesign integration

### Objective
Update legacy automation to match the new feeder and camera mount design.

### Tasks
- Update feeder geometry handling
- Adjust camera alignment and capture position
- Update mechanical assumptions for the redesigned card feeder
- Verify card alignment and recognition consistency with the updated hardware
- Re-test through the full sort path after physical integration

### Expected outcome
The hardware redesign and the software pipeline are aligned and validated together.

## Phase 5: Platform migration

### Objective
Move the system from Raspberry Pi-based camera flow to a more reliable platform.

### Target state
- Current: Windows box
- Future: Linux Docker container

### Tasks
- Port the MQTT service and worker infrastructure to Windows for current operation
- Package the system for Linux/Docker deployment
- Standardize configuration and runtime environment
- Validate startup, failover, and reliability behavior

### Expected outcome
A portable deployment model that supports current testing and future production use.

## Phase 6: Monitoring and operations

### Objective
Create operational tooling for the full system.

### Tasks
- Inventory UI for queue control
- System online/offline status screen
- Diagnostics capture and storage
- Run monitoring and historical logging
- Alerting for capture failures, MQTT disconnects, and route errors
- Queue health / backlog visibility

### Expected outcome
The system can be run and monitored like a real production automation line.

## Priority order

1. MQTT trigger + result contract
2. EV3 sorting integration
3. Inventory mode implementation
4. Updated feeder and camera mount integration
5. Windows-to-Docker migration
6. Monitoring and diagnostics UI

## Risks and watchouts

- OCR false positives on non-title lines if new card styles appear
- Camera alignment changes after hardware redesign
- MQTT message contract drift between producer and consumer
- Offline / missing system states during queue processing
- Inventory inconsistencies if sort-only and sort+inventory modes are not clearly separated

## Handoff note

The recognition app is ready for integration into the broader automation pipeline. The next step is integration work, not further OCR experimentation. The core recognition problem has been solved to the point that the remaining work is operational and system orchestration.
