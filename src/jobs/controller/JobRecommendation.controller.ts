import { Body, Controller, Post, Res } from "@nestjs/common";
import { FillFormRequestDto } from "../dto/req/FillFormRequestDto";
import { JobRecommendationService } from "../service/contract/JobRecommendaion.service";

@Controller('/recommendations')
export class JobRecommendationController {
    constructor(
        private readonly service: JobRecommendationService,
    ) {}

    @Post()
    getRecommendations(
        @Body() body: FillFormRequestDto,
        @Res() res
    ) {
        const response = this.service.fillFormFields(body);
        response.then(r => { 
            console.log("Body", body)
            console.log("Response LLM:", r)
            res.status(200).json({
                message: 'Recomendaciones generadas exitosamente',
                recommendations: r.fields
            });
        }).catch(e => console.error(e));
        
    }
}