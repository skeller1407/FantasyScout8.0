# FantasyScout v8

## Added
- Dedicated LigaInsider provider boundary for an authorized/licensed feed or API endpoint.
- No default scraping of protected LigaInsider pages.
- LigaInsider status normalization hook for player availability.
- Centralized alert builder for favorite-player alerts.
- Android notification permission request (Android 13+).
- Local notification helper for high-value FantasyScout alerts.
- Backend alert contract is ready for a later FCM/push gateway.

## Data-source note
LigaInsider currently exposes rich player-status categories such as fit, slightly injured, questionable, injured, recovery training, suspensions and playing-chance information. FantasyScout models these as normalized status values, but the production integration should use an authorized feed/API rather than scraping protected pages.
