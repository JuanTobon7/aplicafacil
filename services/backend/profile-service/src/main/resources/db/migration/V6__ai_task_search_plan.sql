-- SEARCH_PLAN: la IA elige las búsquedas y, al terminar, se lanzan en los portales (profile.search-plan.ready).
ALTER TABLE ai_task DROP CONSTRAINT ck_ai_task_kind;
ALTER TABLE ai_task ADD CONSTRAINT ck_ai_task_kind
    CHECK (kind IN ('CV_EXTRACTION', 'SEARCH_SUGGESTION', 'SEARCH_PLAN'));
