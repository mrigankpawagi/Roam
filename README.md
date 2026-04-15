<div align="center">

# 🧭 Roam

### Gamify exploration in any area you care about.

Plan an area, walk it, and watch your progress clear the map in real time.

[![Download Latest Build](https://img.shields.io/badge/Download-Latest%20Build-2563eb?style=for-the-badge)](https://nightly.link/mrigankpawagi/Roam/workflows/build.yml/main/Roam-release-APK.zip)
![Platform](https://img.shields.io/badge/Platform-Android-16a34a?style=for-the-badge)
![Maps](https://img.shields.io/badge/Maps-OpenStreetMap-f97316?style=for-the-badge)
![Status](https://img.shields.io/badge/Status-Active%20Development-9333ea?style=for-the-badge)

</div>

---

## ✨ What is Roam?

**Roam** turns exploration into a challenge for any custom area.

A great example is a **college campus**: draw your campus boundary, roam around between classes, and try to push your explored percentage higher every day.

Roam is also proudly **vibe-coded**.

---

## ✅ Features

### Area creation and editing
- Draw one or more polygons directly on the map (supports disjoint shapes)
- Search for locations to jump the map quickly before drawing
- Edit existing areas later without recreating them from scratch
- Set an exploration radius (in meters) per area

### Real-time exploration tracking
- Start/stop tracking per area
- Foreground location service keeps tracking active during exploration
- Area progress is shown as a percentage on the dashboard and in the exploration view
- Fog-of-war style map overlay clears as you explore more cells

### Import and export
- Export an area **with progress** to share your current state
- Export an area **without progress** to create fair challenges
- Import shared area files into your app
- Useful for friend groups, clubs, and campus challenges

### Eraser tool
- Enable the eraser from the main screen's overflow menu (**Enable Eraser**)
- While exploring, tap the **Eraser** button to activate eraser mode
- Tap or drag over explored cells to un-explore them, restoring the fog
- Useful for correcting accidental over-exploration or resetting specific zones

### Area management
- Quick access to edit, set radius, export, and delete from area list actions
- Deleting an area also removes its tracked progress for that area

---

## 🚀 Quick Start

1. Download the latest APK: **[Nightly Build](https://nightly.link/mrigankpawagi/Roam/workflows/build.yml/main/Roam-release-APK.zip)**
2. Install it on an Android device (allow install from unknown sources if prompted)
3. Open Roam and tap **+** to create a new area
4. Search/map-pan to your target place (for example, your college campus)
5. Draw polygon boundaries, name the area, and save
6. Open the area and press **Start** to begin tracking

---

## 🧭 Usage Walkthrough

### 1) Create your challenge area
- Tap **+** on the main screen
- Tap on map points to create polygon vertices
- Press **Close** after at least 3 points
- Add another polygon if needed (for disjoint zones)
- Enter area name and save

### 2) Start exploring
- Open the area card from the dashboard
- Grant location permission when asked
- Press **Start** to begin marking explored cells
- Keep the app/service running while walking the area

### 3) Tune difficulty
- Open area menu → **Set Exploration Radius**
- Lower radius = finer granularity, harder challenge
- Higher radius = faster progress, easier challenge

### 4) Erase explored cells
- Enable the eraser: main screen overflow menu → **Enable Eraser**
- Open an area and tap **Eraser** to enter eraser mode
- Tap or drag on the map to un-explore cells and restore the fog
- Tap **Eraser** again (or press **Stop**) to exit eraser mode

### 5) Share with friends
- Area menu → **Export**
- Choose with/without progress
- Send JSON file to friends
- Friends can import from the main screen and compete

---

## 🛠️ Built With

- Kotlin + Android SDK
- osmdroid + OpenStreetMap tiles
- Room (local persistence)
- Lifecycle ViewModel / LiveData

---

## 🤝 Contributing

PRs and ideas are welcome. If you use Roam for campus exploration challenges, share feedback and feature requests.

---

<div align="center">

### Explore your area. Track your progress. Roam more.

</div>
