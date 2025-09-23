# AURA: Mood-Based Music Journal & Player

AURA is a feature-rich, single-activity Android application that combines a local music player with a mood journaling system. It's designed with a modern, flexible architecture to provide a seamless user experience, including persistent background playback and personalized user profiles.

---

## ✨ Features

* **🎵 Full-Featured Music Player**: Enjoy local audio playback with a clean UI, complete with standard controls (play, pause, skip), a seek bar, and an optional 8D Audio effect for an immersive experience.

* **😴 Background Playback**: Music continues to play even when the app is in the background, thanks to a persistent Foreground Service.

* **🔔 Rich Media Notifications**: Control your music directly from the notification shade and lock screen, with album art and playback controls.

* **📓 Mood Journal**: Log your moods throughout the day. You can view your mood history, and long-press an entry to share, delete, or add notes.

* **👤 User Profile**: Personalize your experience by setting a custom username and profile picture, using your camera or gallery.

* **🧭 Easy Navigation**: A smooth navigation drawer allows for easy switching between the music player, your journal, and your profile.

---

## 🛠️ Technical Architecture

AURA is built on a modern single-activity, multi-fragment model to ensure a lightweight and efficient application.

* **Core Components**:

    * **`MainActivity.kt`**: The single host activity that manages the navigation drawer and fragment transactions.

    * **Fragments**: The UI is modularized into several fragments (`HomeFragment`, `ProfileFragment`, `JournalFragment`, `MusicPlayerFragment`) for better state management and separation of concerns.

    * **`MusicPlaybackService.kt`**: A foreground service that handles all media playback logic, ensuring music persists outside of the app's UI.

* **Data Management**:

    * **`SharedPreferences`**: Used for lightweight data persistence to store user profile information and mood journal entries locally on the device.

* **Permissions**:

    * The app gracefully handles runtime permissions for `POST_NOTIFICATIONS` (for media controls) and `READ_MEDIA_AUDIO` (to find local audio files) on modern Android versions.

---

## 🚧 Project Status & Future Scope

This project was developed as part of a university course. While the current version is stable and demonstrates all the core features, it is not a final product and serves as a foundation for future development. Potential future improvements could include:

* **☁️ Cloud Synchronization**: Syncing journal entries and user profiles across devices.

* **🌐 Streaming API Integration**: Integrating with services like Spotify or Apple Music.

* **🧠 Advanced Mood Analysis**: Implementing features to visualize mood patterns over time.

---

## 🚀 Getting Started

To get this project running on your own device:

1.  **Clone the repository:**

    ```
    git clone https://github.com/SridharPlays/AURA.git
    ```

2.  **Open in Android Studio:**

    * Open Android Studio.

    * Click on "Open an existing Android Studio project".

    * Navigate to the cloned repository folder and select it.

3.  **Build the project:**

    * Android Studio will automatically sync the Gradle files.

    * Once synced, you can build the project by clicking `Build > Make Project`.

4.  **Run the app:**

    * Connect an Android device or start an emulator.

    * Click the "Run" button in Android Studio.

### Adding Music to the App

For the app to recognize and play your songs, you need to place them in a specific folder.

1.  On your device's internal storage, create a folder named **`AURA_Musics`**.
2.  Copy your music files (e.g., `.mp3`, `.m4a`) into this folder.
3.  Relaunch the app. It will automatically find and load the songs from that folder.

---

*This project was developed by Sridhar N as part of the Mobile Applications course at CHRIST (Deemed to be University), Bangalore.*