import { MigrationInterface, QueryRunner } from "typeorm";

/**
 * Cambia la columna `url` de la tabla `jobs` de varchar(500) a text.
 *
 * Las URLs de LinkedIn con parámetros de tracking (eBP, refId, trackingId,
 * trk, ...) superan fácilmente los 500 caracteres y provocaban el error
 * `value too long for type character varying(500)` al guardar vacantes.
 */
export class AlterJobsUrlToText1789344000000 implements MigrationInterface {
    name = 'AlterJobsUrlToText1789344000000'

    public async up(queryRunner: QueryRunner): Promise<void> {
        await queryRunner.query(`ALTER TABLE "jobs" ALTER COLUMN "url" TYPE text`);
    }

    public async down(queryRunner: QueryRunner): Promise<void> {
        await queryRunner.query(`ALTER TABLE "jobs" ALTER COLUMN "url" TYPE character varying(500)`);
    }

}