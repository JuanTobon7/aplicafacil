import { Inject, Injectable, Logger, OnModuleInit } from "@nestjs/common";
import { Cron, CronExpression } from '@nestjs/schedule';
import { ScrapingLinkldnService } from "../service/contract/scraping.linkldn.service";
import { JobPostingDto } from "src/jobs/dto/req/job..osting.dto";
import { LinkedInSearchParams } from "../dto/params.lindkln.search";
import { ValidateJobsService } from "../../jobs/service/contract/validate.jobs";
import { JobsService } from "src/jobs/service/contract/jobs.service";
import { JobApplicationStatus } from "src/jobs/enum/job-application-status";
import { JobAutomationHelperService } from "../service/job.automation.helper.service";
import { JobApplyerQueue } from "./job.applyer.queu";

@Injectable()
export class JobWorkerAutomation implements OnModuleInit {
    private readonly logger = new Logger(JobWorkerAutomation.name);
    params: LinkedInSearchParams;
    private readonly MAX_JOBS_TO_APPLY = 10;
    credentialsLinkdln: {email: string, password: string};

    constructor(
        @Inject('ScrapingLinkldnService') 
        private readonly scrapingLinkldnService: ScrapingLinkldnService,
        @Inject('ValidateJobsService')
        private readonly validateJobsService: ValidateJobsService,
        @Inject('JobsService')
        private readonly jobsService: JobsService,
        private readonly helper: JobAutomationHelperService,
        private readonly jobApplyerQueue: JobApplyerQueue,
    ) {
        this.params = new LinkedInSearchParams(
            '1_week', 
            'Colombia', 
            true,
        );
        this.credentialsLinkdln = this.helper.getCredentialsLinkdln();
    }

    /**
     * Se ejecuta apenas el servidor arranca: abre el navegador y dispara
     * el primer ciclo de aplicación a vacantes pendientes sin esperar al cron.
     */
    async onModuleInit(): Promise<void> {
        this.logger.log('Job automation starting on server startup...');
        try {
            await this.applyToPendingJobs();
        } catch (error) {
            this.logger.error(
                `Job automation on startup failed: ${error instanceof Error ? error.message : error}`,
            );
        }
    }

    @Cron(CronExpression.EVERY_5_MINUTES)
    async startJobAutomation(){
        this.logger.log('Starting job automation process...');

        const userId = await this.helper.getAutomationUserId();

        await this.scrapingLinkldnService.openLinkdlnProfile(this.credentialsLinkdln);

        const searchJobsToApply : JobPostingDto[] = await this.scrapingLinkldnService.getJobsToApply(this.params);
        const validJobs = await this.validateJobsService.validateJobsApplied(this.jobsService, searchJobsToApply, userId);

        const jobsToApplyLimited = this.helper.extractNumberOfJobsToApply(validJobs, this.MAX_JOBS_TO_APPLY);

        if(jobsToApplyLimited.length <= 0) {
            this.logger.log('No valid jobs found to apply for.');
            return;
        }

        this.logger.log(`Found ${jobsToApplyLimited.length} valid jobs to apply for.`);
        await this.validateJobsService.markJobsAsPending(this.jobsService, jobsToApplyLimited, userId);
        await this.scrapingLinkldnService.close();
    }

    @Cron(CronExpression.EVERY_10_MINUTES)
    async applyToPendingJobs() {
        this.logger.log('Starting job application process...');

        const pendingJobs = await this.jobsService.getJobsByStatus(JobApplicationStatus.MATCHED);

        if(pendingJobs.length <= 0) {
            this.logger.log('No pending jobs found to apply for.');
            return;
        }

        this.logger.log(`Found ${pendingJobs.length} pending jobs to apply for.`);

        // Encolar las vacantes para que el procesador las aplique una a una
        const enqueued = await this.jobApplyerQueue.enqueueJobs(
            this.helper.extractNumberOfJobsToApply(pendingJobs, this.MAX_JOBS_TO_APPLY),
        );

        this.logger.log(`Enqueued ${enqueued} jobs for application.`);
    }
}