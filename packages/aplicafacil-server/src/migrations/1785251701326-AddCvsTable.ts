import { MigrationInterface, QueryRunner } from "typeorm";

export class AddCvsTable1785251701326 implements MigrationInterface {
    name = 'AddCvsTable1785251701326'

    public async up(queryRunner: QueryRunner): Promise<void> {
        await queryRunner.query(`CREATE TABLE "cvs" ("id" uuid NOT NULL DEFAULT uuid_generate_v4(), "filePath" character varying NOT NULL, "mimeType" character varying NOT NULL, CONSTRAINT "PK_e7d8a4d55eb4e7a2e43bea8d83a" PRIMARY KEY ("id"))`);
        await queryRunner.query(`ALTER TABLE "profiles" ADD "cvId" uuid`);
        await queryRunner.query(`ALTER TABLE "profiles" ADD CONSTRAINT "UQ_9287547c00d8e7287e143a050b3" UNIQUE ("cvId")`);
        await queryRunner.query(`ALTER TABLE "profiles" ADD CONSTRAINT "FK_9287547c00d8e7287e143a050b3" FOREIGN KEY ("cvId") REFERENCES "cvs"("id") ON DELETE NO ACTION ON UPDATE NO ACTION`);
    }

    public async down(queryRunner: QueryRunner): Promise<void> {
        await queryRunner.query(`ALTER TABLE "profiles" DROP CONSTRAINT "FK_9287547c00d8e7287e143a050b3"`);
        await queryRunner.query(`ALTER TABLE "profiles" DROP CONSTRAINT "UQ_9287547c00d8e7287e143a050b3"`);
        await queryRunner.query(`ALTER TABLE "profiles" DROP COLUMN "cvId"`);
        await queryRunner.query(`DROP TABLE "cvs"`);
    }

}
