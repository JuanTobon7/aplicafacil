import { Module } from '@nestjs/common';
import { ScrapingLinkldnServiceImpl } from '../service/impl/scraping/scraping.linkldn.service.impl';
import { LinkedInComponentsModule } from '../components/linkedin.components.module';

@Module({
  imports: [LinkedInComponentsModule],
  providers: [
    {
      provide: 'ScrapingLinkldnService',
      useClass: ScrapingLinkldnServiceImpl,
    },
  ],
  exports: ['ScrapingLinkldnService'],
})
export class LinkedinAutomationModule {}
