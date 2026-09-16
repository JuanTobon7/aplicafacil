import { Column, Entity, ManyToOne, PrimaryGeneratedColumn } from "typeorm";
import { ProfileModel } from "./profiles.model";

@Entity('projects')
export class ProjectModel {

    @PrimaryGeneratedColumn('uuid')
    id!: string;

    @Column()
    name!: string;

    @Column('text')
    description!: string;

    @Column({ nullable: true })
    technologies?: string;

    @ManyToOne(
        () => ProfileModel,
        profile => profile.projects,
    )
    profile!: ProfileModel;
}