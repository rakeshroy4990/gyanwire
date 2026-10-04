function isConfigured() {
  const key = process.env.LLM_API_KEY;
  return Boolean(key && !key.includes('your-key'));
}

async function chatJson(system, user) {
  if (!isConfigured()) return null;

  const baseUrl = (process.env.LLM_BASE_URL || 'https://api.openai.com/v1').replace(/\/$/, '');
  const model = process.env.LLM_MODEL || 'gpt-4o-mini';

  const response = await fetch(`${baseUrl}/chat/completions`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${process.env.LLM_API_KEY}`,
    },
    body: JSON.stringify({
      model,
      temperature: 0.2,
      response_format: { type: 'json_object' },
      messages: [
        { role: 'system', content: system },
        { role: 'user', content: user },
      ],
    }),
  });

  if (!response.ok) {
    const text = await response.text();
    console.error('LLM request failed:', response.status, text.slice(0, 400));
    return null;
  }

  const payload = await response.json();
  const content = payload?.choices?.[0]?.message?.content;
  if (!content) return null;

  try {
    return JSON.parse(content);
  } catch {
    console.error('LLM returned non-JSON content');
    return null;
  }
}

/**
 * Turn categories + free-form thoughts into a focused web search query.
 */
export async function refineQuery({ categories, subcategory, thoughts }) {
  const fallback = buildFallbackQuery(categories, thoughts, subcategory);
  const scope = subcategory
    ? `${categories.join(', ')} / ${subcategory}`
    : categories.join(', ');

  const refined = await chatJson(
    `You turn research notes into a precise R&D web search query.
Return JSON only: {"query":"...","intent":"one short sentence"}.
Rules:
- Always frame the query for research and development value
- Prefer India-first context whenever relevant (India, Indian companies, ISRO, NSE, Nifty, Indian regulators)
- Prefer papers, trials, patents, labs, technical reports, and primary sources
- Query max 18 words
- Keep the person's real intent
- Include the industry and sub-combination when useful
- Do not invent facts they did not mention`,
    `Research scope: ${scope}\nRegion preference: India first, then global\nResearch notes:\n${thoughts}`,
  );

  if (!refined?.query || typeof refined.query !== 'string') {
    return {
      query: fallback,
      intent: `R&D research in ${scope}.`,
      usedLlm: false,
    };
  }

  return {
    query: refined.query.trim().slice(0, 180),
    intent: typeof refined.intent === 'string'
      ? refined.intent.trim().slice(0, 160)
      : `R&D research in ${scope}.`,
    usedLlm: true,
  };
}

/**
 * Optional LLM blend on top of pointer scores.
 * Pointer ranking always remains the base; LLM can nudge score and why.
 */
export async function blendLlmRankings({ categories, thoughts, query, results }) {
  if (!results.length || !isConfigured()) {
    return { results, usedLlm: false };
  }

  const ranked = await chatJson(
    `You nudge rankings for an R&D research search engine that already scored pages with pointers.
Return JSON only:
{"rankings":[{"id":"r-1","delta":-15to15,"why":"one plain sentence under 22 words"}]}.
Rules:
- Include every id exactly once
- delta adjusts the existing pointer score
- Prefer research value: evidence, methods, trials, patents, technical depth
- Prefer India-relevant sources and companies when quality is comparable
- Prefer concrete usefulness for THIS research question
- No hype words`,
    JSON.stringify({
      categories,
      thoughts,
      query,
      foundation: 'R&D research',
      regionPreference: 'India first, then global',
      results: results.map((item) => ({
        id: item.id,
        title: item.title,
        url: item.url,
        description: item.description,
        snippet: item.snippet?.slice(0, 280),
        pointerScore: item.score,
        why: item.why,
      })),
    }),
  );

  if (!ranked?.rankings || !Array.isArray(ranked.rankings)) {
    return { results, usedLlm: false };
  }

  const byId = new Map(ranked.rankings.map((row) => [row.id, row]));

  const blended = results
    .map((item) => {
      const row = byId.get(item.id);
      const delta = Number(row?.delta);
      const nextScore = Number.isFinite(delta)
        ? Math.max(0, Math.min(100, Math.round(item.score + Math.max(-15, Math.min(15, delta)))))
        : item.score;

      return {
        ...item,
        score: nextScore,
        why: typeof row?.why === 'string' && row.why.trim()
          ? row.why.trim().slice(0, 180)
          : item.why,
        usedLlm: true,
      };
    })
    .sort((a, b) => b.score - a.score);

  return { results: blended, usedLlm: true };
}


export function buildFallbackQuery(categories, thoughts, subcategory = null) {
  const cleaned = thoughts
    .replace(/[^\p{L}\p{N}\s'-]/gu, ' ')
    .replace(/\s+/g, ' ')
    .trim();

  const thoughtCore = cleaned.split(' ').slice(0, 11).join(' ');
  const categoryCore = categories.slice(0, 3).join(' ');
  const subCore = subcategory ? subcategory : '';
  const hasResearchWord = /\b(research|r&d|study|trial|patent|paper)\b/i.test(cleaned);
  const hasIndiaWord = /\bindia\b|\bindian\b|\bnifty\b|\bsensex\b|\bisro\b/i.test(cleaned);
  const foundation = hasResearchWord ? '' : 'R&D research';
  const region = hasIndiaWord ? '' : 'India';
  return `${foundation} ${region} ${categoryCore} ${subCore} ${thoughtCore}`
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 180);
}

export { isConfigured as isLlmConfigured };
