# FantasyScout v9

- Added persistent backend state to detect real status changes.
- Added 15-minute polling scheduler (`backend/scheduler.py`).
- Added change policy: alerts are limited to favorite players and meaningful status/news changes.
- Added atomic state-file writes for safer restarts.
- Added explicit separation between new news and status-change alerts.
- Kept provider architecture: Kicker RSS, Bundesliga, Comunio, and authorized Kickbase/LigaInsider adapters.
- No scraping of protected sources is introduced by this release.

- Android WorkManager background sync every 15 minutes (when a backend URL is configured).
- Push notifications are triggered only for meaningful backend alerts.
- Backend URL remains user/configuration data; provider credentials stay server-side.
