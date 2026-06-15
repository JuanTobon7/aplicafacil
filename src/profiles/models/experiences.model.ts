import { Column, Entity, OneToMany, PrimaryGeneratedColumn } from "typeorm";
import { ProfileModel } from "./profiles.model";

@Entity('experiences')
export class ExperiencesModel {
    @PrimaryGeneratedColumn('uuid')
    id!: string;

    @Column({ length: 100 })
    companyName!: string;

    @Column({ length: 100 })
    position!: string;

    @Column({ length: 500, nullable: true })
    description?: string;

    @Column({ type: 'date' })
    startDate!: Date;

    @Column({ type: 'date', nullable: true })
    endDate?: Date;

    @OneToMany(
            () => ProfileModel,
            (profile) => profile.experiences,
        )
        profile!: ProfileModel;
}