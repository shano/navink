# Navink — Initial Brief

**Date:** 2026-05-19
**Owner:** Shane
**Device:** Mudita Compact (e-ink Android)
**Inspired by:** Remink (reminder app, same repo owner, same stack)

---

## What it is

A Navidrome/Subsonic music player for the Mudita Compact.
Navidrome is a self-hosted music server implementing the Subsonic REST API.

## Core features required

- Browse library: artists → albums → songs
- Search: artists, albums, songs
- Play / pause / skip / seek
- Now Playing screen (full-screen, e-ink optimised)
- Favourites: star/unstar songs, albums, artists; view starred list
- Offline mode: download albums/songs for local playback without network
- Settings: enter Navidrome server URL + credentials

## Out of scope (MVP)

- Playlists (create/edit — read-only display acceptable)
- Podcast support
- Multiple server profiles
- Scrobbling / Last.fm

## Key constraints

- E-ink display: no animations, no gradients, high contrast only, large touch targets
- Must use Mudita MMD library for UI components (see CLAUDE.md)
- Subsonic API only (no OpenSubsonic extensions needed for MVP)
- Offline downloads stored in app-private external storage

## Success criteria

- Can connect to a Navidrome instance (URL + user + password)
- Can browse and stream any song in the library
- Can star a song and find it in Favourites
- Can download an album and play it without network
- App does not crash or hang on e-ink refresh

---

*Full spec to be written by spec-writer agent before any implementation begins.*
*See CLAUDE.md for build environment, MMD component reference, and known gotchas.*
