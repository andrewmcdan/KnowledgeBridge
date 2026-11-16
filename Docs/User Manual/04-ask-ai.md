# 4. Ask AI: search and cited answers

## Choose a mode

**Search** returns ranked matching excerpts from available workspace documents. It does not make an LLM synthesis call. **Synthesis** asks the knowledge engine to produce an answer from retrieved workspace material and returns citations, warnings, or knowledge gaps when available. Synthesis can take longer and may incur provider charges.

Only active, completed documents are eligible. Search results can be shorter than the selected limit when few matches exist or unavailable items are excluded. A title-only search belongs in the top Knowledge Base search field; Ask AI searches indexed content.

## Run a search

1. Open **Ask AI** and select **Search**.
2. Enter a focused question or phrase in **Message KnowledgeBridge**. For example: `What approval is required for a software purchase?`
3. Set **Result limit** to an integer from 1 to 50. The default is 10.
4. Select **Submit question** or press Enter. Use Shift+Enter for a new line.
5. Review the ranked excerpts. Select **View document** on a relevant result to read its full source.

Search questions accept up to 1,000 UTF-16 code units after surrounding whitespace is removed. Most ordinary characters use one code unit; many emoji use two. Internal line breaks and spacing are preserved. Blank questions and NUL characters are rejected. Invalid limits or questions show a message and remain editable.

The result limit applies only to Search. Raising it asks for more hits, not a stronger guarantee of relevance. Multiple matching passages can come from the same document and are intentionally kept as separate results.

## Request a synthesis

1. Select **Synthesis**.
2. Ask one answerable question, naming the relevant project, policy, date, or decision where possible. Synthesis questions accept up to 2,000 normalized UTF-16 code units.
3. Submit and wait for the answer. A request already in progress cannot be submitted again until it finishes or is canceled locally.
4. Read the answer together with its **Citations**, **Knowledge gaps**, and **Warnings**.
5. Open the source citations to verify the exact language and context before using the answer in a decision.

An **AI-generated with citations** label identifies generated synthesis. **Extractive fallback** means the engine returned source excerpts instead of a complete generated answer. A fallback can still be useful, but should not be presented as a completed AI analysis. Missing citations or gaps mean the evidence may be insufficient; ask an administrator to add or correct sources if needed.

## Follow the evidence

Inline citation markers and entries under **Citations** can open the same stored document. The preview may highlight related passages and provide next/previous passage navigation. Search snippets and synthesis wording may be abbreviated or paraphrased. If no reliable passage match is found, read the full document; absence of a highlight does not prove that the claim appears elsewhere.

The preview loads the document's current retained content, which can differ from an older answer. If a referenced item has become unavailable, KnowledgeBridge can reject the answer and ask you to retry rather than disclose content from an excluded source.

## Cancellation, caching, and another question

Select **Cancel request** to stop waiting in the browser. Cancellation does not guarantee the backend, engine, or provider stopped processing or charging for the request. You can submit another question afterward. Avoid using repeated cancel/resubmit actions as a way to force a faster answer.

The composer clears after submission, while the displayed result retains the submitted question. Changing the mode does not relabel an already displayed answer as a result from the new mode. There is no persistent chat history or promised multi-turn memory: include the necessary context in each new question.

Identical synthesis questions can reuse an exact-match cached answer while the knowledge revision is unchanged. Surrounding whitespace is ignored; different wording or letter case is not necessarily the same cache key. Successful knowledge changes invalidate older cached answers. Cache hits make no new synthesis call and report zero new query tokens/cost. Extractive fallbacks are not stored as successful generated answers.

## Get better results

- Ask `What does the travel policy say about manager approval?` rather than `Explain everything about travel`.
- Separate questions about unrelated projects or dates.
- Name the source or decision you expect, then inspect its citation.
- If results conflict, ask a narrower question and compare the source dates and wording yourself.
- Treat a **semantic retrieval signals unavailable** warning as reduced search capability, not proof that no relevant knowledge exists.
