# 📸 SnapSwap – AI Powered Photo Categorization App

SnapSwap is an AI-powered Android application that automatically generates captions and categorizes images using a backend AI service. Users can select multiple photos, process them through an AI model, and generate organized outputs such as categorized groups and shareable collages.

---

## 🚀 Features

- 📂 Select multiple images from gallery  
- 🤖 AI-based image caption generation  
- 🗂 Automatic image categorization  
- 🖼 Collage generation  
- 📤 Share generated collage  
- ⚡ Image compression before upload (faster & reduced backend load)  
- 💾 Local caching using SHA-256 hashing (prevents reprocessing same images)  

---

## 🧠 How It Works

1. User selects photos.
2. Images are resized and compressed before upload.
3. A SHA-256 hash is generated for each image.
4. If a caption already exists locally → instant retrieval.
5. Otherwise → image is sent to backend AI API.
6. Caption is cached for future reuse.
7. Images are categorized and displayed in the app.

---

## 🏗 Tech Stack

### Android (Frontend)
- Kotlin
- Material Design Components
- Coroutines
- OkHttp
- SharedPreferences (Local Caching)

### Backend
- AI Image Captioning Model
- REST API (Hosted on Render)

---

## 📦 Installation

To install manually:

1. Build APK from Android Studio  
2. Share the generated `.apk` file  
3. Enable **Install from Unknown Sources**  
4. Install on Android device  

---

## 🛠 Setup & Run Locally

```bash
git clone https://github.com/Soupal-Ghosh/Android-Project-SnapSwap.git
