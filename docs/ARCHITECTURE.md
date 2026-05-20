# Architecture - AiReplayMate

## High-level Architecture

```text
Overlay Trigger
   -> SessionManager
      -> Context Collector
         -> Accessibility Extractor
         -> OCR Fallback
      -> Prompt Builder
      -> LLM Gateway
      -> Candidate Presenter
      -> Autofill Engine
```

## Layer Description

### 1. Trigger Layer
Responsible for user initiation:
- Floating button
- Notification entry
- Quick Settings tile (future)

### 2. Context Layer
Responsible for obtaining current chat context:
- Accessibility first
- OCR as a fallback path

### 3. Intelligence Layer
Responsible for prompt generation and LLM invocation.

### 4. Presentation Layer
Responsible for candidate display and user selection.

### 5. Autofill Layer
Responsible for writing the selected text into the target input box:
- `ACTION_SET_TEXT` first
- Clipboard fallback if needed

## Design Principles

- App-specific rules should be adapter-based
- Accessibility and OCR outputs should converge to a shared model
- No auto-send in MVP
- Key steps should be traceable and diagnosable
