// vector-db/vector-db.module.ts
import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { ConfigModule, ConfigService } from '@nestjs/config';

@Module({
  imports: [
    TypeOrmModule.forRootAsync({
      name: 'vectorConnection', // nombre distinto a la conexión default
      imports: [ConfigModule],
      inject: [ConfigService],
      useFactory: (config: ConfigService) => ({
        type: 'postgres',
        host: config.get('VECTOR_DB_HOST'),
        port: config.get('VECTOR_DB_PORT'),
        username: config.get('VECTOR_DB_USER'),   // rol con permiso de escritura
        password: config.get('VECTOR_DB_PASSWORD'),
        database: config.get('VECTOR_DB_NAME'),
        schema: 'vector',
        entities: [DocumentEmbedding],
        synchronize: false, // nunca true en este tipo de tabla, usa migraciones
      }),
    }),
    TypeOrmModule.forFeature([DocumentEmbedding], 'vectorConnection'),
  ],
  exports: [TypeOrmModule],
})
export class VectorDbModule {}