import {
  Entity,
  PrimaryGeneratedColumn,
  OneToMany,
  ManyToOne,
  Column
} from 'typeorm';
import { ExperiencesModel } from './experiences.model';
import { EducationModel } from './education.model';
import { ProjectModel } from './projects.model';
import { SkillsModel } from './skills.model';
import { PeopleModel } from 'src/people/models/people.model';

@Entity('profiles')
export class ProfileModel {
    @PrimaryGeneratedColumn('uuid')
    id!: string;
    @Column({ nullable: false })
    title!: string;
    @Column({ nullable: false })
    summary!: string;

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

    @ManyToOne(
        () => PeopleModel,
        (people) => people.id,
    )
    people!: PeopleModel;
}