import { Inject, Injectable, Logger } from "@nestjs/common";
import { Cron, CronExpression } from '@nestjs/schedule';
import { ScrapingLinkldnService } from "../service/contract/scraping.linkldn.service";
import { JobPostingDto } from "src/jobs/dto/req/job..osting.dto";
import { LinkedInSearchParams } from "../dto/params.lindkln.search";
import { ValidateJobsService } from "../../jobs/service/contract/validate.jobs";
import { JobsService } from "src/jobs/service/contract/jobs.service";
import { JobsApplymentsModel, StatusApplyment } from "src/jobs/models/jobs.applyments";

@Injectable()
export class JobWorkerAutomation {
    private readonly logger = new Logger(JobWorkerAutomation.name);
    params: LinkedInSearchParams;
    private readonly MAX_JOBS_TO_APPLY = 10;
    credentialsLinkdln: {email: string, password: string} = this.getCredentialsLinkdln();

    constructor(
        @Inject('ScrapingLinkldnService') 
        private readonly scrapingLinkldnService: ScrapingLinkldnService,
        @Inject('ValidateJobsService')
        private readonly validateJobsService: ValidateJobsService,
        @Inject('JobsService')
        private readonly jobsService: JobsService

    ) {
        this.params = new LinkedInSearchParams(
            '1_week', 
            'Colombia', 
            true,
        );
    }

    @Cron(CronExpression.EVERY_5_MINUTES)
    async startJobAutomation(){
        this.logger.log('Starting job automation process...');

        await this.scrapingLinkldnService.openLinkdlnProfile(this.credentialsLinkdln);

        const searchJobsToApply : JobPostingDto[] = await this.scrapingLinkldnService.getJobsToApply(this.params);
        const validJobs = await this.validateJobsService.validateJobsApplied(this.jobsService, searchJobsToApply);

        const jobsToApplyLimited = this.extractNumberOfJobsToApply(validJobs);

        if(jobsToApplyLimited.length <= 0) {
            this.logger.log('No valid jobs found to apply for.');
            return;
        }

        this.logger.log(`Found ${jobsToApplyLimited.length} valid jobs to apply for.`);
        await this.validateJobsService.markJobsAsPending(this.jobsService, jobsToApplyLimited);
    }

    @Cron(CronExpression.EVERY_10_MINUTES)
    async applyToPendingJobs() {
        this.logger.log('Starting job application process...');

        await this.scrapingLinkldnService.openLinkdlnProfile(this.credentialsLinkdln);

        const getPendingJobs = await this.jobsService.getJobsByStatus(StatusApplyment.PENDING);

        if(getPendingJobs.length <= 0) {
            this.logger.log('No pending jobs found to apply for.');
            return;
        }

        this.logger.log(`Found ${getPendingJobs.length} pending jobs to apply for.`);
    }

    getCredentialsLinkdln(): {email: string, password: string} {
        const email = process.env.LINKEDIN_EMAIL;
        const password = process.env.LINKEDIN_PASSWORD;
        
        if(!email || !password) {
            throw new Error('LinkedIn credentials are not set in environment variables.');
        }

        return { email, password };
    }

    extractNumberOfJobsToApply(jobs: JobPostingDto[], maxJobs: number = this.MAX_JOBS_TO_APPLY): JobPostingDto[] {
        if(jobs.length > maxJobs) {
            return jobs.slice(0, maxJobs);
        }
        return jobs;
    }

    sendApplicationToQueu(jobs: JobsApplymentsModel[]): Promise<void> {
        // Implementation for applying to a specific job



        return Promise.resolve();
    }
}