import {
  Entity,
  PrimaryGeneratedColumn,
  OneToMany
} from 'typeorm';
import { ExperiencesModel } from './experiences.model';
import { EducationModel } from './education.model';
import { ProjectModel } from './projects.model';
import { SkillsModel } from './skills.model';

@Entity('profiles')
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
        (educations) => educations.profile,
        {
        cascade: true,
        eager: true,
        },
    )
    educations!: EducationModel[];

    @OneToMany(
        () => ProjectModel,
        (project) => project.profile,
        {
        cascade: true,
        eager: true,
        },
    )
    projects!: ProjectModel[];
}