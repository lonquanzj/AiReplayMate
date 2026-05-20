# TDD - AiReplayMate

## 1. Technical Goal

Build an Android application that supports:

- WeChat chat page recognition
- Accessibility-based chat extraction
- OCR fallback
- LLM-generated reply candidates
- Autofill into the target input field

## 2. Technical Principles

- Availability first
- Safety first
- Fallback by design
- Modular architecture
- App-target adapters
- End-to-end observability

## 3. Suggested Modules

- app
- feature-permission
- feature-overlay
- feature-session
- core-accessibility
- core-capture
- core-ocr
- core-context
- core-prompt
- core-llm
- feature-candidate
- feature-settings
- core-storage
- core-logging
- target-adapter-wechat

## 4. Core Execution Pipeline

1. TriggerCoordinator receives the user trigger
2. ReplySessionManager orchestrates the session
3. Accessibility extractor attempts to collect messages
4. OCR fallback is used if needed
5. ContextBuilder prepares structured context
6. PromptBuilder creates the LLM prompt
7. LlmGateway requests reply candidates
8. CandidatePresenter renders candidate options
9. AutofillEngine writes the selected text back

## 5. Risks

- WeChat UI structure changes
- Incomplete Accessibility node data
- OCR quality variation
- Input field autofill failure
- Device and ROM compatibility issues

## 6. Recommended MVP Stack

- Kotlin
- Jetpack Compose
- Coroutines / Flow
- Room
- DataStore
- AccessibilityService
- MediaProjection
- ML Kit OCR
- OpenAI-compatible API
