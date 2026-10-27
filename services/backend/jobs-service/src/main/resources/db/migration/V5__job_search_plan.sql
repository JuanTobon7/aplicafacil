-- Búsquedas lanzadas por la IA desde el perfil (profile.search-plan.ready): plan_id es el id de la tarea de IA.
-- Sirve para no lanzar dos veces el mismo plan si RabbitMQ entrega el evento repetido, y para marcarlas en el front.
ALTER TABLE job_search ADD COLUMN plan_id UUID;
CREATE INDEX ix_job_search_plan ON job_search (plan_id) WHERE plan_id IS NOT NULL;
