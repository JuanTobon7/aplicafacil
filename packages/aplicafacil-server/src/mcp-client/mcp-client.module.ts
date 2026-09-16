import { Module } from '@nestjs/common';
import { HttpModule } from '@nestjs/axios';
import { ConfigModule } from '@nestjs/config';
import { McpClientService } from './mcp-client.service';

@Module({
  imports: [HttpModule, ConfigModule],
  providers: [McpClientService],
  exports: [McpClientService],
})
export class McpClientModule {}
