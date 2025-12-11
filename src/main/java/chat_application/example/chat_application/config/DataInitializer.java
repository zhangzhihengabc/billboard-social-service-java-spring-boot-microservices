package chat_application.example.chat_application.config;

import chat_application.example.chat_application.entities.GroupMessageMember;
import chat_application.example.chat_application.entities.GroupMessageRoom;
import chat_application.example.chat_application.entities.User;
import chat_application.example.chat_application.entities.enums.*;
import chat_application.example.chat_application.repository.groupMessageMemberRepository;
import chat_application.example.chat_application.repository.groupMessageRoomRepository;
import chat_application.example.chat_application.repository.userRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final userRepository userRepository;
    private final groupMessageRoomRepository roomRepository;
    private final groupMessageMemberRepository memberRepository;

    @Override
    @Transactional
    public void run(String... args) {
        // Only initialize if no test users exist
        if (userRepository.existsByEmail("john.test@example.com")) {
            log.info("Test data already exists, skipping initialization...");
            printTestInstructions();
            return;
        }

        log.info("Initializing test data...");

        // ========================================
        // Create Test Users
        // ========================================
        User john = userRepository.save(User.builder()
                .name("John Doe")
                .email("john.test@example.com")
                .password("$2a$10$dummyhashedpassword123")  // Dummy hashed password
                .phoneNumber("1234567890")
                .status(UserStatus.ACTIVE)
                .userType(UserTypeEnum.NORMAL)
                .type(Type.NORMAL)
                .isCyclos(IsCyclos.NO)
                .build());

        User jane = userRepository.save(User.builder()
                .name("Jane Smith")
                .email("jane.test@example.com")
                .password("$2a$10$dummyhashedpassword456")
                .phoneNumber("0987654321")
                .status(UserStatus.ACTIVE)
                .userType(UserTypeEnum.NORMAL)
                .type(Type.NORMAL)
                .isCyclos(IsCyclos.NO)
                .build());

        User bob = userRepository.save(User.builder()
                .name("Bob Wilson")
                .email("bob.test@example.com")
                .password("$2a$10$dummyhashedpassword789")
                .phoneNumber("5555555555")
                .status(UserStatus.ACTIVE)
                .userType(UserTypeEnum.NORMAL)
                .type(Type.NORMAL)
                .isCyclos(IsCyclos.NO)
                .build());

        log.info("Created test users: john({}), jane({}), bob({})",
                john.getId(), jane.getId(), bob.getId());

        // ========================================
        // Create Test Rooms
        // ========================================
        GroupMessageRoom generalRoom = roomRepository.save(GroupMessageRoom.builder()
                .title("General Chat")
                .description("A room for general discussions")
                .owner(john)
                .roomType(RoomType.CHAT)
                .isActive(true)
                .isArchived(false)
                .maxMembers(100)
                .memberCount(3)
                .messageCount(0L)
                .build());

        GroupMessageRoom techRoom = roomRepository.save(GroupMessageRoom.builder()
                .title("Tech Talk")
                .description("Discuss technology and programming")
                .owner(jane)
                .roomType(RoomType.CHAT)
                .isActive(true)
                .isArchived(false)
                .maxMembers(50)
                .memberCount(2)
                .messageCount(0L)
                .build());

        log.info("Created test rooms: General({}), Tech({})",
                generalRoom.getId(), techRoom.getId());

        // ========================================
        // Create Room Memberships
        // ========================================

        // General Chat: john (owner), jane, bob
        memberRepository.save(GroupMessageMember.builder()
                .room(generalRoom)
                .user(john)
                .role(MemberRole.OWNER)
                .isMuted(false)
                .unreadCount(0)
                .notificationsEnabled(true)
                .build());

        memberRepository.save(GroupMessageMember.builder()
                .room(generalRoom)
                .user(jane)
                .role(MemberRole.MEMBER)
                .isMuted(false)
                .unreadCount(0)
                .notificationsEnabled(true)
                .build());

        memberRepository.save(GroupMessageMember.builder()
                .room(generalRoom)
                .user(bob)
                .role(MemberRole.MEMBER)
                .isMuted(false)
                .unreadCount(0)
                .notificationsEnabled(true)
                .build());

        // Tech Talk: jane (owner), bob
        memberRepository.save(GroupMessageMember.builder()
                .room(techRoom)
                .user(jane)
                .role(MemberRole.OWNER)
                .isMuted(false)
                .unreadCount(0)
                .notificationsEnabled(true)
                .build());

        memberRepository.save(GroupMessageMember.builder()
                .room(techRoom)
                .user(bob)
                .role(MemberRole.MEMBER)
                .isMuted(false)
                .unreadCount(0)
                .notificationsEnabled(true)
                .build());

        log.info("Created room memberships");

        printTestInstructions();
    }

    private void printTestInstructions() {
        log.info("");
        log.info("═══════════════════════════════════════════════════════════════");
        log.info("  ✅ Application Ready!");
        log.info("═══════════════════════════════════════════════════════════════");
        log.info("");
        log.info("  ⚠️  REDIS SETUP (Run in terminal):");
        log.info("    1. Start Redis: redis-server");
        log.info("    2. Enable events: redis-cli CONFIG SET notify-keyspace-events Ex");
        log.info("");
        log.info("  👤 Test Users (created with .test@example.com emails):");
        log.info("    • Check your DB for actual user IDs");
        log.info("");
        log.info("  💬 Test Rooms:");
        log.info("    • Check your DB for actual room IDs");
        log.info("");
        log.info("  🧪 Test Endpoints (port 8082):");
        log.info("    POST http://localhost:8082/api/v1/chat/connect?userId=ID&roomId=ID");
        log.info("    POST http://localhost:8082/api/v1/chat/read?userId=ID&roomId=ID");
        log.info("    POST http://localhost:8082/api/v1/chat/heartbeat?userId=ID&roomId=ID");
        log.info("");
        log.info("  📊 Check Redis:");
        log.info("    redis-cli KEYS \"chat:session:*\"");
        log.info("    redis-cli TTL \"chat:session:1\"");
        log.info("");
        log.info("  🌐 Swagger UI: http://localhost:8082/swagger-ui.html");
        log.info("═══════════════════════════════════════════════════════════════");
    }
}
