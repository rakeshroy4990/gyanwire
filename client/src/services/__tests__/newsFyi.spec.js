import { describe, expect, it } from 'vitest';
import { isFyiNewsItem, withoutFyiNews } from '../newsFyi.js';

describe('newsFyi', () => {
  it('drops settlement check-details briefs', () => {
    const title = 'Sebi notifies new settlement rule; unveils formula, fast-track route. Check details';
    expect(isFyiNewsItem({ title, signalType: 'launch' })).toBe(true);
    expect(withoutFyiNews([{ title, signalType: 'launch' }, { title: 'Broker cuts fees', signalType: 'pricing' }]))
      .toHaveLength(1);
  });

  it('drops commodity market commentary', () => {
    const title = 'Brent commands a war premium as Middle East supply risks keep oil';
    expect(isFyiNewsItem({ title, signalType: 'other' })).toBe(true);
    expect(withoutFyiNews([
      { title, signalType: 'other' },
      { title: 'HPCL launches a prepaid fuel card for truck fleets', signalType: 'launch' },
    ])).toHaveLength(1);
  });

  it('drops plant inspection observations', () => {
    const title = 'Dr Reddy’s Laboratories receives two observations for Andhra Pradesh facility';
    expect(isFyiNewsItem({ title, signalType: 'other' })).toBe(true);
    expect(withoutFyiNews([
      { title, signalType: 'other' },
      { title: 'Dr Reddy’s recalls a batch after the plant observations', signalType: 'shortage' },
    ])).toHaveLength(1);
  });

  it('drops explicit fyi signal', () => {
    expect(isFyiNewsItem({ title: 'Anything', signalType: 'fyi' })).toBe(true);
  });
});
