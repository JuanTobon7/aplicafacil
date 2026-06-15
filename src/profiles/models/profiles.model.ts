import {
  Entity,
  PrimaryGeneratedColumn,
  OneToMany
} from 'typeorm';
import { SkillsModel } from './skills.model';
import { ExperiencesModel } from './experiences.model';
import { EducationModel } from './education.model';

@Entity('people')
export class ProfileModel {
    @PrimaryGeneratedColumn('uuid')
    id!: string;

    @OneToMany(
        () => SkillsModel,
        (skill) => skill.profile,
        {
        cascade: true,
        eager: true,
        },
    )
    skills!: SkillsModel[];

    @OneToMany(
        () => ExperiencesModel,
        (experience) => experience.profile,
        {
        cascade: true,
        eager: true,
        },
    )
    experiences!: ExperiencesModel[];

    @OneToMany(
        () => EducationModel,
        (education) => education.profile,
        {
        cascade: true,
        eager: true,
        },
    )
    education!: EducationModel[];
}