package chat_application.example.chat_application.repository;

import chat_application.example.chat_application.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface userRepository extends JpaRepository<User, Long> {

    Optional<User> findByName(String name);
    Optional<User> findByEmail(String email);

    boolean existsByName(String name);
    boolean existsByEmail(String email);
}
