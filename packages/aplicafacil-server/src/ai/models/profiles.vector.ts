import { Column, Entity, ManyToOne } from "typeorm";
import { SkillsVector } from "./skills.vector";
import { ExperiencesVector } from "./experiences.vector";
import { EducationVector } from "./education.vector";

@Entity('profiles_vector')
export class ProfilesVector {
    @Column({ type: 'uuid', primary: true })
    id!: string;

    @Column({ type: 'float4', array: true })
    vector!: number[];

    @Column({ type: 'uuid' })
    userId!: string;

    @ManyToOne(
        () => SkillsVector,
        { 
            eager: true 
        }
    )
    skills!: SkillsVector

    @ManyToOne(
        () => ExperiencesVector,
        { 
            eager: true 
        }
    )
    experiences!: ExperiencesVector

    @ManyToOne(
        () => EducationVector,
        { 
            eager: true 
        }
    )
    education!: EducationVector

    @ManyToOne(
        () => SkillsVector,
        { 
            eager: true 
        }
    )
    softSkills!: SkillsVector
}