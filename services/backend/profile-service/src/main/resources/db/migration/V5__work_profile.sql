-- Perfil laboral (contracts/openapi/profile-api.yaml, WorkProfile): Backend, Frontend, Fullstack… Sin work_primary_role
-- todavía no se sabe. Los valores válidos de cada enum los decide el dominio (WorkRole, Seniority).
ALTER TABLE user_profile ADD COLUMN work_primary_role VARCHAR(32);
ALTER TABLE user_profile ADD COLUMN work_secondary_roles JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE user_profile ADD COLUMN work_seniority VARCHAR(16);
ALTER TABLE user_profile ADD COLUMN work_title VARCHAR(120);
ALTER TABLE user_profile ADD COLUMN work_source VARCHAR(8)
    CONSTRAINT ck_user_profile_work_source CHECK (work_source IN ('AI', 'MANUAL'));
ALTER TABLE user_profile ADD COLUMN work_updated_at TIMESTAMPTZ;
ALTER TABLE user_profile ADD CONSTRAINT ck_user_profile_work_complete
    CHECK ((work_primary_role IS NULL) = (work_source IS NULL));
