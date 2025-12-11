package chat_application.example.chat_application.entities.enums;

public enum MemberRole {

    OWNER,      // Room creator - full control
    ADMIN,      // Can manage members, delete messages
    MODERATOR,  // Can mute users, delete messages
    MEMBER      // Regular member - can send messages
}
