-- Cada persona pertenece a una cuenta de auth-service (sub de los tokens).
-- Sin FK: la cuenta vive en otro servicio. La tabla no tenía filas cuando se
-- introdujo esta columna; con datos existentes habría que poblarla antes del NOT NULL.
ALTER TABLE people ADD COLUMN user_id UUID;
ALTER TABLE people ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE people ADD CONSTRAINT uk_people_user_id UNIQUE (user_id);
