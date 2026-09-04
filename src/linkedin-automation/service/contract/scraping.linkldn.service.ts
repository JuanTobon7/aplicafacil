import { JobPostingDto } from "src/jobs/dto/req/job..osting.dto";
import { LinkedInSearchParams } from "src/linkedin-automation/dto/params.lindkln.search";

export abstract class ScrapingLinkldnService {
    abstract openLinkdlnProfile({email, password}: 
        {email: string, password: string}): Promise<void>;

    /**
     * Get the list of job postings to apply for from
     * the linkdln scraping service.
     * navigate to the jobs list page, apply the filters and extract the job postings.
     */
    abstract getJobsToApply(params: LinkedInSearchParams): Promise<JobPostingDto[]>;

    abstract searchJob(url: string): Promise<JobPostingDto | null>;
    abstract resolveFillFormAndApply(job: JobPostingDto): Promise<void>;

}