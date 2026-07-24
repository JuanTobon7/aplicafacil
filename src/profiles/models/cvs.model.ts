import {
  Column,
  Entity,
  OneToOne,
  PrimaryGeneratedColumn,
} from "typeorm";
import { ProfileModel } from "./profiles.model";

@Entity("cvs")
export class CvsModel {
  @PrimaryGeneratedColumn("uuid")
  id!: string;

  @Column()
  filePath!: string;

  @Column()
  mimeType!: string;

  @OneToOne(
    () => ProfileModel,
    (profile) => profile.cv,
  )
  profile!: ProfileModel;
}