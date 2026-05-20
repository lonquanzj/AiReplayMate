# PRD - AiReplayMate

## 1. Product Overview

AiReplayMate is an Android AI-assisted reply tool for chat scenarios. It is designed to:

1. Read the current chat context
2. Generate AI reply candidates
3. Autofill the selected reply into the input box
4. Keep the user in control of sending

## 2. Core Positioning

- Assistance, not full auto-chat
- Semi-automatic, not unattended operation
- Safety first, no auto-send by default
- WeChat one-to-one chat as the MVP entry point

## 3. Target Users

- Business communication users
- Customer support and operations staff
- High-frequency chat users
- Users who want faster and better replies

## 4. MVP Scope

- WeChat one-to-one chat page
- Floating trigger button
- Accessibility-based message extraction
- OCR fallback
- LLM generation of 3 reply candidates
- Autofill into input box
- Settings page
- Basic logging and diagnostics

## 5. Core Flow

1. User taps the floating trigger on a chat page
2. The app tries to read chat content via AccessibilityService
3. If extraction fails, it falls back to screenshot + OCR
4. Recent chat context is sent to the LLM
5. The LLM returns 3 candidate replies
6. The user selects one reply
7. The app autofills the reply into the input box
8. The user manually reviews and sends it

## 6. Non-goals

- Auto-send
- Complex group chat understanding in MVP
- Root / Hook / Injection
- Accessing private app protocols
- Unattended auto-chatting

## 7. Success Criteria

- Reliable triggering in supported chat pages
- Stable chat context extraction
- Useful candidate replies
- Safe autofill with user confirmation
- Low-friction user experience
