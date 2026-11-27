# LLM API Cheat Sheet (for students)

You never write HTTP code in this assignment: `HttpLlmClient` does it. This page explains what it does,
so you can debug it, and how to get a key working in 10 minutes.

---

## 1. Get a key (pick ONE provider)

| Provider | Where to create a key | `LLM_PROVIDER` |
|---|---|---|
| Anthropic | API keys page of the Anthropic console (platform.claude.com) | `anthropic` (default) |
| OpenAI | API keys page of the OpenAI platform (platform.openai.com) | `openai` |
| Google Gemini | Google AI Studio → "Get API key" (aistudio.google.com) | `gemini` |

Keys are secrets. Anyone who has the key can spend your credit. **Never** put a key in source code,
a README, a screenshot, or a commit.

> No key at all? Everything except the "real model" call works offline. Use `FakeLlmClient` (tests) and the
> rule-based parser (`"offline"` mode). Your app must answer **503** when the key is missing — that is graded.

## 2. Give the key to your program (environment variables)

| Variable | Required | Meaning |
|---|---|---|
| `LLM_API_KEY` | yes (for the real model) | your key |
| `LLM_PROVIDER` | no | `anthropic` (default), `openai` or `gemini` |
| `LLM_MODEL` | no | model name; only set it if the default is rejected (see §6) |

**IntelliJ:** *Run → Edit Configurations → (your class) → Environment variables* → `LLM_API_KEY=your-key;LLM_PROVIDER=openai`
**Terminal (macOS/Linux):** `export LLM_API_KEY=your-key` then run from the same terminal window.
**Windows PowerShell:** `$env:LLM_API_KEY="your-key"`

Read it in Java: `String key = System.getenv("LLM_API_KEY");` → `null` when it is not set. Check for `null` **and** blank.
`.env` files are *not* read by Java; if you keep one, it must be in `.gitignore` and you still have to copy values into the run configuration.

## 3. Check it works — before writing any AI code

Run `LlmSmokeTest` (in `scaffold/app`). It sends "Reply with the single word OK" and prints either
`Model replied: OK` or what to check. Fix this first; everything else builds on it.

## 4. What one call looks like

Your prompt goes in, text comes out. Only the wrapping JSON differs per provider.

**Anthropic** — `POST https://api.anthropic.com/v1/messages`

```
headers:  content-type: application/json
          x-api-key: <key>
          anthropic-version: 2023-06-01
body:     {"model":"claude-haiku-5-5","max_tokens":1024,
           "messages":[{"role":"user","content":"<your prompt>"}]}
reply:    {"content":[{"type":"text","text":"<the answer>"}], "stop_reason":"end_turn", ...}
```

**OpenAI** — `POST https://api.openai.com/v1/chat/completions`

```
headers:  Content-Type: application/json
          Authorization: Bearer <key>
body:     {"model":"gpt-6-luna","messages":[{"role":"user","content":"<your prompt>"}]}
reply:    {"choices":[{"message":{"role":"assistant","content":"<the answer>"},"finish_reason":"stop"}], ...}
```

**Google Gemini** — `POST https://generativelanguage.googleapis.com/v1beta/models/<model>:generateContent`

```
headers:  Content-Type: application/json
          x-goog-api-key: <key>
body:     {"contents":[{"parts":[{"text":"<your prompt>"}]}]}
reply:    {"candidates":[{"content":{"parts":[{"text":"<the answer>"}]}, "finishReason":"STOP"}], ...}
```

Try one by hand to see the raw thing (replace the key):

```bash
curl https://api.anthropic.com/v1/messages \
  -H "content-type: application/json" -H "x-api-key: $LLM_API_KEY" -H "anthropic-version: 2023-06-01" \
  -d '{"model":"claude-haiku-5-5","max_tokens":200,"messages":[{"role":"user","content":"Say hi in 3 words"}]}'
```

`HttpLlmClient` does exactly this with `java.net.http.HttpClient`, then pulls out the answer text (`SimpleJson.firstStringValue`).
Notes: OpenAI also offers a newer "Responses" API and Google calls `generateContent` "legacy but supported";
the older endpoints used here are simpler and still work.

## 5. The text you get back is still text

The model's answer is *a String that is supposed to be JSON*. Treat it like user input:

1. Parse it with `SimpleJson.parseFlat(reply)` → throws `IllegalArgumentException` if it is not JSON → **502**.
2. Look at each field: missing? empty? wrong type (`"dueDate": 20261201`)? impossible date? → validation → **502**.
3. Only then save.

Models sometimes wrap JSON in ```` ```json ```` fences even when told not to. `SimpleJson` strips them for you.

## 6. When a call fails

| What you see | Likely cause | Your app should return |
|---|---|---|
| `LLM_API_KEY is not set` | key missing in the run configuration | **503** |
| `rejected the key (HTTP 401/403)` | wrong key, or `LLM_PROVIDER` doesn't match the key's provider | 502 |
| `HTTP 400` / `HTTP 404` mentioning the model | model name retired or unavailable | 502 → set `LLM_MODEL` |
| `HTTP 429` | rate limit or no credit left | 502 |
| `HttpTimeoutException`, `UnknownHostException`, `ConnectException` | no internet, VPN/proxy, or slow response (30 s timeout) | 502 |
| reply is words, not JSON | prompt too loose | 502 (and improve the prompt) |

**Model names change.** Providers retire them every few months. Defaults in `HttpLlmClient`
(`claude-haiku-5-5`, `gpt-6-luna`, `gemini-3.8-flash`) were current when this was written. If you get a
"model not found" error, open your provider's model list and set `LLM_MODEL`.

## 7. Writing a prompt that returns usable JSON

Four habits (explain them in your README):

1. **Give the date.** "Today's date is 2026-11-13 (YYYY-MM-DD)" — otherwise "next Friday" is a guess.
2. **Name every field and its type**, and say what to do when a value is unknown (`null`).
3. **Say "ONLY a JSON object, no explanation, no code fences."**
4. **Show one example** of an input and the exact output. Examples beat descriptions.

Weak: `Make a task from: pay rent monthly`
Better: the prompt in the instructor's `PromptAndEnvWalkthrough` — then write your own. You must be able to explain *your* prompt in the README.

## 8. Cost and safety

A call in this assignment is a few hundred tokens: a fraction of a cent. Still: never put a call in a loop without
a limit, and run the tests with fakes so they never spend money.
Before every push: `git status` shows no `.env`, and `git diff --staged` contains no key.
If you ever commit a key: **revoke it in the provider console immediately** — deleting the commit is not enough.
