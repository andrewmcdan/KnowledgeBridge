"""Preview or import a missing canonical Markdown copy when an existing import differs only by blank lines."""

import argparse
import json
from pathlib import Path
import sys
import uuid
from urllib.request import Request, build_opener

import evaluate_synthesis as evaluator


def formatting_identity(body):
    # Preserve every nonblank line byte-for-byte; do not normalize words, numbers, indentation or code.
    return "\n".join(line for line in evaluator.normalized(body).splitlines() if line.strip())


def reconcile(app, documents, apply=False):
    existing = evaluator.array(app.request("/api/knowledge-items"), "knowledge items", 1000)
    bodies = [app.content(item["id"], fresh=True) for item in existing
              if item.get("status") == "COMPLETED" and item.get("lifecycleState", "ACTIVE") == "ACTIVE"]
    results = []
    for doc in documents:
        exact = [body["id"] for body in bodies if evaluator.digest(body["text"]) == evaluator.digest(doc["text"])]
        if exact:
            results.append({"doc_id": doc["doc_id"], "status": "already_matches", "item_ids": exact})
            continue
        aliases = [body["id"] for body in bodies if formatting_identity(body["text"]) == formatting_identity(doc["text"])]
        if not aliases:
            raise evaluator.EvaluationError(f"{doc['doc_id']} has no formatting-only matching import; no content was changed.")
        result = {"doc_id": doc["doc_id"], "status": "formatting_only_difference", "existing_item_ids": aliases}
        if apply:
            result["new_item"] = upload(app, doc)
            result["status"] = "canonical_copy_queued"
        results.append(result)
    return results


def upload(app, document):
    boundary = "kb-evaluation-" + uuid.uuid4().hex
    filename = Path(document["path"]).name
    parts = []
    for key, value in [("title", document["title"]), ("type", "Markdown document")]:
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n'.encode("utf-8"))
    parts.extend([f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\nContent-Type: text/markdown\r\n\r\n'.encode("utf-8"),
                  document["text"].encode("utf-8"), f"\r\n--{boundary}--\r\n".encode("ascii")])
    request = Request(app.base + "/api/knowledge-items/upload", data=b"".join(parts), method="POST",
                      headers={**app.headers, "Content-Type": "multipart/form-data; boundary=" + boundary})
    try:
        with build_opener(evaluator.NoRedirect()).open(request, timeout=180) as response:
            value = evaluator.record(json.loads(response.read(evaluator.MAX_RESPONSE_BYTES)), "upload result")
        return {key: value.get(key) for key in ["id", "status", "ingestionStage"]}
    except OSError:
        raise evaluator.EvaluationError("Canonical upload failed; it was not retried. Inspect the knowledge list before retrying.") from None


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--doc-id", action="append", required=True)
    parser.add_argument("--apply", action="store_true", help="Import a new canonical copy; preserve existing documents.")
    parser.add_argument("--env-file", type=Path, default=evaluator.ROOT / ".env")
    parser.add_argument("--api-url", default="http://127.0.0.1:8080")
    args = parser.parse_args(argv)
    try:
        _, documents = evaluator.load_documents(args.doc_id, 1, 42)
        app = evaluator.Application(args.api_url, evaluator.read_env(args.env_file))
        if args.apply and app.request("/api/auth/me").get("role") != "ADMIN":
            raise evaluator.EvaluationError("Canonical repair needs an existing ADMIN account; no role is changed.")
        print(json.dumps(reconcile(app, documents, args.apply), indent=4))
        return 0
    except (evaluator.EvaluationError, OSError, ValueError, KeyError) as error:
        print(str(error), file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
