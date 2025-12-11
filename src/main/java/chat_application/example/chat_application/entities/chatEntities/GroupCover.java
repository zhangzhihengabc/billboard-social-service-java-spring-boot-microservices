package chat_application.example.chat_application.entities.chatEntities;



import chat_application.example.chat_application.entities.Group;
import chat_application.example.chat_application.entities.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "group_covers", indexes = {
    @Index(name = "idx_cover_group", columnList = "group_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupCover {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false, unique = true)
    private Group group;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    // Position for repositioning
    @Column(name = "position_x")
    @Builder.Default
    private Integer positionX = 0;

    @Column(name = "position_y")
    @Builder.Default
    private Integer positionY = 0;

    @Column(name = "scale")
    @Builder.Default
    private Double scale = 1.0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_id")
    private User uploadedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
