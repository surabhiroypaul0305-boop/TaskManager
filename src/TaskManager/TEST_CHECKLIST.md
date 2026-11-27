# Step 9: offline API test checklist

Run `TaskManager.app.TestRunner` in a fresh JVM. No key is needed: both strategies receive test clients through `ParserFactory`. Each assertion prints PASS or FAIL; a failed run exits nonzero.

| Case | Expected result |
|---|---|
| Blank structured fields, invalid boolean/priority/date | 400 with every field issue; invalid update leaves the original task unchanged |
| February 29 in a leap year; February 30 | Valid date accepted; impossible date rejected |
| Unknown ID on get, update, delete | 404 with `NOT_FOUND` and empty details |
| Blank text | 400 before the fake is called |
| Empty model title, impossible model date, malformed reply, or thrown I/O failure | 502 `BAD_GATEWAY`; repository equals its pre-call snapshot |
| Retry after a model failure | Same key can succeed; failures do not reserve it |
| Same key and text; same key and different text | Original response on replay, 422 on conflict; one fake call and one task |
| Breakdown with a valid first item and invalid second item | 502; neither item saved |
| Valid breakdown retried with the same key | Same response, two children total, one fake call |
| Create a subtask under a child; break down a child | Both 400; no additional breakdown call |
| Page past the last task | 200 with an empty list |
| Unexpected exception | 500 with a sanitized message |

**Critique of the baseline:** the original 22 checks verified structured validation and error formatting. Constructing a `BadGatewayException` directly did not prove that the parser rejected a bad reply or that the repository stayed unchanged. Separate suites covered parts of the flow; the main runner now exercises those paths through `TaskApi` with counting fakes, including the retry after failure.

**Debugger checkpoints:** In `LlmTaskParser.parse`, stop on `return new ParsedTask(...)` and inspect `fields`. In `LlmBreakdownStrategy.breakDown`, stop on `return requests` and inspect `reply`, `requests.size()`, and the request titles. Run `TestRunner` in debug mode; both replies come from local fixtures.
