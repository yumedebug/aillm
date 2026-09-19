# AI Assistant (AILLM)

A local AI assistant Android app with long-term memory capabilities. The app processes everything on-device by default, with optional web search functionality.

## Features

- **Local AI Processing**: All AI inference happens on-device
- **Long-term Memory**: The AI remembers your preferences, information, and past conversations
- **Chat Interface**: Clean, modern chat UI with message history
- **Image Support**: Attach images for analysis by vision models
- **File Memory**: Store and retrieve information from files
- **Web Search**: Optional web search with Brave/Tavily API integration
- **Model Management**: Load/unload AI models based on your needs
- **Privacy First**: All data stored locally on your device

## Architecture

```
app/
├── ai/          - AI model abstractions and implementations
├── memory/      - Memory engine for long-term storage
├── chat/        - Chat UI and ViewModel
├── search/      - Web search providers
├── settings/    - App settings
├── files/       - File management
└── core/        - Database, utilities, and shared components
```

## Supported Models

- **Chat**: Qwen series (4B-8B parameters)
- **Vision**: Qwen2.5-VL series
- **Image Generation**: Stable Diffusion / SDXL (future)

Models are not embedded in the app. You can configure which models to use based on your device capabilities.

## Installation

1. Clone the repository
2. Open in Android Studio
3. Build and run on your device

## Model Installation

Models can be downloaded through the app's Settings > Model Management section. Supported formats:

- GGUF (recommended for Android)
- SafeTensors
- ONNX

## Memory System

The AI remembers:

- Your name and preferences
- Operating system and device information
- Programming languages and tools
- Past conversations
- Images you've shared
- Files you've processed

Memories are stored locally in SQLite/Room database and can be managed through the Settings screen.

## Web Search

Web search is disabled by default. To enable:

1. Go to Settings
2. Navigate to Web Search section
3. Enable Web Search
4. Enter your API key (Brave Search or Tavily)

API keys are stored securely on your device and never sent to external servers.

## Privacy

- All conversations are stored locally
- All AI processing happens on-device
- No data is sent to external servers unless web search is enabled
- API keys are stored securely on device
- You can delete all memories at any time

## Build

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease
```

## License

MIT License - see LICENSE file for details.
