# StudioBridge

StudioBridge is an Android app that acts as a full-featured remote control for OBS Studio, giving you complete control over your stream or recording directly from your phone.

Built for streamers, content creators, and studio environments, StudioBridge lets you manage scenes, sources, audio, and filters without needing to interact with your PC during a live session.

![Screenshort StudioBridge](./docs/StudioBridge-login)
![Screenshort StudioBridge](./docs/StudioBridge-scenes)
![Screenshort StudioBridge](./docs/StudioBridge-mode)
![Screenshort StudioBridge](./docs/StudioBridge-sources)
![Screenshort StudioBridge](./docs/StudioBridge-audiolvl)
![Screenshort StudioBridge](./docs/StudioBridge-chat)

## Features

### 🎬 Scene Control
Quickly switch between scenes while streaming or recording.

### 🧩 Source Management
Add, remove, enable, or disable sources in real time to keep your production flexible and responsive.

### 🎚️ Audio Control
Control audio levels, mute or unmute sources, and manage your sound mix remotely.

### 🎛️ Audio & Video Filters
Add, remove, and configure audio and video filters directly from your phone.  
Adjust filter values in real time to fine-tune audio effects and video appearance without interrupting your stream.

### 📡 Remote Control
Use your Android device as a reliable remote for OBS Studio, whether you're on the same network or connected remotely.

## Use Cases
- Live streaming
- Content creation
- Studio and multi-monitor setups
- Live events and recordings
- Remote stream control outside your local network

## Connectivity
StudioBridge can connect to OBS Studio in two ways:
- **Local network connection** for low-latency control
- **Remote connection** using port forwarding, allowing control from anywhere

## Requirements
- Android device
- OBS Studio with WebSocket support enabled
- Network access (local or remote via port forwarding)



## Todo

- The read chat is still work in progress its doesn't show all emote types and there are bugs
- Make the settings menu more clear as right now its just clicking on the logo 
- add text to speech for chat
- look if we can move around sources in the video preview like in real obs
- find out if we can set Twitch streamtitle and switch categories (Twitch dashboard)
- may need add twitch api and auth token 
- clean up the cookie crumbles ..