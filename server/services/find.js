import { searchWeb } from '../engine/search.js';
import { isLlmConfigured, refineQuery, blendLlmRankings } from './llm.js';

export async function findBestResults({ categories, subcategory, thoughts, limit }) {
  const refined = await refineQuery({ categories, subcategory, thoughts });

  let results = await searchWeb({
    query: refined.query,
    categories,
    thoughts: subcategory ? `${subcategory}. ${thoughts}` : thoughts,
    limit,
  });

  const blended = await blendLlmRankings({
    categories,
    thoughts: subcategory ? `${categories[0]} / ${subcategory}: ${thoughts}` : thoughts,
    query: refined.query,
    results,
  });

  results = blended.results;

  return {
    query: refined.query,
    intent: refined.intent,
    engine: 'gyanwire',
    usedLlm: refined.usedLlm || blended.usedLlm,
    llmAvailable: isLlmConfigured(),
    categories,
    subcategory: subcategory || null,
    results: results.map(({ usedLlm, snippet, text, pointers, ...rest }) => ({
      ...rest,
      excerpt: snippet || rest.description,
      pointers,
    })),
  };
}
