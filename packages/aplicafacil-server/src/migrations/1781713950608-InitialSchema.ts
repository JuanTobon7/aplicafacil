import { MigrationInterface, QueryRunner } from "typeorm";

export class InitialSchema1781713950608 implements MigrationInterface {
    name = 'InitialSchema1781713950608'

    public async up(queryRunner: QueryRunner): Promise<void> {
        await queryRunner.query(`CREATE TABLE "people" ("id" uuid NOT NULL DEFAULT uuid_generate_v4(), "firstName" character varying(100) NOT NULL, "lastName" character varying(100) NOT NULL, "email" character varying NOT NULL, "phone" character varying, "linkedinUrl" character varying, "githubUrl" character varying, "portfolioUrl" character varying, "resumeUrl" character varying, "createdAt" TIMESTAMP NOT NULL DEFAULT now(), "updatedAt" TIMESTAMP NOT NULL DEFAULT now(), CONSTRAINT "UQ_c77e8752faa45901af2b245dff2" UNIQUE ("email"), CONSTRAINT "PK_aa866e71353ee94c6cc51059c5b" PRIMARY KEY ("id"))`);
        await queryRunner.query(`CREATE TABLE "experiences" ("id" uuid NOT NULL DEFAULT uuid_generate_v4(), "companyName" character varying(100) NOT NULL, "position" character varying(100) NOT NULL, "description" character varying(500), "startDate" date NOT NULL, "endDate" date, "profile_id" uuid, CONSTRAINT "PK_884f0913a63882712ea578e7c85" PRIMARY KEY ("id"))`);
        await queryRunner.query(`CREATE TABLE "projects" ("id" uuid NOT NULL DEFAULT uuid_generate_v4(), "name" character varying NOT NULL, "description" text NOT NULL, "technologies" character varying, "profileId" uuid, CONSTRAINT "PK_6271df0a7aed1d6c0691ce6ac50" PRIMARY KEY ("id"))`);
        await queryRunner.query(`CREATE TABLE "skills" ("id" uuid NOT NULL DEFAULT uuid_generate_v4(), "name" character varying(100) NOT NULL, "description" character varying(255), "yearsOfExperience" integer, "profileId" uuid, CONSTRAINT "PK_0d3212120f4ecedf90864d7e298" PRIMARY KEY ("id"))`);
        await queryRunner.query(`CREATE TABLE "profiles" ("id" uuid NOT NULL DEFAULT uuid_generate_v4(), CONSTRAINT "PK_8e520eb4da7dc01d0e190447c8e" PRIMARY KEY ("id"))`);
        await queryRunner.query(`CREATE TABLE "education" ("id" uuid NOT NULL DEFAULT uuid_generate_v4(), "institutionName" character varying(100) NOT NULL, "description" character varying(250), "startDate" date NOT NULL, "endDate" date, "profileId" uuid, CONSTRAINT "PK_bf3d38701b3030a8ad634d43bd6" PRIMARY KEY ("id"))`);
        await queryRunner.query(`ALTER TABLE "experiences" ADD CONSTRAINT "FK_358a314d189554fce6d662d042a" FOREIGN KEY ("profile_id") REFERENCES "profiles"("id") ON DELETE CASCADE ON UPDATE NO ACTION`);
        await queryRunner.query(`ALTER TABLE "projects" ADD CONSTRAINT "FK_215b7d688cce0e7093b50b2078c" FOREIGN KEY ("profileId") REFERENCES "profiles"("id") ON DELETE NO ACTION ON UPDATE NO ACTION`);
        await queryRunner.query(`ALTER TABLE "skills" ADD CONSTRAINT "FK_dd863ecfe71e180024ca9693fb7" FOREIGN KEY ("profileId") REFERENCES "profiles"("id") ON DELETE NO ACTION ON UPDATE NO ACTION`);
        await queryRunner.query(`ALTER TABLE "education" ADD CONSTRAINT "FK_d6ebf3bb8e04d86d532f4fb11c3" FOREIGN KEY ("profileId") REFERENCES "profiles"("id") ON DELETE NO ACTION ON UPDATE NO ACTION`);
    }

    public async down(queryRunner: QueryRunner): Promise<void> {
        await queryRunner.query(`ALTER TABLE "education" DROP CONSTRAINT "FK_d6ebf3bb8e04d86d532f4fb11c3"`);
        await queryRunner.query(`ALTER TABLE "skills" DROP CONSTRAINT "FK_dd863ecfe71e180024ca9693fb7"`);
        await queryRunner.query(`ALTER TABLE "projects" DROP CONSTRAINT "FK_215b7d688cce0e7093b50b2078c"`);
        await queryRunner.query(`ALTER TABLE "experiences" DROP CONSTRAINT "FK_358a314d189554fce6d662d042a"`);
        await queryRunner.query(`DROP TABLE "education"`);
        await queryRunner.query(`DROP TABLE "profiles"`);
        await queryRunner.query(`DROP TABLE "skills"`);
        await queryRunner.query(`DROP TABLE "projects"`);
        await queryRunner.query(`DROP TABLE "experiences"`);
        await queryRunner.query(`DROP TABLE "people"`);
    }

}
