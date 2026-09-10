import { Column, Entity } from "typeorm";

@Entity('experiences_vector')
export class ExperiencesVector {
    @Column({ type: 'uuid', primary: true })
    id!: string;

    @Column({ type: 'float4', array: true })
    vector!: number[];
}