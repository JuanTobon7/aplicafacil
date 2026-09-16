import { Column, Entity, JoinColumn, ManyToOne, OneToMany, PrimaryGeneratedColumn } from "typeorm";
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

    @ManyToOne(
        () => ProfileModel,
        (profile) => profile.experiences,
        {
            onDelete: 'CASCADE',
        },
    )
    @JoinColumn({ name: 'profile_id' })
    profile!: ProfileModel;
}