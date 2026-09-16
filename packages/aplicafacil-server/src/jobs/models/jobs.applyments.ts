import {
  Column,
  CreateDateColumn,
  Entity,
  PrimaryGeneratedColumn,
} from 'typeorm';
import { JobSourceModel } from './job-source.model';
import { RequirementsModel } from './requirements.model';
import { SkillMatchModel } from './skill-match.model';

export class StatusApplyment {
  public static readonly DONE = 'DONE';
  public static readonly PENDING = 'PENDING';
  public static readonly NONE = 'NONE';
  public static readonly ERROR = 'ERROR';
  public static readonly CANNOT = 'CANNOT';
}

@Entity('jobs_applyments')
export class JobsApplymentsModel {

  @PrimaryGeneratedColumn('uuid')
  id!: string;

  @Column({ type: 'enum', enum: [StatusApplyment.DONE, StatusApplyment.PENDING, 
    StatusApplyment.NONE, StatusApplyment.ERROR, StatusApplyment.CANNOT], default: StatusApplyment.NONE })
  status!: StatusApplyment;

  @Column({ nullable: false })
  title!: string;

  @Column({ type: 'jsonb', nullable: true })
  match?: SkillMatchModel;

  @Column({ type: 'jsonb', nullable: false })
  source!: JobSourceModel;

  @Column({ type: 'jsonb', nullable: true })
  requirements?: RequirementsModel;

  @CreateDateColumn()
  appliedAt!: Date;
}