# JIBRAN AI

A voice-first Android assistant prototype.

## First milestone
- Dark premium assistant interface with animated orb.
- Arabic (Egyptian) speech input using Android's speech recognition service.
- Spoken responses using Android Text-to-Speech.
- Initial safe actions: open YouTube/search, open Settings, open Android Internet panel, set an alarm, and adjust media volume.

## Build
GitHub Actions builds a debug APK on pushes to the `jibran-ai` branch.
Download the `JIBRAN-AI-debug` artifact from the workflow run.

## Roadmap
1. Improve command understanding and confirmation states.
2. Add supported system controls (torch, brightness, audio, connectivity panels).
3. Add contact lookup and call intents.
4. Add app discovery and app-specific integrations.
5. Evaluate a battery-conscious wake phrase service and lock-screen behavior.
6. Add optional conversational answers through a configurable AI provider.

Android permissions and lock-screen restrictions remain controlled by Android and the user.
