import { MigrationInterface, QueryRunner } from "typeorm";

export class AdProfileTitle1781888063216 implements MigrationInterface {
    name = 'AdProfileTitle1781888063216'

    public async up(queryRunner: QueryRunner): Promise<void> {
        await queryRunner.query(`ALTER TABLE "profiles" ADD "title" character varying NOT NULL`);
        await queryRunner.query(`ALTER TABLE "profiles" ADD "summary" character varying NOT NULL`);
    }

    public async down(queryRunner: QueryRunner): Promise<void> {
        await queryRunner.query(`ALTER TABLE "profiles" DROP COLUMN "summary"`);
        await queryRunner.query(`ALTER TABLE "profiles" DROP COLUMN "title"`);
    }

}
