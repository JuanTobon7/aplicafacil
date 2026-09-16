import { Column, Entity } from "typeorm";

@Entity('education_vector')
export class EducationVector {
    @Column({ type: 'uuid', primary: true })
    id!: string;

    @Column({ type: 'float4', array: true })
    vector!: number[];
}