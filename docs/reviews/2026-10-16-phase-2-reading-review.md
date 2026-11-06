# Phase 2 final independent whole-change review

Reviewed 2026-10-16 against `review-package.md`, the phase 2 specification and implementation plan, and `AGENTS.md`. Reviewed working-tree/untracked phase 2 implementation, not just the diff against the pre-existing identity baseline. No production or test files changed; no Maven, Playwright, database calls, secret configuration, or credential fixtures accessed.

Result: **changes requested: 3 Important findings; no Critical finding identified.** These are frontend lifecycle/data-loss problems. No confirmed backend authorization bypass was identified in the reviewed start/unpublish, old-version access, collection ownership, or import paths.

## Important

### I1 — Administrator publication confirmation survives identity/component changes

**Location:** `frontend/src/views/AdminArticleEditorView.vue:47–54`, especially the request at line 52 after the confirmation wait at line 51.

**Trigger:** Administrator A opens an already saved article and clicks Publish or Unpublish, leaving the confirmation open. Another tab changes the session to administrator B. The router restores B and replaces/remounts the admin page, but the Element Plus programmatic message box is mounted independently under `document.body`. Confirm the surviving dialog.

**Result:** The old editor closure still calls `publishArticle`/`unpublishArticle` for A's selected article. `businessWrite` captures identity only when invoked, after the confirmation has resolved, so it legitimately captures B and sends the old operation as B. If its article lock version is still current, the server accepts it. This changes publication under an identity that did not initiate the operation; it is not a server role bypass. Ordinary navigation/unmount while confirmation waits has the same missing invalidation.

**Basis:** There is no `onBeforeUnmount`, captured identity generation, or post-confirmation context check in this component. `AdminLayout.vue` keys the editor by user ID and the remote identity handler navigates administrators to `/admin`; neither closes this programmatic box. The installed Element Plus `messageBox.mjs` creates an independent rendered container and disposes it only on its own vanish/close lifecycle. A read-only Node harness transpiled the actual SFC script, deferred its confirmation, changed a mocked identity A→B and invoked registered unmount hooks, then resolved confirmation. Output: `confirmationAfterAccountChange:[{identity:"admin-B",id:"10",lock:0}], componentUnmountGuards:0` (exit 0).

**Fix/verification:** Capture identity and article/version context before opening the dialog; invalidate on unmount/account change, and recheck after confirmation and before sending. Close the owned dialog when its initiating context disappears. Add a regression that changes administrator identity while the confirmation is pending and verifies zero publication requests after confirming the stale dialog. Apply to both publish and unpublish.

### I2 — Leaving while a save is in flight aborts the newer queued reading position

**Location:** `frontend/src/reading/useReadingProgress.ts:20–46` (line 23 and lines 42–46); integration at `frontend/src/views/ReadingDetailView.vue:70–71,106`.

**Trigger:** A save for position S2 is in flight; the reader moves to S3 and then clicks a router link to leave. The route guard calls `await tracking.flush()` while S2 is pending.

**Result:** `flush` returns only S2's promise. When S2 succeeds, its `finally` starts the queued S3 save without chaining it into the promise awaited by the guard. The guard therefore completes while S3 is still pending. Unmount calls `dispose`, which aborts S3. With the normal additional CSRF request for S3, its business write can be prevented entirely. Returning to the article restores S2 although S3 was already queued when departure was requested. This occurs on a healthy connection with ordinary response latency, rather than only during browser termination/offline best-effort saving.

**Basis:** A read-only Node harness transpiled the actual composable, deferred saves, and followed the route-guard/unmount sequence. At guard resolution it printed two saves (S2 and S3), acknowledged S2, and state `saving`; after `dispose`, S3's signal was aborted. Exit 0. The existing serialization unit test confirms that a second request starts, but does not assert that a departure flush waits for that request. The browser progress test only uses one sentence and explicit completion.

**Fix/verification:** Provide a departure/drain operation whose promise includes the revision queued when departure begins (while still respecting failures, conflicts, disposal and account changes), and have both route guards await it. Add a deterministic test holding the first save, enqueueing another position, requesting departure, then proving departure remains pending until the queued position is acknowledged or explicitly fails.

### I3 — Reconnecting after a failed draft save silently discards the administrator's edits

**Location:** `frontend/src/views/AdminArticleEditorView.vue:19–27,39,61–62`.

**Trigger:** Open an existing draft, change its body/title, and fail Save with a network error, timeout, or authentication-service outage before the write commits. Restore service and click the displayed “重新连接” button.

**Result:** The save failure sets `pageError` through shared form feedback. The template removes the editor and offers only a button invoking `load`. `load` fetches the saved server draft, overwrites the locally edited input via `Object.assign`, then sets the snapshot to the overwritten input. Unsaved work is lost without the existing leave/discard confirmation, and the editor now treats it as clean. This can discard a substantial article correction after a routine temporary failure.

**Basis:** `classifyFeedback` routes network/timeout/auth-service failures to `onPageError`; the new article editor binds this to its page replacement state. A read-only harness of the actual SFC observed `input:"Unsaved new body",pageError:network` after failed save and `input:"Saved body",dirty:false` after the reconnect action (exit 0). Existing editor browser coverage tests successful save/preview/republish, not failed-save recovery. The defect is in how this new editor uses the existing feedback helper, not a requested rewrite of the old authentication forms.

**Fix/verification:** Preserve editable input and its original lock version on save failures. Retry connectivity/save without rehydrating the form; if reloading server content is necessary, make discarding dirty local input an explicit choice. Cover failed existing-draft save → reconnect/retry with all local fields retained and no silent dirty reset.

## Minor / deferred

- **Keyword senses need two targeted editorial corrections before content acceptance.** `content/dictionary/editorial-supplement.v1.tsv:109` supplies only a verb sense for `review`, but the first article's designated keyword appears as a noun in “This short review” and “another review” (`content/reading/original-b1-b2.v1.source.md:6,10`). Likewise line 228 provides only verb `repair`, while the designated surface includes “every kind of repair” at source line 30. The generated dictionary repeats these single-sense entries. Add corresponding noun senses and regenerate with the required new dataset version; do not rewrite an already-loaded manifest version. This is limited contextual coverage, not a claim that the existing verb definitions themselves are false. `ContentQualityTest` currently checks keyword lookup availability, not contextual part of speech. Remaining editorial/human acceptance is already honestly marked pending.
- Code in several new Java services/controllers and Vue templates is compressed into long single lines. This increases the cost of maintaining the concurrency and lifecycle rules but is not itself a correctness blocker. Avoid unrelated wholesale formatting during fixes.

## Reviewed boundaries and basis

- `ReadingService.start` locks the article, checks its current published state, and uses current reads for both version and existing progress. Controlled start/unpublish order, duplicate-start, and republish tests are present. Version reads require either the currently published version or the actor's own progress; the version mapper also constrains article ownership.
- Collection reconstructs the sentence from the owned immutable version, requires a whole token and a dictionary candidate, and serializes writes by owner with current reads after the lock. Soft cancel keeps sources; historical access still derives from owned reading progress. Public dictionary cache entries exclude collection state.
- The shared token patterns/UTF-16 range code and browser selection logic were inspected; no confirmed neighboring-word/surrogate-split defect was found. Rendering remains plain Vue text.
- Import validation and confirmation share the input rules; confirmation repeats validation, uses the normalized payload digest and actor match, and handles duplicate-ID races after the failed transaction has ended. Tests include concurrent replay, cross-actor conflict, mixed invalid input, injected rollback, and chunked byte limits.
- Frontend business transport captures identity before CSRF and checks it through authenticated transport. Reading/lookup/collection cancellation generally protects stale responses. I1 identifies an earlier confirmation boundary not covered by that transport protection.

## Declined to judge / verification limits

- Did not rerun Maven, Playwright, full unit suites, build, or real database flows because the review assignment expressly reserved these for the main agent. The supplied 47+45 backend, 50 frontend, and browser counts remain implementation evidence, not independently rerun review claims. Only the two read-only, in-memory Node control-flow harnesses above were executed, both exit 0; no artifact besides this report was written.
- Did not audit prior JWT/identity dirty changes as part of this phase 2 review. Related source was read only to establish how the new feature's identity boundaries behave. No local secrets or credential fixtures were read.
- Did not judge final development-service cutover, operational cleanup, or final acceptance/progress documents still being prepared by the main agent. Incomplete cutover is not counted as a code defect.
- Did not certify visual/mobile native-selection acceptance, official CEFR level, or independent human language approval. Source/content inspection and existing synthetic range tests do not replace those checks. The content README currently distinguishes automated/editor checks from pending human acceptance.

## 实现者分级与修复（2026-10-16）

三项Important均接受。重点词名词义项从Minor升级为Important：原文已指定这些关键词作学习重点，只给动词义项会向学习者传递错误词性线索，不能仅作为覆盖不足略过。保持319基本词条，原文名词释义新增到词典1.1，未覆写已装载1.0内容。

- I1：发布/下架在打开确认前捕获身份代次、文章ID/锁版本和组件上下文；确认后再次检查。身份变化/卸载关闭本组件拥有的弹窗，迟到读写不再改旧页。admin-article-recovery的跨账号弹窗回归先失败，修复后通过，发布/下架均覆盖。
- I2：flush返回整个串行排队保存的Promise；路由离开等待最新排队修订确认或明确失败，失败停止自动重放。reading-progress单元先复现离开过早完成，修复后50项前端单元通过。
- I3：网络故障后保留编辑表单和dirty，重新连接只安全GET检查服务，不重载服务器旧正文。初次加载失败仍可重载。浏览器先复现正文被替换，修复后重连/重试保留本地正文。
- 义项：ContentQualityTest新增review/repair名词断言，先失败后新增原创释义并升版；修复后全量48后端单元/MVC+45真实集成通过。

格式压缩作为Minor保留：后续维护时局部整理，不在本次修复中进行无关的大面积格式改动。测试、部署切换及人工验收的评审边界保持原报告说明；最终全套浏览器与运行结果见phase-2验收记录。未做第二轮评审、未自动提交。
