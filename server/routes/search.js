import { Router } from 'express';
import { z } from 'zod';
import { findBestResults } from '../services/find.js';
import {
  getDefaultProductNews,
  getIndustryCatalog,
  getIndustryProductNews,
  listIndustries,
  listSubs,
} from '../services/industryNews.js';

export const searchRouter = Router();

const bodySchema = z.object({
  categories: z.array(z.string().trim().min(1).max(40)).min(1).max(6),
  subcategory: z.string().trim().min(1).max(40).optional().nullable(),
  thoughts: z.string().trim().min(8).max(2000),
  limit: z.number().int().min(3).max(10).optional().default(6),
});

searchRouter.get('/industries', (_req, res) => {
  res.json({
    success: true,
    data: getIndustryCatalog(),
    message: 'Research industries and sub-combinations.',
    timestamp: new Date().toISOString(),
  });
});

searchRouter.get('/news/default', async (_req, res, next) => {
  try {
    const data = await getDefaultProductNews(5);
    return res.json({
      success: true,
      data: {
        ...data,
        intent: 'Top searched share and medical product news',
        engine: 'gyanwire',
        usedLlm: false,
        isDefaultNews: true,
      },
      message: 'Product news ready.',
      timestamp: new Date().toISOString(),
    });
  } catch (error) {
    return next(error);
  }
});

searchRouter.get('/news/:industry', async (req, res, next) => {
  try {
    const industry = decodeURIComponent(req.params.industry || '');
    const sub = typeof req.query.sub === 'string' && req.query.sub.trim()
      ? req.query.sub.trim()
      : null;

    if (!listIndustries().includes(industry)) {
      return res.status(400).json({
        success: false,
        message: 'Pick a valid research industry.',
        errorCode: 'UNKNOWN_INDUSTRY',
      });
    }

    if (sub && !listSubs(industry).includes(sub)) {
      return res.status(400).json({
        success: false,
        message: 'Pick a valid sub-combination for this industry.',
        errorCode: 'UNKNOWN_SUB',
      });
    }

    const data = await getIndustryProductNews(industry, 5, sub);
    return res.json({
      success: true,
      data: {
        ...data,
        intent: `Latest ${data.label}`,
        engine: 'gyanwire',
        usedLlm: false,
        isDefaultNews: false,
        subs: listSubs(industry),
      },
      message: `Latest ${data.label}.`,
      timestamp: new Date().toISOString(),
    });
  } catch (error) {
    return next(error);
  }
});

searchRouter.post('/search', async (req, res, next) => {
  try {
    const parsed = bodySchema.safeParse(req.body);
    if (!parsed.success) {
      return res.status(400).json({
        success: false,
        message: 'Pick a research industry and write a clearer research question.',
        errorCode: 'VALIDATION_ERROR',
        details: parsed.error.flatten(),
      });
    }

    const result = await findBestResults(parsed.data);
    return res.json({
      success: true,
      data: result,
      message: 'Here are the best matches we found.',
      timestamp: new Date().toISOString(),
    });
  } catch (error) {
    return next(error);
  }
});
