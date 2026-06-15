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
        }).catch(e => console.error(e));
        res.status(200).json({
            message: 'Recomendaciones generadas exitosamente',
            recommendations: [
                {
                    id: 1,
                    title: 'Software Engineer',
                    company: 'Tech Corp',
                    location: 'San Francisco, CA'
                },
                {
                    id: 2,
                    title: 'Product Manager',
                    company: 'Innovate Inc',
                    location: 'New York, NY'
                }
            ]
        });
    }
}