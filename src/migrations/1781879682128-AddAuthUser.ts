import { MigrationInterface, QueryRunner } from "typeorm";

export class AddAuthUser1781879682128 implements MigrationInterface {
    name = 'AddAuthUser1781879682128'

    public async up(queryRunner: QueryRunner): Promise<void> {
        await queryRunner.query(`CREATE TABLE "users" ("id" uuid NOT NULL DEFAULT uuid_generate_v4(), "username" character varying NOT NULL, "password" character varying NOT NULL, "personId" uuid, CONSTRAINT "UQ_fe0bb3f6520ee0469504521e710" UNIQUE ("username"), CONSTRAINT "REL_ddd0d20e45dbd0d1536dc08203" UNIQUE ("personId"), CONSTRAINT "PK_a3ffb1c0c8416b9fc6f907b7433" PRIMARY KEY ("id"))`);
        await queryRunner.query(`ALTER TABLE "users" ADD CONSTRAINT "FK_ddd0d20e45dbd0d1536dc082039" FOREIGN KEY ("personId") REFERENCES "people"("id") ON DELETE NO ACTION ON UPDATE NO ACTION`);
    }

    public async down(queryRunner: QueryRunner): Promise<void> {
        await queryRunner.query(`ALTER TABLE "users" DROP CONSTRAINT "FK_ddd0d20e45dbd0d1536dc082039"`);
        await queryRunner.query(`DROP TABLE "users"`);
    }

}
