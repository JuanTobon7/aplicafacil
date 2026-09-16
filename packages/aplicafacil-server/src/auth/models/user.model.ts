import { PeopleModel } from "src/people/models/people.model";
import { Column, Entity, JoinColumn, OneToOne, PrimaryGeneratedColumn } from "typeorm";

// user.model.ts
@Entity('users')
export class UserModel {
  @PrimaryGeneratedColumn('uuid')
  id!: string;

  @Column({ unique: true })
  username!: string;

  @Column()
  password!: string;

  @OneToOne(
    () => PeopleModel,
    (person) => person.user
)
  @JoinColumn()
  person!: PeopleModel;
}