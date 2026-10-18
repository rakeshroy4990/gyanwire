import { expect, it } from 'vitest';
import { applyIdeaItem } from '../useNewsIdeas.js';

it('keeps a preview card until a real idea arrives', () => {
  const withPreview = applyIdeaItem([], {
    id: 'ni-1-x',
    title: 'Preview angle',
    preview: true,
  });
  expect(withPreview).toHaveLength(1);
  expect(withPreview[0].preview).toBe(true);

  const withReal = applyIdeaItem(withPreview, {
    id: 'ni-1-x',
    title: 'Real angle',
  });
  expect(withReal).toHaveLength(1);
  expect(withReal[0].title).toBe('Real angle');
  expect(withReal[0].preview).toBeUndefined();
});

it('drops every preview card when any real idea arrives', () => {
  const list = applyIdeaItem(
    [{ id: 'preview-1', title: 'Weak', preview: true }],
    { id: 'ni-2', title: 'Strong angle' },
  );
  expect(list.map((row) => row.title)).toEqual(['Strong angle']);
});

it('replaces an idea with the same id', () => {
  const list = applyIdeaItem(
    [{ id: 'ni-1', title: 'First' }],
    { id: 'ni-1', title: 'Updated' },
  );
  expect(list).toEqual([{ id: 'ni-1', title: 'Updated' }]);
});
