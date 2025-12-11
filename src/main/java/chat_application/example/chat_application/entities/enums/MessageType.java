package chat_application.example.chat_application.entities.enums;

public enum MessageType {

    TEXT,           // Regular text message
    IMAGE,          // Image attachment
    VIDEO,          // Video attachment
    AUDIO,          // Audio/voice clip
    FILE,           // General file
    EMBED,          // URL embed (link preview)
    STICKER,        // Sticker/emoji
    SYSTEM,         // System notification (join, leave, etc.)
    REPLY           // Reply to another message
}
