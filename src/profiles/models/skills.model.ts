import { Column, Entity, OneToMany, PrimaryGeneratedColumn } from "typeorm";
import { ProfileModel } from "./profiles.model";

@Entity('skills')
export class SkillsModel {
    @PrimaryGeneratedColumn('uuid')
    id!: string;

    @Column({ length: 100 })
    name!: string;

    @Column({ length: 255, nullable: true })
    description?: string;

    @OneToMany(
        () => ProfileModel,
        (profile) => profile.skills,
    )
    profile!: ProfileModel;
}