import { FillFormRequestDto } from "src/jobs/dto/req/fill.form.request.dto";
import { FillFormResponse } from "../impl/job.recommendation.service";

export abstract class JobRecommendationService {
    abstract fillFormFields(body: FillFormRequestDto): Promise<FillFormResponse>;
}