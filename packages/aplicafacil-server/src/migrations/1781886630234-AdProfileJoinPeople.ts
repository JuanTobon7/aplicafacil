import { MigrationInterface, QueryRunner } from "typeorm";

export class AdProfileJoinPeople1781886630234 implements MigrationInterface {
    name = 'AdProfileJoinPeople1781886630234'

    public async up(queryRunner: QueryRunner): Promise<void> {
        await queryRunner.query(`ALTER TABLE "profiles" ADD "peopleId" uuid`);
        await queryRunner.query(`ALTER TABLE "profiles" ADD CONSTRAINT "FK_ff5c2711d706c96bfcf1b1b9af7" FOREIGN KEY ("peopleId") REFERENCES "people"("id") ON DELETE NO ACTION ON UPDATE NO ACTION`);
    }

    public async down(queryRunner: QueryRunner): Promise<void> {
        await queryRunner.query(`ALTER TABLE "profiles" DROP CONSTRAINT "FK_ff5c2711d706c96bfcf1b1b9af7"`);
        await queryRunner.query(`ALTER TABLE "profiles" DROP COLUMN "peopleId"`);
    }

}
