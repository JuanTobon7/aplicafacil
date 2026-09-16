import { Column, Entity } from "typeorm";

@Entity('skills_vector')
export class SkillsVector {
    @Column({ type: 'uuid', primary: true })
    id!: string;

    @Column({ type: 'float4', array: true })
    vector!: number[];
}