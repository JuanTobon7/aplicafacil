import { MiddlewareConsumer, Module, NestModule, RequestMethod } from '@nestjs/common';
import { JwtModule } from '@nestjs/jwt';
import { AuthController } from '../controller/auth.controller';
import { ConfigModule } from '@nestjs/config';
import { TypeOrmModule } from '@nestjs/typeorm';
import { UserModel } from '../models/user.model';
import { AuthService } from '../service/contract/auth.service';
import { AuthServiceImpl } from '../service/impl/auth.service.impl';
import { PeopleModule } from 'src/people/module/people.module';
import { AuthMiddleware } from '../component/impl/auth.middleware.impl';
import { JwtComponent } from '../component/contract/jwt.component';
import { JwtComponentImpl } from '../component/impl/jwt.component.impl';

@Module({

  controllers: [
    AuthController
  ],

  providers: [
      {
        provide: AuthService,
        useClass: AuthServiceImpl,
      },
      {
        provide: JwtComponent,
        useClass: JwtComponentImpl,
      }
    ],

  imports: [
    PeopleModule,
    JwtModule.register({
      secret: process.env.JWT_SECRET,
      signOptions: {
        expiresIn: '1d',
      },
    }),

    ConfigModule.forRoot({
          isGlobal: true,
        }),
        TypeOrmModule.forFeature([UserModel]),
  ],
})

export class AuthModule implements NestModule {
  configure(consumer: MiddlewareConsumer) {
    consumer
      .apply(AuthMiddleware)
      .exclude(
        { path: 'auth/login', method: RequestMethod.POST },
        { path: 'auth/register', method: RequestMethod.POST },  
        { path: 'people', method: RequestMethod.POST },
        { path: 'people', method: RequestMethod.GET },

      )
      .forRoutes('*');
  }
}