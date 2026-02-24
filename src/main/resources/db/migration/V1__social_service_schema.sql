-- V1__social_service_schema.sql
-- Merged from: social-graph-service, group-service, event-service
-- User IDs are BIGINT to match SSO-service

-- ======================
-- SOCIAL GRAPH TABLES
-- ======================

CREATE TABLE friendships (
                             id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                             requester_id BIGINT NOT NULL,
                             addressee_id BIGINT NOT NULL,
                             status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                             message VARCHAR(500),
                             mutual_friends_count INTEGER DEFAULT 0,
                             accepted_at TIMESTAMP,
                             created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             updated_at TIMESTAMP,
                             deleted_at TIMESTAMP,
                             version BIGINT DEFAULT 0,
                             CONSTRAINT uk_friendship_users UNIQUE (requester_id, addressee_id),
                             CONSTRAINT chk_friendship_status CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'BLOCKED', 'CANCELLED'))
);

CREATE INDEX idx_friendship_requester ON friendships(requester_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_friendship_addressee ON friendships(addressee_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_friendship_status ON friendships(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_friendship_accepted ON friendships(requester_id, addressee_id) WHERE status = 'ACCEPTED' AND deleted_at IS NULL;

CREATE TABLE follows (
                         id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                         follower_id BIGINT NOT NULL,
                         following_id BIGINT NOT NULL,
                         notifications_enabled BOOLEAN DEFAULT TRUE,
                         is_close_friend BOOLEAN DEFAULT FALSE,
                         is_muted BOOLEAN DEFAULT FALSE,
                         created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at TIMESTAMP,
                         deleted_at TIMESTAMP,
                         version BIGINT DEFAULT 0,
                         CONSTRAINT uk_follow_users UNIQUE (follower_id, following_id)
);

CREATE INDEX idx_follow_follower ON follows(follower_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_follow_following ON follows(following_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_follow_close_friends ON follows(follower_id) WHERE is_close_friend = TRUE AND deleted_at IS NULL;

CREATE TABLE blocks (
                        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        blocker_id BIGINT NOT NULL,
                        blocked_id BIGINT NOT NULL,
                        reason VARCHAR(500),
                        hide_from_suggestions BOOLEAN DEFAULT TRUE,
                        block_messages BOOLEAN DEFAULT TRUE,
                        block_posts BOOLEAN DEFAULT TRUE,
                        block_comments BOOLEAN DEFAULT TRUE,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP,
                        deleted_at TIMESTAMP,
                        version BIGINT DEFAULT 0,
                        CONSTRAINT uk_block_users UNIQUE (blocker_id, blocked_id)
);

CREATE INDEX idx_block_blocker ON blocks(blocker_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_block_blocked ON blocks(blocked_id) WHERE deleted_at IS NULL;

CREATE TABLE reactions (
                           id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                           user_id BIGINT NOT NULL,
                           content_type VARCHAR(20) NOT NULL,
                           content_id UUID NOT NULL,
                           content_owner_id BIGINT,
                           reaction_type VARCHAR(20) NOT NULL DEFAULT 'LIKE',
                           created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           updated_at TIMESTAMP,
                           deleted_at TIMESTAMP,
                           version BIGINT DEFAULT 0,
                           CONSTRAINT uk_reaction_user_content UNIQUE (user_id, content_type, content_id),
                           CONSTRAINT chk_content_type CHECK (content_type IN ('POST', 'COMMENT', 'PHOTO', 'VIDEO', 'STORY', 'POLL', 'EVENT', 'GROUP', 'PAGE')),
                           CONSTRAINT chk_reaction_type CHECK (reaction_type IN ('LIKE', 'LOVE', 'HAHA', 'WOW', 'SAD', 'ANGRY'))
);

CREATE INDEX idx_reaction_user ON reactions(user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_reaction_content ON reactions(content_type, content_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_reaction_type ON reactions(reaction_type) WHERE deleted_at IS NULL;
CREATE INDEX idx_reaction_owner ON reactions(content_owner_id) WHERE deleted_at IS NULL;

CREATE TABLE shares (
                        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        user_id BIGINT NOT NULL,
                        content_type VARCHAR(20) NOT NULL,
                        content_id UUID NOT NULL,
                        content_owner_id BIGINT,
                        target_user_id BIGINT,
                        message VARCHAR(1000),
                        share_to_feed BOOLEAN DEFAULT TRUE,
                        share_to_story BOOLEAN DEFAULT FALSE,
                        is_private_share BOOLEAN DEFAULT FALSE,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP,
                        deleted_at TIMESTAMP,
                        version BIGINT DEFAULT 0,
                        CONSTRAINT chk_share_content_type CHECK (content_type IN ('POST', 'COMMENT', 'PHOTO', 'VIDEO', 'STORY', 'POLL', 'EVENT', 'GROUP', 'PAGE'))
);

CREATE INDEX idx_share_user ON shares(user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_share_content ON shares(content_type, content_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_share_target ON shares(target_user_id) WHERE deleted_at IS NULL AND is_private_share = TRUE;

CREATE TABLE pokes (
                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       poker_id BIGINT NOT NULL,
                       poked_id BIGINT NOT NULL,
                       is_active BOOLEAN DEFAULT TRUE,
                       poked_back_at TIMESTAMP,
                       poke_count INTEGER DEFAULT 1,
                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP,
                       deleted_at TIMESTAMP,
                       version BIGINT DEFAULT 0
);

CREATE INDEX idx_poke_poker ON pokes(poker_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_poke_poked ON pokes(poked_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_poke_active ON pokes(poked_id) WHERE is_active = TRUE AND deleted_at IS NULL;

CREATE TABLE invitations (
                             id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                             inviter_id BIGINT NOT NULL,
                             invitee_id BIGINT,
                             invitee_email VARCHAR(255),
                             invitation_type VARCHAR(20) NOT NULL,
                             target_id UUID,
                             message VARCHAR(500),
                             status VARCHAR(20) DEFAULT 'PENDING',
                             invite_code VARCHAR(50),
                             expires_at TIMESTAMP,
                             accepted_at TIMESTAMP,
                             declined_at TIMESTAMP,
                             created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             updated_at TIMESTAMP,
                             deleted_at TIMESTAMP,
                             version BIGINT DEFAULT 0,
                             CONSTRAINT uk_invitation UNIQUE (inviter_id, invitee_id, invitation_type, target_id),
                             CONSTRAINT chk_invitation_type CHECK (invitation_type IN ('FRIEND', 'GROUP', 'EVENT', 'PAGE', 'APP'))
);

CREATE INDEX idx_invitation_inviter ON invitations(inviter_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_invitation_invitee ON invitations(invitee_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_invitation_type ON invitations(invitation_type) WHERE deleted_at IS NULL;
CREATE INDEX idx_invitation_status ON invitations(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_invitation_code ON invitations(invite_code) WHERE invite_code IS NOT NULL;

CREATE TABLE relationship_suggestions (
                                          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                          user_id BIGINT NOT NULL,
                                          suggested_user_id BIGINT NOT NULL,
                                          score DECIMAL(5,2) DEFAULT 0,
                                          mutual_friends_count INTEGER DEFAULT 0,
                                          mutual_groups_count INTEGER DEFAULT 0,
                                          is_dismissed BOOLEAN DEFAULT FALSE,
                                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                          updated_at TIMESTAMP,
                                          expires_at TIMESTAMP,
                                          CONSTRAINT uk_suggestion UNIQUE (user_id, suggested_user_id)
);

CREATE INDEX idx_suggestion_user ON relationship_suggestions(user_id) WHERE is_dismissed = FALSE;
CREATE INDEX idx_suggestion_score ON relationship_suggestions(user_id, score DESC) WHERE is_dismissed = FALSE;

-- ======================
-- GROUP TABLES
-- ======================

CREATE TABLE group_categories (
                                  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                  name VARCHAR(100) NOT NULL,
                                  slug VARCHAR(120) NOT NULL UNIQUE,
                                  description VARCHAR(500),
                                  icon VARCHAR(50),
                                  parent_id UUID REFERENCES group_categories(id),
                                  display_order INTEGER DEFAULT 0,
                                  group_count INTEGER DEFAULT 0,
                                  is_active BOOLEAN DEFAULT TRUE,
                                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  updated_at TIMESTAMP,
                                  deleted_at TIMESTAMP,
                                  version BIGINT DEFAULT 0
);

CREATE INDEX idx_group_category_slug ON group_categories(slug);
CREATE INDEX idx_group_category_parent ON group_categories(parent_id);

CREATE TABLE groups (
                        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        name VARCHAR(100) NOT NULL,
                        slug VARCHAR(120) NOT NULL UNIQUE,
                        description VARCHAR(5000),
                        group_type VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
                        owner_id BIGINT NOT NULL,
                        category_id UUID REFERENCES group_categories(id),
                        cover_image_url VARCHAR(500),
                        icon_url VARCHAR(500),
                        location VARCHAR(255),
                        website VARCHAR(255),
                        rules VARCHAR(10000),
                        member_count INTEGER DEFAULT 1,
                        post_count INTEGER DEFAULT 0,
                        is_verified BOOLEAN DEFAULT FALSE,
                        is_featured BOOLEAN DEFAULT FALSE,
                        allow_member_posts BOOLEAN DEFAULT TRUE,
                        require_post_approval BOOLEAN DEFAULT FALSE,
                        require_join_approval BOOLEAN DEFAULT FALSE,
                        allow_member_invites BOOLEAN DEFAULT TRUE,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP,
                        deleted_at TIMESTAMP,
                        version BIGINT DEFAULT 0,
                        CONSTRAINT chk_group_type CHECK (group_type IN ('PUBLIC', 'CLOSED', 'PRIVATE', 'SECRET'))
);

CREATE INDEX idx_group_slug ON groups(slug) WHERE deleted_at IS NULL;
CREATE INDEX idx_group_owner ON groups(owner_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_group_type ON groups(group_type) WHERE deleted_at IS NULL;
CREATE INDEX idx_group_category ON groups(category_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_group_featured ON groups(is_featured) WHERE deleted_at IS NULL AND is_featured = TRUE;
CREATE INDEX idx_group_popular ON groups(member_count DESC) WHERE deleted_at IS NULL;

CREATE TABLE group_members (
                               id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
                               user_id BIGINT NOT NULL,
                               role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
                               status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                               invited_by BIGINT,
                               approved_by BIGINT,
                               approved_at TIMESTAMP,
                               joined_at TIMESTAMP,
                               muted_until TIMESTAMP,
                               notifications_enabled BOOLEAN DEFAULT TRUE,
                               post_count INTEGER DEFAULT 0,
                               contribution_score INTEGER DEFAULT 0,
                               created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               updated_at TIMESTAMP,
                               deleted_at TIMESTAMP,
                               version BIGINT DEFAULT 0,
                               CONSTRAINT uk_group_member UNIQUE (group_id, user_id),
                               CONSTRAINT chk_member_role CHECK (role IN ('MEMBER', 'MODERATOR', 'ADMIN', 'OWNER')),
                               CONSTRAINT chk_member_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'BANNED', 'LEFT', 'INVITED'))
);

CREATE INDEX idx_group_member_group ON group_members(group_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_group_member_user ON group_members(user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_group_member_status ON group_members(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_group_member_role ON group_members(role) WHERE deleted_at IS NULL;
CREATE INDEX idx_group_member_approved ON group_members(group_id) WHERE status = 'APPROVED' AND deleted_at IS NULL;

CREATE TABLE group_invitations (
                                   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                   group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
                                   inviter_id BIGINT NOT NULL,
                                   invitee_id BIGINT,
                                   invitee_email VARCHAR(255),
                                   message VARCHAR(500),
                                   status VARCHAR(20) DEFAULT 'PENDING',
                                   invite_code VARCHAR(50),
                                   expires_at TIMESTAMP,
                                   accepted_at TIMESTAMP,
                                   declined_at TIMESTAMP,
                                   created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   updated_at TIMESTAMP,
                                   deleted_at TIMESTAMP,
                                   version BIGINT DEFAULT 0,
                                   CONSTRAINT uk_group_invitation UNIQUE (group_id, invitee_id)
);

CREATE INDEX idx_group_inv_group ON group_invitations(group_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_group_inv_invitee ON group_invitations(invitee_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_group_inv_status ON group_invitations(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_group_inv_code ON group_invitations(invite_code) WHERE invite_code IS NOT NULL;

CREATE TABLE group_posts (
                             id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                             group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
                             user_id BIGINT NOT NULL,
                             content TEXT,
                             is_pinned BOOLEAN DEFAULT FALSE,
                             is_announcement BOOLEAN DEFAULT FALSE,
                             is_approved BOOLEAN DEFAULT TRUE,
                             approved_by BIGINT,
                             approved_at TIMESTAMP,
                             like_count INTEGER DEFAULT 0,
                             comment_count INTEGER DEFAULT 0,
                             share_count INTEGER DEFAULT 0,
                             created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             updated_at TIMESTAMP,
                             deleted_at TIMESTAMP,
                             version BIGINT DEFAULT 0
);

CREATE INDEX idx_group_post_group ON group_posts(group_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_group_post_user ON group_posts(user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_group_post_pinned ON group_posts(group_id) WHERE is_pinned = TRUE AND deleted_at IS NULL;

INSERT INTO group_categories (id, name, slug, description, icon, display_order) VALUES
                                                                                    (gen_random_uuid(), 'Gaming', 'gaming', 'Gaming communities and discussions', 'gamepad', 1),
                                                                                    (gen_random_uuid(), 'Sports', 'sports', 'Sports fans and activities', 'trophy', 2),
                                                                                    (gen_random_uuid(), 'Technology', 'technology', 'Tech enthusiasts and discussions', 'laptop', 3),
                                                                                    (gen_random_uuid(), 'Music', 'music', 'Music lovers and artists', 'music', 4),
                                                                                    (gen_random_uuid(), 'Art', 'art', 'Artists and art appreciation', 'palette', 5),
                                                                                    (gen_random_uuid(), 'Food', 'food', 'Food lovers and recipes', 'utensils', 6),
                                                                                    (gen_random_uuid(), 'Travel', 'travel', 'Travel experiences and tips', 'plane', 7),
                                                                                    (gen_random_uuid(), 'Education', 'education', 'Learning and knowledge sharing', 'book', 8),
                                                                                    (gen_random_uuid(), 'Business', 'business', 'Business networking and discussions', 'briefcase', 9),
                                                                                    (gen_random_uuid(), 'Entertainment', 'entertainment', 'Movies, TV, and entertainment', 'film', 10);

-- ======================
-- EVENT TABLES
-- ======================

CREATE TABLE event_categories (
                                  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                  name VARCHAR(100) NOT NULL,
                                  slug VARCHAR(120) NOT NULL UNIQUE,
                                  description VARCHAR(500),
                                  icon VARCHAR(50),
                                  color VARCHAR(20),
                                  display_order INTEGER DEFAULT 0,
                                  event_count INTEGER DEFAULT 0,
                                  is_active BOOLEAN DEFAULT TRUE,
                                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  updated_at TIMESTAMP,
                                  deleted_at TIMESTAMP,
                                  version BIGINT DEFAULT 0
);

CREATE TABLE events (
                        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        title VARCHAR(200) NOT NULL,
                        slug VARCHAR(250) NOT NULL UNIQUE,
                        description TEXT,
                        host_id BIGINT NOT NULL,
                        group_id UUID,
                        category_id UUID REFERENCES event_categories(id),
                        event_type VARCHAR(20) NOT NULL DEFAULT 'IN_PERSON',
                        visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
                        status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
                        cover_image_url VARCHAR(500),
                        start_time TIMESTAMP NOT NULL,
                        end_time TIMESTAMP,
                        timezone VARCHAR(50) DEFAULT 'UTC',
                        is_all_day BOOLEAN DEFAULT FALSE,
                        venue_name VARCHAR(200),
                        address VARCHAR(500),
                        city VARCHAR(100),
                        country VARCHAR(100),
                        latitude DOUBLE PRECISION,
                        longitude DOUBLE PRECISION,
                        online_url VARCHAR(500),
                        online_platform VARCHAR(50),
                        max_attendees INTEGER,
                        going_count INTEGER DEFAULT 0,
                        maybe_count INTEGER DEFAULT 0,
                        invited_count INTEGER DEFAULT 0,
                        is_ticketed BOOLEAN DEFAULT FALSE,
                        ticket_price DECIMAL(10,2),
                        ticket_currency VARCHAR(3),
                        tickets_sold INTEGER DEFAULT 0,
                        recurrence_type VARCHAR(20) DEFAULT 'NONE',
                        recurrence_end_date TIMESTAMP,
                        parent_event_id UUID REFERENCES events(id),
                        allow_guests BOOLEAN DEFAULT TRUE,
                        guests_per_rsvp INTEGER DEFAULT 1,
                        show_guest_list BOOLEAN DEFAULT TRUE,
                        allow_comments BOOLEAN DEFAULT TRUE,
                        require_approval BOOLEAN DEFAULT FALSE,
                        accepting_rsvps BOOLEAN DEFAULT TRUE,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP,
                        deleted_at TIMESTAMP,
                        version BIGINT DEFAULT 0
);

CREATE INDEX idx_event_host ON events(host_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_event_start ON events(start_time) WHERE deleted_at IS NULL;
CREATE INDEX idx_event_status ON events(status) WHERE deleted_at IS NULL;

CREATE TABLE event_rsvps (
                             id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                             event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
                             user_id BIGINT NOT NULL,
                             status VARCHAR(20) NOT NULL DEFAULT 'INVITED',
                             guest_count INTEGER DEFAULT 0,
                             note VARCHAR(500),
                             invited_by BIGINT,
                             responded_at TIMESTAMP,
                             checked_in_at TIMESTAMP,
                             notifications_enabled BOOLEAN DEFAULT TRUE,
                             created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             updated_at TIMESTAMP,
                             deleted_at TIMESTAMP,
                             version BIGINT DEFAULT 0,
                             CONSTRAINT uk_event_rsvp UNIQUE (event_id, user_id)
);

CREATE INDEX idx_event_rsvp_event ON event_rsvps(event_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_event_rsvp_user ON event_rsvps(user_id) WHERE deleted_at IS NULL;

CREATE TABLE event_cohosts (
                               id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
                               user_id BIGINT NOT NULL,
                               can_edit BOOLEAN DEFAULT TRUE,
                               can_invite BOOLEAN DEFAULT TRUE,
                               can_manage_rsvps BOOLEAN DEFAULT TRUE,
                               created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               updated_at TIMESTAMP,
                               deleted_at TIMESTAMP,
                               version BIGINT DEFAULT 0,
                               CONSTRAINT uk_event_cohost UNIQUE (event_id, user_id)
);

CREATE TABLE event_attendees (
                                 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                 event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
                                 user_id BIGINT NOT NULL,
                                 rsvp_status VARCHAR(20) NOT NULL DEFAULT 'INVITED',
                                 rsvp_at TIMESTAMP,
                                 checked_in_at TIMESTAMP,
                                 guest_count INTEGER DEFAULT 0,
                                 is_host BOOLEAN DEFAULT FALSE,
                                 is_co_host BOOLEAN DEFAULT FALSE,
                                 note VARCHAR(500),
                                 invited_by BIGINT,
                                 invited_at TIMESTAMP,
                                 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 updated_at TIMESTAMP,
                                 deleted_at TIMESTAMP,
                                 version BIGINT DEFAULT 0,
                                 CONSTRAINT uk_event_attendee UNIQUE (event_id, user_id)
);

CREATE INDEX idx_attendee_event ON event_attendees(event_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_attendee_user ON event_attendees(user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_attendee_status ON event_attendees(rsvp_status) WHERE deleted_at IS NULL;

CREATE TABLE event_tickets (
                               id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
                               user_id BIGINT NOT NULL,
                               ticket_code VARCHAR(50) NOT NULL UNIQUE,
                               ticket_type VARCHAR(50) DEFAULT 'GENERAL',
                               price DECIMAL(10,2),
                               currency VARCHAR(3),
                               quantity INTEGER DEFAULT 1,
                               status VARCHAR(20) DEFAULT 'VALID',
                               purchased_at TIMESTAMP,
                               used_at TIMESTAMP,
                               qr_code_url VARCHAR(500),
                               payment_reference VARCHAR(100),
                               created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               updated_at TIMESTAMP,
                               deleted_at TIMESTAMP,
                               version BIGINT DEFAULT 0
);

INSERT INTO event_categories (name, slug, description, icon, color, display_order) VALUES
                                                                                       ('Music', 'music', 'Concerts and live performances', 'music', '#FF6B6B', 1),
                                                                                       ('Sports', 'sports', 'Sports events and activities', 'trophy', '#4ECDC4', 2),
                                                                                       ('Arts', 'arts', 'Art exhibitions and cultural events', 'palette', '#45B7D1', 3),
                                                                                       ('Business', 'business', 'Networking and professional events', 'briefcase', '#96CEB4', 4),
                                                                                       ('Food & Drink', 'food-drink', 'Food festivals and tastings', 'utensils', '#FFEAA7', 5),
                                                                                       ('Community', 'community', 'Community gatherings and meetups', 'users', '#DDA0DD', 6),
                                                                                       ('Education', 'education', 'Workshops and learning events', 'book', '#98D8C8', 7),
                                                                                       ('Technology', 'technology', 'Tech meetups and conferences', 'laptop', '#F7DC6F', 8),
                                                                                       ('Health', 'health', 'Wellness and fitness events', 'heart', '#E74C3C', 9),
                                                                                       ('Gaming', 'gaming', 'Gaming tournaments and meetups', 'gamepad', '#9B59B6', 10);