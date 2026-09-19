# AI Assistant (AILLM)

A local AI assistant Android app with long-term memory capabilities. All AI processing happens on-device by default, with optional web search functionality.

## Features

### Core AI
- **On-Device LLM**: Chat with AI models running entirely on your phone
- **Long-term Memory**: AI remembers your preferences, information, and past conversations
- **Vision Support**: Analyze images with vision models
- **Image Generation**: Generate images (coming soon)

### Memory System
- **Automatic Extraction**: AI automatically learns from your conversations
- **Semantic Search**: Find memories using natural language queries
- **Categories**: Organize memories by type (Profile, Preferences, Projects, etc.)
- **Memory Management**: Edit, delete, or view all stored memories

### Chat Interface
- **Modern UI**: Clean Jetpack Compose interface with dark theme
- **Message History**: Full conversation history with timestamps
- **Image Attachments**: Send images for AI analysis
- **Real-time Streaming**: Watch AI responses appear in real-time

### File Management
- **File Import**: Import text, markdown, JSON, and other files
- **Text Extraction**: Automatically extract and summarize file contents
- **File Search**: Search through imported files

### Privacy & Security
- **100% Local**: All data stays on your device
- **No Cloud Required**: Works completely offline
- **Encrypted Storage**: Secure storage for sensitive data
- **API Key Protection**: Web search API keys stored securely

## Supported Models

### Chat Models (GGUF format)
| Model | Size | Speed | Quality |
|-------|------|-------|---------|
| Qwen2.5-0.5B | ~400MB | Very Fast | Basic |
| Qwen2.5-1.5B | ~1GB | Fast | Good |
| Qwen2.5-3B | ~2GB | Moderate | High |
| Phi-3.5 Mini | ~2.2GB | Moderate | High |
| Llama 3.2 3B | ~2GB | Moderate | High |

### Vision Models
- Qwen2.5-VL series (coming soon)

### Embedding Models
- For semantic memory search

## Architecture

```
app/
├── ai/          - AI model abstractions and implementations
│   ├── chat/    - Chat model interface
│   ├── vision/  - Vision model interface
│   ├── llm/     - llama.cpp integration
│   └── prompt/  - Prompt engineering
├── memory/      - Memory engine for long-term storage
│   ├── database/ - Room entities and DAOs
│   ├── embedding/ - Semantic search
│   └── extraction/ - Automatic memory extraction
├── chat/        - Chat UI and ViewModel
├── search/      - Web search providers
├── settings/    - App settings and model management
├── files/       - File management
└── core/        - Database, utilities, and shared components
```

## Installation

### From Source
1. Clone the repository
2. Open in Android Studio
3. Build and run on your device (API 26+)

### Model Setup
1. Open the app
2. Go to Settings > Model Management
3. Download a GGUF model (Qwen2.5 recommended)
4. Load the model when download completes

## Technical Details

### Tech Stack
- **Language**: Kotlin
- **UI**: Jetpack Compose with Material 3
- **DI**: Hilt
- **Database**: Room (SQLite)
- **AI Engine**: llama.cpp via llama-android
- **Architecture**: MVVM with Clean Architecture

### Requirements
- Android 8.0 (API 26) or higher
- 4GB+ RAM recommended
- 2GB+ free storage for models

### Build Information
- Gradle 9.1.0
- AGP 9.0.0
- Kotlin 2.2.10
- Hilt 2.59.2

## Privacy

This app is designed with privacy as a core principle:

- **No data collection**: We don't collect any user data
- **No analytics**: No tracking or analytics services
- **Local storage**: All conversations and memories stay on your device
- **Offline first**: Works completely without internet
- **Open source**: Full source code available for review

## License

MIT License - see [LICENSE](LICENSE) for details.
