import { FillFormRequestDto } from "src/jobs/dto/req/FillFormRequestDto";
import { FillFormResponse } from "../impl/JobRecommendation.service";

export abstract class JobRecommendationService {
    abstract fillFormFields(body: FillFormRequestDto): Promise<FillFormResponse>;
}