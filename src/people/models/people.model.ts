import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
} from 'typeorm';

@Entity('people')
export class PeopleModel {

  @PrimaryGeneratedColumn('uuid')
  id!: string;

  @Column({ length: 100 })
  firstName!: string;

  @Column({ length: 100 })
  lastName!: string;

  @Column({ unique: true })
  email!: string;

  @Column({ nullable: true })
  phone?: string;

  @Column({ nullable: true })
  linkedinUrl?: string;

  @Column({ nullable: true })
  githubUrl?: string;

  @Column({ nullable: true })
  portfolioUrl?: string;

  @Column({ nullable: true })
  resumeUrl?: string;

  @CreateDateColumn()
  createdAt!: Date;

  @UpdateDateColumn()
  updatedAt!: Date;
}