# Research Without web_search (Firecrawl unconfigured)

This session `web_search` failed with: "Web tools are not configured. Set
FIRECRAWL_API_KEY ... Your Nous Portal account has no usable paid credits."
But the box HAS outbound network — `curl` reaches the public internet. So use
curl against free, structured knowledge APIs instead of giving up.

## Reusable curl recipes (live, no key needed)

Wikipedia REST summary (one paragraph):
```bash
curl -s --max-time 20 "https://en.wikipedia.org/api/rest_v1/page/summary/Artificial_general_intelligence"
```

Wikipedia full plain-text extract (use in execute_code with `json` imported):
```bash
curl -s --max-time 30 "https://en.wikipedia.org/w/api.php?action=query&prop=extracts&explaintext=1&titles=Artificial%20general%20intelligence&format=json&exsectionformat=plain"
```
Parse: `json.loads(output)["query"]["pages"][<id>]["extract"]`.

Section-only (intro): append `&exintro=1`. Follow redirects: append `&redirects=1`.
If exact title unknown, try `ARC Prize`, `OpenAI o3`, `DeepSeek`, `GPT-5`,
`Claude (language model)`, `Gemini (language model)`, `AI safety`.

## Pattern that worked
1. In `execute_code`, `import json` (hermes_tools does NOT auto-import stdlib).
2. Loop over a dict of {name: url}, `terminal(f'curl ...')` each, return json.
3. Print `extract[:N]` per source — enough to ground factual claims.

## Caveats
- This gives encyclopedic / public facts, not live news or paywalled docs.
- For library/framework API docs, the `gitmcp-docs` skill (context7 MCP) is the
  real path — but that needs the MCP wired. curl-to-wiki is the no-setup fallback.
- Don't present curl-fetched text as "I know" — it's fetched data; cite it as such.
