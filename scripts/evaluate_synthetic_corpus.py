"""Evaluate every canonical synthetic document and build a PDF from the checkpointed results."""

import argparse
from collections import Counter
from datetime import datetime, timezone
import io
import json
from pathlib import Path
import sys
import uuid
from xml.sax.saxutils import escape

import evaluate_synthesis as evaluator

ROOT = Path(__file__).resolve().parents[1]
DIMENSIONS = ("accuracy", "grounding", "completeness")


def pdf_dependencies():
    """Fail before paid requests if the optional reporting dependency is missing."""
    try:
        import reportlab
        from reportlab.lib import colors
        from reportlab.lib.pagesizes import A4
        from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
        from reportlab.pdfbase import pdfmetrics
        from reportlab.pdfbase.ttfonts import TTFont
        from reportlab.platypus import Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle
    except ImportError as error:
        raise evaluator.EvaluationError(
            "PDF reporting requires ReportLab. Run: python -m pip install -r scripts/requirements-evaluation.txt"
        ) from error
    return (reportlab, colors, A4, ParagraphStyle, getSampleStyleSheet,
            pdfmetrics, TTFont, Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle)


def corpus_ids():
    catalog = json.loads((ROOT / "synthetic-data/reference/catalog.json").read_text(encoding="utf-8"))
    documents = catalog.get("documents")
    if not isinstance(documents, list) or not documents:
        raise evaluator.EvaluationError("The synthetic catalog must contain documents.")
    ids = [entry["id"] for entry in documents]
    if (len(ids) != len(set(ids)) or catalog.get("document_count") != len(ids)
            or not all(isinstance(doc_id, str) and doc_id for doc_id in ids)):
        raise evaluator.EvaluationError("The synthetic catalog has invalid IDs or document counts.")
    # Explicit selection includes archived, draft and incomplete records; --count does not.
    evaluator.load_documents(ids, len(ids), 42)
    return ids


def load_report(path):
    report = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(report, dict) or report.get("report_format_version") != 2:
        raise evaluator.EvaluationError("PDF reporting requires an evaluator JSON report with report_format_version 2.")
    plan = report.get("plan", [])
    cases = report.get("cases", [])
    if not isinstance(plan, list) or not isinstance(cases, list):
        raise evaluator.EvaluationError("Invalid evaluation plan or cases.")
    planned = {}
    for item in plan:
        if (not isinstance(item, dict) or not isinstance(item.get("case_id"), str)
                or not isinstance(item.get("doc_ids"), list) or not item["doc_ids"]
                or not all(isinstance(doc_id, str) for doc_id in item["doc_ids"])
                or item["case_id"] in planned):
            raise evaluator.EvaluationError("Invalid or duplicate planned case.")
        planned[item["case_id"]] = item
    seen = set()
    for case in cases:
        if (not isinstance(case, dict) or case.get("case_id") not in planned
                or case["case_id"] in seen or case.get("doc_ids") != planned[case["case_id"]]["doc_ids"]
                or case.get("status") not in {"pass", "fail", "error", "running"}):
            raise evaluator.EvaluationError("Invalid, duplicate or unplanned result case.")
        seen.add(case["case_id"])
        if case.get("judgment_validated") and case["status"] in {"pass", "fail"}:
            scores = case.get("judgment", {}).get("scores", {})
            if any(type(scores.get(name)) is not int or not 0 <= scores[name] <= 4 for name in DIMENSIONS):
                raise evaluator.EvaluationError("Invalid validated judgment scores.")
        elif case["status"] == "pass":
            raise evaluator.EvaluationError("A passing case must have a validated judgment.")
    # Derive aggregates from the plan/results, rather than trusting stale checkpoint summaries.
    evaluator.update_summary(report)
    return report


def score_text(value):
    return "N/A" if value is None else f"{value:.2f}"


def case_scores(case):
    if case.get("judgment_validated") and case.get("status") in {"pass", "fail"}:
        return [str(case["judgment"]["scores"][name]) for name in DIMENSIONS]
    return ["N/A"] * len(DIMENSIONS)


def build_pdf(report, source, output):
    (reportlab, colors, A4, ParagraphStyle, getSampleStyleSheet,
     pdfmetrics, TTFont, Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle) = pdf_dependencies()
    # Bundle-compatible embedded fonts keep curly quotes/dashes and Latin names readable on Windows/Linux.
    font_dir = Path(reportlab.__file__).parent / "fonts"
    for name, filename in [("EvalSans", "Vera.ttf"), ("EvalSans-Bold", "VeraBd.ttf")]:
        if name not in pdfmetrics.getRegisteredFontNames():
            pdfmetrics.registerFont(TTFont(name, str(font_dir / filename)))
    pdfmetrics.registerFontFamily("EvalSans", normal="EvalSans", bold="EvalSans-Bold",
                                  italic="EvalSans", boldItalic="EvalSans-Bold")
    styles = getSampleStyleSheet()
    for name in ("Normal", "BodyText", "Title", "Heading1", "Heading2", "Heading3"):
        styles[name].fontName = "EvalSans-Bold" if name.startswith("Heading") or name == "Title" else "EvalSans"
    styles["BodyText"].fontSize = 8.5
    styles["BodyText"].leading = 12
    styles["BodyText"].spaceAfter = 5
    styles["Title"].fontSize = 23
    styles["Title"].leading = 29
    styles["Heading1"].fontSize = 15
    styles["Heading1"].leading = 20
    styles["Heading2"].fontSize = 11
    styles["Heading2"].leading = 15
    styles.add(ParagraphStyle("Cell", parent=styles["BodyText"], fontSize=7.5, leading=10, spaceAfter=0))
    styles.add(ParagraphStyle("CellHeader", parent=styles["Cell"], fontName="EvalSans-Bold", textColor=colors.white))
    story = []

    def paragraph(value, style="BodyText"):
        # Provider/document strings are text, never ReportLab markup or file/URL directives.
        value = "".join(c for c in str(value) if c in "\n\t" or ord(c) >= 32)
        return Paragraph(escape(value).replace("\n", "<br/>"), styles[style])

    def add(value, style="BodyText"):
        story.append(paragraph(value, style))

    def table(headers, rows, widths):
        data = [[paragraph(cell, "CellHeader") for cell in headers]]
        data.extend([[paragraph(cell, "Cell") for cell in row] for row in rows])
        result = Table(data, colWidths=widths, repeatRows=1, hAlign="LEFT", splitByRow=1, splitInRow=1)
        result.setStyle(TableStyle([
            ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#18364d")),
            ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#f1f5f8")]),
            ("VALIGN", (0, 0), (-1, -1), "TOP"),
            ("LEFTPADDING", (0, 0), (-1, -1), 6),
            ("RIGHTPADDING", (0, 0), (-1, -1), 6),
            ("TOPPADDING", (0, 0), (-1, -1), 6),
            ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
            ("LINEBELOW", (0, 0), (-1, 0), 0.5, colors.HexColor("#18364d")),
        ]))
        story.extend([result, Spacer(1, 10)])

    width = A4[0] - 80
    totals = report["summary"]
    documents = {doc["doc_id"]: doc for doc in report.get("selected_documents", [])}
    cases = {case["case_id"]: case for case in report.get("cases", [])}
    running = sum(case.get("status") == "running" for case in cases.values())
    add("Synthesis evaluation report", "Title")
    add(f"Run {report.get('run_id', 'unknown')} | Status: {report.get('status', 'unknown').upper()}")
    add(f"Started: {report.get('started_at', 'N/A')}\nFinished: {report.get('finished_at', 'Not finished')}")
    add("Overview", "Heading1")
    table(["Documents", "Planned", "Passed", "Failed", "Errors", "Not run*"], [[
        len(report["document_summaries"]), totals["planned"], totals["passed"], totals["failed"],
        totals["errors"], totals["not_run"],
    ]], [width / 6] * 6)
    rate = "N/A" if totals["pass_rate"] is None else f"{totals['pass_rate']:.1%}"
    add(f"Pass rate: {rate} (passed / planned). Completed: {totals['completed']}/{totals['planned']}. "
        f"Validated scored cases: {totals['scored_cases']}. Cache hits: {totals['cache_hits']}.")
    add(f"*Not run includes {running} in-progress/unresolved case(s) in this snapshot. "
        "Errors and unrun cases are inconclusive; they do not count as passes or receive zero scores.")
    if report.get("status") == "dry_run":
        add("DRY RUN - local corpus and query plan only. No application, synthesis or evaluator calls were made. No quality results exist.")
    if report.get("error"):
        add("Run error: " + report["error"])
    table(["Average accuracy / 4", "Average grounding / 4", "Average completeness / 4"], [[
        score_text(totals["average_scores"][name]) for name in DIMENSIONS
    ]], [width / 3] * 3)
    add("Method and scope", "Heading1")
    add(f"Mode: {report.get('mode', 'unknown')}; queries per document: {report.get('queries_per_doc', 'N/A')}; "
        f"reference date: {report.get('as_of', 'N/A')}; rubric: {report.get('rubric_version', 'N/A')}.")
    counts = Counter(doc.get("status", "unknown") for doc in documents.values())
    add("Selected document statuses: " + (", ".join(f"{key}: {value}" for key, value in sorted(counts.items())) or "none"))
    add(f"Evaluator: {report.get('provider', 'not selected')}; "
        f"generator: {report.get('models', {}).get('generator') or 'not selected'}; "
        f"judge: {report.get('models', {}).get('judge') or 'not selected'}.")
    add(f"Minimum score: {report.get('min_score', 3)}/4 in every dimension; fresh synthesis required: "
        f"{'yes' if report.get('require_fresh') else 'no'}. "
        f"Recorded evaluator calls: {len(report.get('evaluator_calls', []))}.")
    add("A pass also requires every selected source to be cited, an AI-synthesized answer, every applicable "
        "essential criterion supported, and no material unsupported claims or contradictions. Effective scores "
        "include the evaluator's consistency caps. Averages use only validated pass/fail judgments; averages "
        "cannot override a failed criterion. AI-generated questions and judging require human review and "
        "do not replace the fixed reliability question set.")
    add(f"Full questions, answers, evidence, raw judgments and provider usage remain in the JSON audit report: {source.resolve()}")

    add("Results by document", "Heading1")
    rows = []
    for doc_id, summary in report["document_summaries"].items():
        document = documents.get(doc_id, {})
        rows.append([f"{doc_id}\n{document.get('title', '')}", summary["planned"], summary["passed"],
                     summary["failed"], summary["errors"], summary["not_run"],
                     " / ".join(score_text(summary["average_scores"][name]) for name in DIMENSIONS)])
    if rows:
        table(["Document", "Plan", "Pass", "Fail", "Error", "Not run", "Avg A / G / C"], rows,
              [width - 290, 32, 32, 32, 35, 43, 116])
    else:
        add("No document plan was recorded.")

    add("Detailed query scores", "Heading1")
    add("A = accuracy; G = grounding; C = completeness. All scores use a 0-4 scale. N/A means no validated, completed judgment.")
    # Include every planned query, including those absent from an interrupted checkpoint.
    for doc_id in report["document_summaries"]:
        document = documents.get(doc_id, {})
        add(f"{doc_id} - {document.get('title', 'Document')}", "Heading2")
        planned = [item for item in report["plan"] if doc_id in item["doc_ids"]]
        table(["Case", "Result", "A", "G", "C", "Cache"], [[
            item["case_id"], cases.get(item["case_id"], {}).get("status", "not_run").upper(),
            *case_scores(cases.get(item["case_id"], {})),
            {True: "Hit", False: "Fresh"}.get(cases.get(item["case_id"], {}).get("synthesis", {}).get("cacheHit"), "N/A"),
        ] for item in planned], [width - 248, 76, 32, 32, 32, 76])
        for item in planned:
            case = cases.get(item["case_id"])
            if not case:
                continue
            add(f"{item['case_id']} - {case['status'].upper()}", "Heading3")
            add("Question: " + case.get("generated", {}).get("question", "Not generated."))
            if case.get("error"):
                add(f"Error at {case.get('stage', 'unknown')}: {case['error']}")
            for failure in case.get("failures", []):
                add("Failure: " + failure)
            citation = case.get("citation_check")
            if citation:
                add("Selected-source citation check: " + ("passed" if citation.get("all_selected_cited") else
                    "missing " + ", ".join(citation.get("missing_doc_ids", []))))
            judgment = case.get("judgment", {})
            if not case.get("judgment_validated"):
                if judgment:
                    add("The raw judgment did not pass validation. Its scores are excluded.")
                continue
            add("Judge summary: " + judgment.get("summary", "N/A"))
            if judgment.get("raw_scores"):
                add("Raw A / G / C: " + " / ".join(str(judgment["raw_scores"].get(name, "N/A")) for name in DIMENSIONS))
            for adjustment in judgment.get("score_adjustments", []):
                add("Score adjustment: " + adjustment)
            generated = {i: criterion for i, criterion in enumerate(case.get("generated", {}).get("criteria", []), 1)}
            for criterion in judgment.get("criteria", []):
                claim = generated.get(criterion.get("index"), {}).get("claim", "")
                add(f"Criterion {criterion.get('index', '?')} [{criterion.get('verdict', 'unknown')}]: "
                    f"{claim}\n{criterion.get('rationale', '')}")
            for key, label in [("unsupported_claims", "Unsupported claim"), ("contradictions", "Contradiction")]:
                for finding in judgment.get(key, []):
                    add(f"{label}: {finding}")

    def page_footer(canvas, document):
        canvas.saveState()
        canvas.setFont("EvalSans", 7)
        canvas.setFillColor(colors.HexColor("#526679"))
        canvas.drawString(40, 24, "KnowledgeBridge | Synthesis evaluation")
        canvas.drawRightString(A4[0] - 40, 24, f"Page {document.page}")
        canvas.restoreState()

    buffer = io.BytesIO()
    pdf = SimpleDocTemplate(buffer, pagesize=A4, rightMargin=40, leftMargin=40,
                            topMargin=38, bottomMargin=42, title="KnowledgeBridge synthesis evaluation",
                            author="KnowledgeBridge", pageCompression=1)
    pdf.build(story, onFirstPage=page_footer, onLaterPages=page_footer)
    # Build completely before reserving the final filename; never overwrite any existing artifact.
    with output.open("xb") as stream:
        stream.write(buffer.getbuffer())


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--queries-per-doc", type=int, choices=range(1, 11), default=1, metavar="1-10")
    parser.add_argument("--output-dir", type=Path, help="New run artifacts directory (default: evaluation/runs/corpus-<run-id>).")
    parser.add_argument("--from-json", type=Path, help="Build only a PDF from an existing version-2 JSON report; no network calls.")
    parser.add_argument("--pdf-output", type=Path, help="PDF path for --from-json (default: beside the JSON with .pdf extension).")
    parser.add_argument("--api-url", default="http://127.0.0.1:8080")
    parser.add_argument("--env-file", type=Path, default=ROOT / ".env")
    parser.add_argument("--generator-model")
    parser.add_argument("--judge-model")
    parser.add_argument("--min-score", type=int, choices=range(3, 5), default=3)
    parser.add_argument("--require-fresh", action="store_true")
    parser.add_argument("--max-context-chars", type=int, default=120000)
    parser.add_argument("--dry-run", action="store_true", help="Validate all corpus files and build a plan/PDF without network calls.")
    args = parser.parse_args(argv)
    if args.pdf_output and not args.from_json:
        parser.error("--pdf-output requires --from-json; use --output-dir for a new evaluation.")
    if args.from_json and (args.output_dir or args.dry_run):
        parser.error("--from-json cannot be combined with --output-dir or --dry-run.")
    try:
        pdf_dependencies()
        if args.from_json:
            source = args.from_json
            output = args.pdf_output or source.with_suffix(".pdf")
            code = 0
        else:
            ids = corpus_ids()
            run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:8]
            directory = args.output_dir or ROOT / "evaluation/runs" / ("corpus-" + run_id)
            directory.mkdir(parents=True, exist_ok=True)
            source, output = directory / "results.json", directory / "report.pdf"
            if source.exists() or output.exists():
                raise evaluator.EvaluationError("Output artifacts already exist; choose a new --output-dir.")
            arguments = ["--output", str(source), "--queries-per-doc", str(args.queries_per_doc),
                         "--api-url", args.api_url, "--env-file", str(args.env_file),
                         "--min-score", str(args.min_score), "--max-context-chars", str(args.max_context_chars)]
            for doc_id in ids:
                arguments.extend(["--doc-id", doc_id])
            for option, value in [("--generator-model", args.generator_model), ("--judge-model", args.judge_model)]:
                if value:
                    arguments.extend([option, value])
            for option, enabled in [("--require-fresh", args.require_fresh), ("--dry-run", args.dry_run)]:
                if enabled:
                    arguments.append(option)
            print(f"Entire corpus: {len(ids)} documents, {len(ids) * args.queries_per_doc} planned queries.", flush=True)
            # Reuse the existing CLI, including its source checks, progress, checkpoints, cancellation and exit codes.
            code = evaluator.main(arguments)
        if output.exists():
            raise evaluator.EvaluationError("PDF already exists; choose a new --pdf-output path.")
        report = load_report(source)
        output.parent.mkdir(parents=True, exist_ok=True)
        try:
            build_pdf(report, source, output)
        except Exception as error:
            raise evaluator.EvaluationError(f"PDF generation failed ({type(error).__name__}); the JSON report is preserved.") from error
        print(f"PDF: {output.resolve()}")
        return code
    except (evaluator.EvaluationError, OSError, ValueError, KeyError, TypeError) as error:
        print(f"Corpus evaluation/report failed: {error}", file=sys.stderr)
        print("Any saved JSON checkpoints are preserved. Use --from-json to build a PDF without repeating calls.", file=sys.stderr)
        return 2
    except KeyboardInterrupt:
        print("Reporting interrupted. Use --from-json with the saved checkpoint to build the PDF.", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
