-- V1__Initial_Schema.sql
-- Feed Service Database Schema

-- Posts
CREATE TABLE posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id UUID NOT NULL,
    wall_owner_id UUID,
    group_id UUID,
    event_id UUID,
    post_type VARCHAR(20) NOT NULL DEFAULT 'STATUS',
    visibility VARCHAR(30) NOT NULL DEFAULT 'PUBLIC',
    content TEXT,
    
    -- Link preview
    link_url VARCHAR(1000),
    link_title VARCHAR(255),
    link_description VARCHAR(500),
    link_image VARCHAR(500),
    
    -- Shared post reference
    shared_post_id UUID REFERENCES posts(id),
    
    -- Settings
    is_pinned BOOLEAN DEFAULT FALSE,
    is_highlighted BOOLEAN DEFAULT FALSE,
    allow_comments BOOLEAN DEFAULT TRUE,
    allow_reactions BOOLEAN DEFAULT TRUE,
    
    -- Denormalized counts
    like_count INTEGER DEFAULT 0,
    love_count INTEGER DEFAULT 0,
    comment_count INTEGER DEFAULT 0,
    share_count INTEGER DEFAULT 0,
    view_count INTEGER DEFAULT 0,
    
    -- Additional info
    feeling VARCHAR(50),
    location VARCHAR(255),
    
    -- Scheduling
    scheduled_at TIMESTAMP,
    published_at TIMESTAMP,
    
    -- Audit
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    version BIGINT DEFAULT 0,
    
    CONSTRAINT chk_post_type CHECK (post_type IN ('STATUS', 'PHOTO', 'VIDEO', 'LINK', 'POLL', 'EVENT', 'SHARED', 'STORY')),
    CONSTRAINT chk_visibility CHECK (visibility IN ('PUBLIC', 'FRIENDS', 'FRIENDS_OF_FRIENDS', 'ONLY_ME', 'CUSTOM'))
);

CREATE INDEX idx_post_author ON posts(author_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_post_wall_owner ON posts(wall_owner_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_post_group ON posts(group_id) WHERE deleted_at IS NULL AND group_id IS NOT NULL;
CREATE INDEX idx_post_visibility ON posts(visibility) WHERE deleted_at IS NULL;
CREATE INDEX idx_post_created ON posts(created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_post_trending ON posts((like_count + love_count + comment_count * 2 + share_count * 3) DESC) 
    WHERE deleted_at IS NULL AND visibility = 'PUBLIC';

-- Post Media
CREATE TABLE post_media (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    media_type VARCHAR(20) NOT NULL,
    url VARCHAR(500) NOT NULL,
    thumbnail_url VARCHAR(500),
    width INTEGER,
    height INTEGER,
    duration_seconds INTEGER,
    file_size BIGINT,
    alt_text VARCHAR(255),
    display_order INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    version BIGINT DEFAULT 0
);

CREATE INDEX idx_post_media_post ON post_media(post_id);

-- Post Mentions
CREATE TABLE post_mentions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    mentioned_user_id UUID NOT NULL,
    position_start INTEGER,
    position_end INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    version BIGINT DEFAULT 0,
    CONSTRAINT uk_post_mention UNIQUE (post_id, mentioned_user_id)
);

CREATE INDEX idx_mention_post ON post_mentions(post_id);
CREATE INDEX idx_mention_user ON post_mentions(mentioned_user_id);

-- Post Reactions
CREATE TABLE post_reactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    reaction_type VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    version BIGINT DEFAULT 0,
    CONSTRAINT uk_post_reaction UNIQUE (post_id, user_id),
    CONSTRAINT chk_reaction_type CHECK (reaction_type IN ('LIKE', 'LOVE', 'HAHA', 'WOW', 'SAD', 'ANGRY'))
);

CREATE INDEX idx_reaction_post ON post_reactions(post_id);
CREATE INDEX idx_reaction_user ON post_reactions(user_id);

-- Comments
CREATE TABLE comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    author_id UUID NOT NULL,
    parent_id UUID REFERENCES comments(id),
    content VARCHAR(2000) NOT NULL,
    media_url VARCHAR(500),
    media_type VARCHAR(20),
    like_count INTEGER DEFAULT 0,
    reply_count INTEGER DEFAULT 0,
    is_edited BOOLEAN DEFAULT FALSE,
    is_pinned BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    version BIGINT DEFAULT 0
);

CREATE INDEX idx_comment_post ON comments(post_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_comment_author ON comments(author_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_comment_parent ON comments(parent_id) WHERE deleted_at IS NULL;

-- Comment Reactions
CREATE TABLE comment_reactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    comment_id UUID NOT NULL REFERENCES comments(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    reaction_type VARCHAR(20) NOT NULL DEFAULT 'LIKE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    version BIGINT DEFAULT 0,
    CONSTRAINT uk_comment_reaction UNIQUE (comment_id, user_id)
);

CREATE INDEX idx_comment_reaction_comment ON comment_reactions(comment_id);
CREATE INDEX idx_comment_reaction_user ON comment_reactions(user_id);

-- Hashtags (for future use)
CREATE TABLE hashtags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    post_count INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_hashtag_name ON hashtags(name);
CREATE INDEX idx_hashtag_popular ON hashtags(post_count DESC);

-- Post Hashtags (junction table)
CREATE TABLE post_hashtags (
    post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    hashtag_id UUID NOT NULL REFERENCES hashtags(id) ON DELETE CASCADE,
    PRIMARY KEY (post_id, hashtag_id)
);

CREATE INDEX idx_post_hashtag_hashtag ON post_hashtags(hashtag_id);

-- V1__Initial_Schema.sql
-- Forum Service Database Schema

-- Forums (categories/boards)
CREATE TABLE forums (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id UUID REFERENCES forums(id),
    group_id UUID,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(120) NOT NULL UNIQUE,
    description VARCHAR(500),
    forum_type VARCHAR(20) NOT NULL DEFAULT 'GENERAL',
    icon VARCHAR(100),
    color VARCHAR(7),
    display_order INTEGER DEFAULT 0,
    topic_count INTEGER DEFAULT 0,
    post_count INTEGER DEFAULT 0,
    is_locked BOOLEAN DEFAULT FALSE,
    requires_approval BOOLEAN DEFAULT FALSE,
    min_level_to_post INTEGER DEFAULT 0,
    last_topic_id UUID,
    last_post_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    version BIGINT DEFAULT 0,
    
    CONSTRAINT chk_forum_type CHECK (forum_type IN ('GENERAL', 'ANNOUNCEMENTS', 'HELP_SUPPORT', 'FEEDBACK', 'OFF_TOPIC', 'GROUP', 'PRIVATE'))
);

CREATE INDEX idx_forum_slug ON forums(slug) WHERE deleted_at IS NULL;
CREATE INDEX idx_forum_type ON forums(forum_type) WHERE deleted_at IS NULL;
CREATE INDEX idx_forum_parent ON forums(parent_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_forum_group ON forums(group_id) WHERE deleted_at IS NULL AND group_id IS NOT NULL;

-- Topics (threads)
CREATE TABLE topics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    forum_id UUID NOT NULL REFERENCES forums(id),
    author_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    slug VARCHAR(220) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    is_pinned BOOLEAN DEFAULT FALSE,
    is_sticky BOOLEAN DEFAULT FALSE,
    is_announcement BOOLEAN DEFAULT FALSE,
    is_featured BOOLEAN DEFAULT FALSE,
    reply_count INTEGER DEFAULT 0,
    view_count INTEGER DEFAULT 0,
    upvote_count INTEGER DEFAULT 0,
    downvote_count INTEGER DEFAULT 0,
    score INTEGER DEFAULT 0,
    last_post_id UUID,
    last_post_author_id UUID,
    last_post_at TIMESTAMP,
    is_edited BOOLEAN DEFAULT FALSE,
    edited_at TIMESTAMP,
    edited_by UUID,
    locked_at TIMESTAMP,
    locked_by UUID,
    lock_reason VARCHAR(500),
    tags VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    version BIGINT DEFAULT 0,
    
    CONSTRAINT chk_topic_status CHECK (status IN ('OPEN', 'CLOSED', 'LOCKED', 'ARCHIVED', 'HIDDEN'))
);

CREATE INDEX idx_topic_forum ON topics(forum_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_topic_author ON topics(author_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_topic_status ON topics(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_topic_pinned ON topics(is_pinned) WHERE deleted_at IS NULL;
CREATE INDEX idx_topic_last_post ON topics(last_post_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_topic_forum_slug ON topics(forum_id, slug) WHERE deleted_at IS NULL;
CREATE INDEX idx_topic_trending ON topics(score DESC, view_count DESC, created_at DESC) WHERE deleted_at IS NULL AND status = 'OPEN';

-- Posts (replies)
CREATE TABLE posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic_id UUID NOT NULL REFERENCES topics(id),
    author_id UUID NOT NULL,
    parent_id UUID REFERENCES posts(id),
    content TEXT NOT NULL,
    upvote_count INTEGER DEFAULT 0,
    downvote_count INTEGER DEFAULT 0,
    score INTEGER DEFAULT 0,
    reply_count INTEGER DEFAULT 0,
    is_solution BOOLEAN DEFAULT FALSE,
    is_edited BOOLEAN DEFAULT FALSE,
    edited_at TIMESTAMP,
    edited_by UUID,
    is_hidden BOOLEAN DEFAULT FALSE,
    hidden_reason VARCHAR(500),
    ip_address VARCHAR(45),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    version BIGINT DEFAULT 0
);

CREATE INDEX idx_post_topic ON posts(topic_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_post_author ON posts(author_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_post_parent ON posts(parent_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_post_created ON posts(created_at) WHERE deleted_at IS NULL;

-- Topic votes
CREATE TABLE topic_votes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic_id UUID NOT NULL REFERENCES topics(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    vote_type VARCHAR(10) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_topic_vote UNIQUE (topic_id, user_id),
    CONSTRAINT chk_topic_vote_type CHECK (vote_type IN ('UPVOTE', 'DOWNVOTE'))
);

CREATE INDEX idx_topic_vote_topic ON topic_votes(topic_id);
CREATE INDEX idx_topic_vote_user ON topic_votes(user_id);

-- Post votes
CREATE TABLE post_votes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    vote_type VARCHAR(10) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_post_vote UNIQUE (post_id, user_id),
    CONSTRAINT chk_post_vote_type CHECK (vote_type IN ('UPVOTE', 'DOWNVOTE'))
);

CREATE INDEX idx_post_vote_post ON post_votes(post_id);
CREATE INDEX idx_post_vote_user ON post_votes(user_id);

-- Topic subscriptions
CREATE TABLE topic_subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic_id UUID NOT NULL REFERENCES topics(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    notify_on_reply BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_topic_subscription UNIQUE (topic_id, user_id)
);

CREATE INDEX idx_topic_sub_topic ON topic_subscriptions(topic_id);
CREATE INDEX idx_topic_sub_user ON topic_subscriptions(user_id);

-- Add foreign key for last_topic_id in forums
ALTER TABLE forums ADD CONSTRAINT fk_forum_last_topic 
    FOREIGN KEY (last_topic_id) REFERENCES topics(id);
