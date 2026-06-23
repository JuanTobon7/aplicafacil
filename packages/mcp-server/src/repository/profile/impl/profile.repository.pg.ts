import pool from "../../../db/db.pool.js";
import { Profile } from "../../../models/profile.js";
import { ProfileRepository } from "../contract/profile.repository.js";

export class PostgresProfileRepositoryImpl implements ProfileRepository {

    async save(profile: Profile): Promise<void> {
        await pool.query(
            `
            INSERT INTO profiles (
                id,
                user_id,
                full_name,
                email,
                profession,
                summary
            )
            VALUES ($1,$2,$3,$4,$5,$6)
            ON CONFLICT(id)
            DO UPDATE SET
                full_name = EXCLUDED.full_name,
                email = EXCLUDED.email,
                profession = EXCLUDED.profession,
                summary = EXCLUDED.summary
            `,
            [
                profile.id,
                profile.userId,
                profile.fullName,
                profile.email,
                profile.profession,
                profile.summary,
            ]
        );
    }

    async getById(id: string): Promise<Profile | null> {
        const result = await pool.query(
            `
            SELECT *
            FROM profiles
            WHERE id = $1
            `,
            [id]
        );

        return result.rows[0] ?? null;
    }

    async getByUserId(userId: string): Promise<Profile[]> {
        const result = await pool.query(
            `
            SELECT *
            FROM profiles
            WHERE user_id = $1
            `,
            [userId]
        );

        return result.rows;
    }
}