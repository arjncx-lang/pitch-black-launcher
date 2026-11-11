# Pitch Black Launcher

<p align="center">
  <img src="screenshot.png" width="30%" />
  <img src="dialog_screenshot.png" width="30%" />
  <img src="search_screenshot.png" width="30%" />
</p>

Fair warning: you probably won't like this. To most people it looks dumb, brutally empty, and basically allergic to fun. But for me, it is dumb and useful as fuck.

---

## Where this came from

I didn't plan this out. It literally came to me in a dream while I was standing at a bus stop waiting for my ride.

I was just standing there thinking about how much modern Android launchers annoy me. Microsoft Launcher feels like it wants to manage my 401k. Nova Launcher has about six hundred nested menus just to change an icon shape. And stock phone launchers are crammed with news cards, weather animations, and random garbage running in the background while chewing through battery.

So standing at that bus stop, I decided to just build what I actually wanted: pitch black, instant, and dead simple.

---

## Why put this on GitHub?

Two main reasons:

1. Free code storage for me so I don't lose it when my laptop does something stupid.
2. If you like the idea, steal it. Fork the repo, copy whatever you want, strip out what you hate, and make something neat for yourself. You don't have to ask, just take the code and build something cool.

---

## Why the features look and work like this

Every weird little thing here exists because I got annoyed by the alternative:

### 1. Pitch black background. No wallpapers.
Wallpapers look cool for two days, then they just become visual noise that hides your app icons. Plus on OLED screens, pure black pixels are literally turned off. No backlight, no battery drain, no distractions.

### 2. The nerd HUD at the top
Up top there is a small diagnostic line that shows your live charging or drain current (in mA), battery percent, voltage, device temperature, RAM, and volume.
- Why? If a cheap charging cable is giving me 200 mA instead of fast charging, or if my phone is overheating in my pocket, I want to know right away without opening some ad-riddled cleaner app.
- How it runs without eating battery: It doesn't poll in an infinite loop. It just sits there asleep and only updates when Android itself fires a system broadcast.

### 3. The mini calendar
Right above the apps, you can show 1, 2, or 3 months.
- Why? Because opening a full calendar app just to check what day of the week the 18th lands on is annoying. It is right there.
- Today is just highlighted in bright green text. No weird bloated pills, no oval blocks, no clipping. Just clean text.

### 4. Lock button and refresh right in the toolbar
- The lock icon: Reaching up to press the physical power button wears it out over time, and honestly I'm lazy. One tap locks the phone.
- The refresh button: If you sideload or install an app, tap it once and your app list refreshes immediately.

### 5. Sharp edges toggle
If you hate rounded circle icons and want everything to look like sharp little squares, there is a toggle in settings for that.

### 6. App search and work profile
Swipe up to search your apps with instant filtering. If you have work apps on your phone, swipe sideways to a separate work page so work stuff doesn't clutter your home screen.

---

## What is actually going on under the hood

To keep it simple:

- Built with modern Jetpack Compose.
- Zero background polling loops. It sleeps 59 out of 60 seconds.
- Fully minified with R8 optimization so the whole APK is around 1.7 MB.
- When you are inside an app or your phone is in your pocket, this launcher does literally nothing.

---

## How to install it

1. Grab `app-release.apk` from the Releases section (or build it yourself with Gradle).
2. Install it with adb:
   ```bash
   adb install -r app-release.apk
   ```
3. Set it as default, and you are good to go.
