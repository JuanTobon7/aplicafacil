import { Column, Entity, ManyToOne, PrimaryGeneratedColumn } from "typeorm";
import { ProfileModel } from "./profiles.model";

@Entity('education')
export class EducationModel {
    @PrimaryGeneratedColumn('uuid')
    id!: string;

    @Column({ length: 100, nullable: false })
    institutionName!: string;

    @Column({ length: 250, nullable: true })
    description!: string;

    startDate!: Date;

    endDate?: Date;

    @ManyToOne(
        () => ProfileModel,
        (profile) => profile.education,
    )
    profile!: ProfileModel;
}