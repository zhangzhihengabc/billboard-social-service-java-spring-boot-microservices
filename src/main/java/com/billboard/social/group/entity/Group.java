package com.billboard.social.group.entity;
import com.billboard.social.common.entity.BaseEntity;

import com.billboard.social.group.entity.enums.GroupType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "groups", indexes = {
    @Index(name = "idx_group_slug", columnList = "slug", unique = true),
    @Index(name = "idx_group_owner", columnList = "owner_id"),
    @Index(name = "idx_group_type", columnList = "group_type"),
    @Index(name = "idx_group_category", columnList = "category_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Group extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 120)
    private String slug;

    @Column(name = "description", length = 5000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "group_type", nullable = false, length = 20)
    @Builder.Default
    private GroupType groupType = GroupType.PUBLIC;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @Column(name = "icon_url", length = 500)
    private String iconUrl;

    @Column(name = "location", length = 255)
    private String location;

    @Column(name = "website", length = 255)
    private String website;

    @Column(name = "rules", length = 10000)
    private String rules;

    @Column(name = "member_count")
    @Builder.Default
    private Integer memberCount = 1;

    @Column(name = "post_count")
    @Builder.Default
    private Integer postCount = 0;

    @Column(name = "is_verified")
    @Builder.Default
    private Boolean isVerified = false;

    @Column(name = "is_featured")
    @Builder.Default
    private Boolean isFeatured = false;

    @Column(name = "allow_member_posts")
    @Builder.Default
    private Boolean allowMemberPosts = true;

    @Column(name = "require_post_approval")
    @Builder.Default
    private Boolean requirePostApproval = false;

    @Column(name = "require_join_approval")
    @Builder.Default
    private Boolean requireJoinApproval = false;

    @Column(name = "allow_member_invites")
    @Builder.Default
    private Boolean allowMemberInvites = true;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<GroupMember> members = new HashSet<>();

    public void incrementMemberCount() {
        this.memberCount++;
    }

    public void decrementMemberCount() {
        if (this.memberCount > 0) {
            this.memberCount--;
        }
    }

    public void incrementPostCount() {
        this.postCount++;
    }

    public boolean isPublic() {
        return groupType == GroupType.PUBLIC;
    }

    public boolean requiresApproval() {
        return groupType == GroupType.CLOSED || requireJoinApproval;
    }
}
