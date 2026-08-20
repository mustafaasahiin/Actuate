import { Router } from 'express';
import { config, integrationStatus } from '../config.js';

const router = Router();

router.get('/health', (_req, res) => {
  res.json({
    ok: true,
    service: 'actuate-server',
    version: '1.0.0',
    integrations: integrationStatus(),
    freeQuotaPerWeek: config.freeQuotaPerWeek,
  });
});

export default router;