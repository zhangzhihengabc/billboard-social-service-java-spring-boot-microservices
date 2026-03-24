-- V5__group_team_links.sql
-- Links game groups to esports-backend teams
-- Validated via EsportsBackendClient.validateTeamMembership during link operation

CREATE TABLE group_team_links (
                                  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                  group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
                                  team_id BIGINT NOT NULL,
                                  linked_by BIGINT NOT NULL,
                                  linked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  updated_at TIMESTAMP,
                                  deleted_at TIMESTAMP,
                                  version BIGINT DEFAULT 0,
                                  CONSTRAINT uk_group_team UNIQUE (group_id, team_id)
);

CREATE INDEX idx_group_team_group ON group_team_links(group_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_group_team_team ON group_team_links(team_id) WHERE deleted_at IS NULL;
