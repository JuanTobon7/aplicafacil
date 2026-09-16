export class ExperienceResponseDto {
  id!: string;
  companyName!: string;
  position!: string;
  description?: string;
  startDate!: Date;
  endDate?: Date;
}