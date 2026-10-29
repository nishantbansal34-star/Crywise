# CryWise

An Android app that helps new parents work out why their baby is crying.

- **Listens** with an on-phone sound model (Google's YAMNet) that recognises a baby crying, buzzes the phone and opens the check screen. Nothing is recorded or uploaded.
- **Ranks the likely reasons** from the log (time since feed, awake time, nappy), what the parent notices, the baby's age and how intense the cry sounds.
- **Checks warning signs first** and sends parents to a doctor or emergency when needed.
- **Learns the baby** — after each cry the parent taps what settled them, and similar situations get that answer ranked higher next time.
- **Logs** feeds, sleeps and nappies in one tap.

## Install
Every push to `main` builds the app on GitHub Actions and publishes `CryWise.apk` under **Releases → latest**. Open that link on the phone, download, and allow installing from your browser when Android asks.

The cry intensity rating depends on how far the phone is from the baby. CryWise is a guide, not a diagnosis.
