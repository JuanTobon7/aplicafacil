import express, { Express, Request, Response, NextFunction } from 'express';
import cors from 'cors';
import {
  fillFormRoute,
  embeddingsRoute,
  searchProfileRoute,
  searchPeopleRoute,
  healthRoute,
  infoRoute,
} from '../http/routes.js';
import { logger } from '../logger/logger.js';

/**
 * Create and configure Express app with middleware and routes
 */
export function createExpressApp(): Express {
  const app = express();

  // ====================================
  // MIDDLEWARE
  // ====================================
  app.use(cors());
  app.use(express.json({ limit: '10mb' }));

  // Request logging middleware
  app.use((req: Request, res: Response, next: NextFunction) => {
    const method = req.method;
    const path = req.path;
    logger.info(`📨 Incoming ${method} ${path}`, {
      headers: req.headers,
      bodySize: req.body ? JSON.stringify(req.body).length : 0,
    });
    next();
  });

  // ====================================
  // ROUTES
  // ====================================
  app.get('/', infoRoute);
  app.get('/health', healthRoute);

  // Tools endpoints
  app.post('/tools/fill-form', fillFormRoute);
  app.post('/tools/embeddings', embeddingsRoute);
  app.get('/tools/search-profile', searchProfileRoute);
  app.post('/tools/search-people', searchPeopleRoute);

  // ====================================
  // ERROR HANDLING
  // ====================================
  app.use((err: any, req: Request, res: Response, next: NextFunction) => {
    logger.error('Unhandled error', err);
    res.status(500).json({
      error: 'Internal server error',
      message: err instanceof Error ? err.message : 'Unknown error',
    });
  });

  return app;
}
