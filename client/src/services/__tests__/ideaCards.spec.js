import { describe, expect, it } from 'vitest';
import {
  angleLabel,
  customerLine,
  effortTag,
  isPipelineIdea,
  moneyLine,
  moneyShort,
  scoreBars,
  testLine,
  urgencyChip,
} from '../ideaCards.js';

const idea = {
  angleType: 'comparison_content',
  buildEffort: 'days',
  timeWindow: { urgency: 'weeks' },
  customer: { who: 'Fitness-curious buyers in tier-1 cities', where: 'YouTube in India' },
  revenueModel: {
    type: 'affiliate',
    price: '₹200 to ₹800 per sale',
    rationale: 'associates pay that range on a tracker',
  },
  validationTest: { action: 'Publish the page', successMetric: '30 signups in 7 days' },
  scores: {
    dimensions: [
      { key: 'demandEvidence', score: 3, reason: 'Estimate from India relevance.', assumed: true },
      { key: 'buildEffort', score: 4, reason: 'Build effort is days.', assumed: false },
      { key: 'competition', score: 2, reason: 'Comparison pages compete.', assumed: true },
    ],
  },
};

describe('ideaCards', () => {
  it('labels the angle, effort, and urgency for the card', () => {
    expect(isPipelineIdea(idea)).toBe(true);
    expect(angleLabel(idea)).toBe('Social media');
    expect(effortTag('hours')).toBe('Quick win');
    expect(effortTag('weeks')).toBe('Bigger build');
    expect(urgencyChip('days')).toBe('Days');
  });

  it('states a reasoned price and the week test', () => {
    expect(moneyLine(idea)).toBe('Affiliate, ₹200 to ₹800 per sale, because associates pay that range on a tracker');
    expect(moneyShort(idea)).toBe('Affiliate · ₹200 to ₹800 per sale');
    expect(customerLine(idea)).toContain('Fitness-curious buyers');
    expect(testLine(idea)).toBe('Publish the page. 30 signups in 7 days.');
    expect(scoreBars(idea).map((bar) => bar.label)).toEqual(['Demand', 'Effort', 'Competition']);
    expect(scoreBars(idea)[0].tip).toContain('Assumed');
  });
});
