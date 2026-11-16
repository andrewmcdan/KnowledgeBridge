#!/usr/bin/env python3
"""Build the numbered Markdown chapters into an offline, bookmarked user manual.

Run from any directory. See README.md beside this script for prerequisites and
the deliberately supported Markdown subset. No browser or external service is used.
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass
from datetime import date
from html import escape
import json
import os
from pathlib import Path
import re
import sys
import tempfile
import textwrap
from urllib.parse import quote, unquote, urlsplit

try:
    from markdown_it import MarkdownIt
    import reportlab
    from reportlab.lib import colors
    from reportlab.lib.enums import TA_LEFT
    from reportlab.lib.pagesizes import letter
    from reportlab.lib.styles import ParagraphStyle
    from reportlab.pdfbase import pdfmetrics
    from reportlab.pdfbase.ttfonts import TTFont
    from reportlab.platypus import (
        BaseDocTemplate, Frame, HRFlowable, Indenter, ListFlowable, ListItem,
        LongTable, NextPageTemplate, PageBreak, PageTemplate, Paragraph,
        Preformatted, Spacer, TableStyle,
    )
    from reportlab.platypus.tableofcontents import TableOfContents
except ImportError as exc:
    raise SystemExit(
        f"Missing PDF-build dependency: {exc.name}. Install the packages with:\n"
        f'  python -m pip install -r "{Path(__file__).with_name("requirements.txt")}"'
    ) from exc


HERE = Path(__file__).resolve().parent
PAGE_WIDTH, PAGE_HEIGHT = letter
MARGIN = 53
TEXT_WIDTH = PAGE_WIDTH - 2 * MARGIN
NAVY = colors.HexColor("#10243A")
TEAL = colors.HexColor("#007F86")
INK = colors.HexColor("#23364A")
MUTED = colors.HexColor("#5C6D7B")
PALE = colors.HexColor("#EFF5F7")
RULE = colors.HexColor("#DAE4E9")
SECTION_FILE = re.compile(r"^(\d{2})-[a-z0-9-]+\.md$")
BLOCK_TYPES = {
    "heading_open", "heading_close", "paragraph_open", "paragraph_close", "inline",
    "bullet_list_open", "bullet_list_close", "ordered_list_open", "ordered_list_close",
    "list_item_open", "list_item_close", "blockquote_open", "blockquote_close",
    "fence", "code_block", "hr", "table_open", "table_close", "thead_open", "thead_close",
    "tbody_open", "tbody_close", "tr_open", "tr_close", "th_open", "th_close", "td_open", "td_close",
}
INLINE_TYPES = {
    "text", "softbreak", "hardbreak", "code_inline", "strong_open", "strong_close",
    "em_open", "em_close", "link_open", "link_close",
}


class ManualError(ValueError):
    """Actionable invalid-source or configuration error."""


@dataclass
class Chapter:
    path: Path
    tokens: list
    title: str


def plain_inline(token) -> str:
    return "".join(
        " " if child.type in {"softbreak", "hardbreak"} else child.content
        for child in (token.children or [])
        if child.type in {"text", "code_inline", "softbreak", "hardbreak"}
    )


def slug(text: str) -> str:
    return re.sub(r"[^\w\s-]", "", text.lower()).strip().replace(" ", "-")


def close_at(tokens: list, start: int) -> int:
    depth = 0
    for index in range(start, len(tokens)):
        depth += tokens[index].nesting
        if depth == 0:
            return index
    raise ManualError(f"Unclosed Markdown block: {tokens[start].type}")


class Sources:
    def __init__(self, directory: Path):
        self.directory = directory.resolve()
        self.repo_root = self.directory.parent.parent
        try:
            self.metadata = json.loads((self.directory / "manual.json").read_text(encoding="utf-8"))
        except (OSError, ValueError) as exc:
            raise ManualError(f"Cannot read manual.json: {exc}") from exc
        for field in ("title", "subtitle", "edition", "publication_date", "source_revision", "repository_url"):
            value = self.metadata.get(field)
            if not isinstance(value, str) or not value.strip() or "\n" in value:
                raise ManualError(f"manual.json requires a nonempty single-line {field!r}.")
        try:
            self.published = date.fromisoformat(self.metadata["publication_date"])
        except ValueError as exc:
            raise ManualError("publication_date must be a calendar date in YYYY-MM-DD form.") from exc
        if not re.fullmatch(r"[0-9a-fA-F]{7,40}", self.metadata["source_revision"]):
            raise ManualError("source_revision must be the inspected product commit (7-40 hexadecimal digits).")
        if not self.metadata["repository_url"].startswith("https://"):
            raise ManualError("repository_url must use HTTPS.")

        parser = MarkdownIt("commonmark", {"html": False}).enable("table")
        paths = []
        for path in sorted(self.directory.glob("*.md")):
            if path.name == "README.md":
                continue
            if not SECTION_FILE.fullmatch(path.name):
                raise ManualError(f"Chapter filename must be NN-lowercase-name.md: {path.name}")
            paths.append(path)
        if not paths:
            raise ManualError("No numbered Markdown chapters found.")
        numbers = [int(SECTION_FILE.fullmatch(path.name).group(1)) for path in paths]
        if numbers != list(range(1, len(numbers) + 1)):
            raise ManualError("Chapter numbers must be unique and consecutive, starting at 01.")

        self.chapters = []
        self.anchors = {}
        self.fragments = {}
        self.chapter_targets = {}
        self.link_count = 0
        self.heading_count = 0
        for number, path in zip(numbers, paths):
            raw = path.read_bytes()
            if b"\r" in raw or not raw.endswith(b"\n"):
                raise ManualError(f"Use LF line endings and a final newline: {path.name}")
            text = raw.decode("utf-8")
            tokens = parser.parse(text)
            headings = [i for i, token in enumerate(tokens) if token.type == "heading_open"]
            if not headings or headings[0] != 0 or sum(tokens[i].tag == "h1" for i in headings) != 1:
                raise ManualError(f"Start with exactly one H1 chapter title: {path.name}")
            title = plain_inline(tokens[1])
            if not title.startswith(f"{number}. "):
                raise ManualError(f"H1 must start with '{number}. ': {path.name}")
            self.chapters.append(Chapter(path, tokens, title))
            seen = {}
            for index in headings:
                heading_slug = slug(plain_inline(tokens[index + 1]))
                occurrence = seen.get(heading_slug, 0)
                seen[heading_slug] = occurrence + 1
                fragment = heading_slug + (f"-{occurrence}" if occurrence else "")
                anchor = f"{path.stem}--{fragment}"
                self.anchors[(path.name, index)] = anchor
                self.fragments[(path.name, fragment)] = anchor
                self.heading_count += 1
                if tokens[index].tag == "h1":
                    self.chapter_targets[path.name] = anchor
        # Plan every destination before resolving forward references.
        for chapter in self.chapters:
            for token in chapter.tokens:
                if token.type not in BLOCK_TYPES:
                    raise ManualError(f"Unsupported Markdown block {token.type!r} in {chapter.path.name}.")
                for child in token.children or []:
                    if child.type not in INLINE_TYPES:
                        raise ManualError(f"Unsupported inline content {child.type!r} in {chapter.path.name}.")
                    if child.type == "link_open":
                        self.resolve_link(chapter.path, child.attrGet("href"))
                        self.link_count += 1

    def resolve_link(self, source: Path, target: str) -> str:
        parts = urlsplit(target)
        if parts.scheme:
            if parts.scheme not in {"http", "https", "mailto"}:
                raise ManualError(f"Unsupported link scheme in {source.name}: {target}")
            return target
        if parts.netloc:
            raise ManualError(f"Use an explicit HTTPS URL in {source.name}: {target}")
        path = (source.parent / unquote(parts.path)).resolve() if parts.path else source
        if not path.is_relative_to(self.repo_root) or not path.is_file():
            raise ManualError(f"Missing or out-of-repository link in {source.name}: {target}")
        if path.parent == self.directory and path.name in self.chapter_targets:
            if parts.fragment:
                key = (path.name, unquote(parts.fragment))
                if key not in self.fragments:
                    raise ManualError(f"Missing heading destination in {source.name}: {target}")
                return "#" + self.fragments[key]
            return "#" + self.chapter_targets[path.name]
        relative = path.relative_to(self.repo_root).as_posix()
        base = self.metadata["repository_url"].rstrip("/")
        url = f"{base}/blob/{self.metadata['source_revision']}/{quote(relative, safe='/')}"
        return url + ("#" + quote(unquote(parts.fragment), safe="-") if parts.fragment else "")


def register_fonts() -> None:
    font_dir = Path(reportlab.__file__).parent / "fonts"
    for name, filename in (
        ("ManualSans", "Vera.ttf"), ("ManualSans-Bold", "VeraBd.ttf"),
        ("ManualSans-Italic", "VeraIt.ttf"), ("ManualSans-BoldItalic", "VeraBI.ttf"),
    ):
        if name not in pdfmetrics.getRegisteredFontNames():
            pdfmetrics.registerFont(TTFont(name, str(font_dir / filename)))
    pdfmetrics.registerFontFamily(
        "ManualSans", normal="ManualSans", bold="ManualSans-Bold",
        italic="ManualSans-Italic", boldItalic="ManualSans-BoldItalic",
    )


def styles() -> dict:
    body = ParagraphStyle(
        "Body", fontName="ManualSans", fontSize=9.6, leading=14.5,
        textColor=INK, spaceAfter=8, allowWidows=0, allowOrphans=0,
        splitLongWords=True, alignment=TA_LEFT,
    )
    result = {"Body": body}
    result["List"] = ParagraphStyle("List", parent=body, spaceAfter=4)
    result["Callout"] = ParagraphStyle(
        "Callout", parent=body, backColor=PALE, borderPadding=9,
        spaceBefore=6, spaceAfter=12, textColor=NAVY,
    )
    for level, size, leading in ((1, 23, 29), (2, 13.4, 18), (3, 11, 16)):
        result[f"H{level}"] = ParagraphStyle(
            f"H{level}", parent=body, fontName="ManualSans-Bold", fontSize=size,
            leading=leading, textColor=NAVY if level == 1 else TEAL,
            spaceBefore=0 if level == 1 else 13, spaceAfter=12 if level == 1 else 7,
            keepWithNext=True,
        )
    result["Table"] = ParagraphStyle("Table", parent=body, fontSize=8.5, leading=12, spaceAfter=0)
    result["TableHead"] = ParagraphStyle("TableHead", parent=result["Table"], fontName="ManualSans-Bold", textColor=colors.white)
    result["Code"] = ParagraphStyle(
        "Code", parent=body, fontName="Courier", fontSize=8, leading=11,
        backColor=PALE, borderPadding=8, spaceBefore=4, spaceAfter=12,
    )
    result["Small"] = ParagraphStyle("Small", parent=body, fontSize=8.4, leading=12, textColor=MUTED)
    return result


class Heading(Paragraph):
    def __init__(self, text, style, title, anchor, level):
        super().__init__(text, style)
        self.title = title
        self.anchor = anchor
        self.level = level


class ManualContents(TableOfContents):
    def wrap(self, available_width, available_height):
        result = super().wrap(available_width, available_height)
        row = 0
        for index, entry in enumerate(self._lastEntries):
            level = entry[0]
            if self.getLevelStyle(level).spaceBefore:
                row += 1
            if level == 0 and index + 1 < len(self._lastEntries) and self._lastEntries[index + 1][0] == 1:
                # Keep a chapter entry with its first subsection when contents span pages.
                self._table.setStyle(TableStyle([("NOSPLIT", (0, row), (-1, row + 1))]))
            row += 1
        return result


class ManualDocument(BaseDocTemplate):
    def __init__(self, filename: str, source: Sources, style: dict):
        super().__init__(
            filename, pagesize=letter, leftMargin=MARGIN, rightMargin=MARGIN,
            topMargin=58, bottomMargin=49, title=f"{source.metadata['title']} User Manual",
            author="KnowledgeBridge project", subject=source.metadata["subtitle"],
            pageCompression=1,
        )
        self.source = source
        self.style = style
        frame = lambda name: Frame(MARGIN, 49, TEXT_WIDTH, PAGE_HEIGHT - 107, id=name, leftPadding=0, rightPadding=0, topPadding=0, bottomPadding=0)
        self.addPageTemplates([
            PageTemplate(id="Cover", frames=[frame("cover")], onPage=self.draw_cover),
            PageTemplate(id="Body", frames=[frame("body")], onPage=self.draw_page),
        ])

    def draw_cover(self, canvas, _doc):
        canvas.saveState()
        canvas.setFillColor(NAVY)
        canvas.rect(0, 0, PAGE_WIDTH, PAGE_HEIGHT, fill=1, stroke=0)
        canvas.setFillColor(colors.HexColor("#173C53"))
        canvas.circle(560, 610, 218, fill=1, stroke=0)
        canvas.setStrokeColor(colors.HexColor("#347186"))
        canvas.setLineWidth(1.2)
        for offset in (0, 20, 40, 60):
            path = canvas.beginPath()
            path.moveTo(70, 540 + offset)
            path.curveTo(210, 730 + offset, 370, 420 + offset, 550, 635 + offset)
            canvas.drawPath(path)
        canvas.setFillColor(colors.HexColor("#7BE0D4"))
        canvas.setFont("ManualSans-Bold", 15)
        canvas.drawString(MARGIN, 727, self.source.metadata["title"].upper())
        canvas.setFillColor(colors.white)
        canvas.setFont("ManualSans-Bold", 42)
        canvas.drawString(MARGIN, 411, "User manual")
        canvas.setFont("ManualSans", 13)
        canvas.drawString(MARGIN, 379, self.source.metadata["subtitle"])
        canvas.setFillColor(colors.HexColor("#7BE0D4"))
        canvas.rect(MARGIN, 349, 78, 4, fill=1, stroke=0)
        canvas.setFillColor(colors.HexColor("#DBE9EF"))
        canvas.setFont("ManualSans", 11)
        canvas.drawString(MARGIN, 301, "Find knowledge. Follow the evidence.")
        canvas.setFont("ManualSans", 9)
        canvas.drawString(MARGIN, 121, f"EDITION {self.source.metadata['edition']}  /  {self.source.published.strftime('%B %d, %Y')}")
        canvas.setFont("ManualSans", 8)
        canvas.drawString(MARGIN, 95, "WORKSPACE KNOWLEDGE  /  SEARCH  /  CITED ANSWERS")
        canvas.restoreState()

    def draw_page(self, canvas, _doc):
        canvas.saveState()
        canvas.setFont("ManualSans-Bold", 8)
        canvas.setFillColor(NAVY)
        canvas.drawString(MARGIN, PAGE_HEIGHT - 32, self.source.metadata["title"])
        canvas.setFont("ManualSans", 8)
        canvas.setFillColor(MUTED)
        canvas.drawRightString(PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 32, "USER MANUAL")
        canvas.setStrokeColor(RULE)
        canvas.line(MARGIN, PAGE_HEIGHT - 41, PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 41)
        canvas.line(MARGIN, 38, PAGE_WIDTH - MARGIN, 38)
        canvas.setFont("ManualSans", 7.5)
        canvas.drawString(MARGIN, 24, f"Edition {self.source.metadata['edition']}  |  {self.source.published.strftime('%B %Y')}")
        canvas.drawRightString(PAGE_WIDTH - MARGIN, 24, f"PAGE {self.page}")
        canvas.restoreState()

    def afterFlowable(self, flowable):
        if isinstance(flowable, Heading):
            self.canv.bookmarkPage(flowable.anchor)
            if flowable.level <= 2:
                level = flowable.level - 1
                self.canv.addOutlineEntry(flowable.title, flowable.anchor, level=level, closed=True)
                self.notify("TOCEntry", (level, escape(flowable.title), self.page, flowable.anchor))


class Renderer:
    def __init__(self, source: Sources, style: dict):
        self.source = source
        self.style = style

    def inline(self, token, path: Path) -> str:
        pieces = []
        for child in token.children or []:
            kind = child.type
            if kind == "text":
                pieces.append(escape(child.content))
            elif kind == "code_inline":
                pieces.append(f'<font name="Courier" color="#154A65">{escape(child.content)}</font>')
            elif kind == "softbreak":
                pieces.append(" ")
            elif kind == "hardbreak":
                pieces.append("<br/>")
            elif kind in {"strong_open", "strong_close", "em_open", "em_close"}:
                pieces.append({"strong_open": "<b>", "strong_close": "</b>", "em_open": "<i>", "em_close": "</i>"}[kind])
            elif kind == "link_open":
                target = escape(self.source.resolve_link(path, child.attrGet("href")), quote=True)
                pieces.append(f'<link href="{target}" color="#007F86"><u>')
            elif kind == "link_close":
                pieces.append("</u></link>")
            else:
                raise ManualError(f"Unsupported inline content: {kind}")
        return "".join(pieces)

    def table(self, chapter: Chapter, start: int, end: int):
        rows, row, header = [], [], True
        for index in range(start + 1, end):
            token = chapter.tokens[index]
            if token.type in {"th_open", "td_open"}:
                style = self.style["TableHead" if header else "Table"]
                row.append(Paragraph(self.inline(chapter.tokens[index + 1], chapter.path), style))
            elif token.type == "tr_close":
                rows.append(row)
                row = []
            elif token.type == "thead_close":
                header = False
        columns = len(rows[0])
        if columns == 2:
            widths = [TEXT_WIDTH * 0.32, TEXT_WIDTH * 0.68]
        elif columns == 3:
            widths = [TEXT_WIDTH * 0.25, TEXT_WIDTH * 0.43, TEXT_WIDTH * 0.32]
        else:
            widths = [TEXT_WIDTH / columns] * columns
        table = LongTable(rows, colWidths=widths, repeatRows=1, hAlign="LEFT", spaceBefore=5, spaceAfter=12)
        table.setStyle(TableStyle([
            ("BACKGROUND", (0, 0), (-1, 0), NAVY),
            ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, PALE]),
            ("VALIGN", (0, 0), (-1, -1), "TOP"),
            ("LEFTPADDING", (0, 0), (-1, -1), 8),
            ("RIGHTPADDING", (0, 0), (-1, -1), 8),
            ("TOPPADDING", (0, 0), (-1, -1), 8),
            ("BOTTOMPADDING", (0, 0), (-1, -1), 8),
            ("LINEBELOW", (0, 0), (-1, 0), 0.7, TEAL),
            ("LINEBELOW", (0, 1), (-1, -1), 0.35, RULE),
        ]))
        return table

    def blocks(self, chapter: Chapter, start=0, end=None, paragraph_style="Body") -> list:
        tokens = chapter.tokens
        end = len(tokens) if end is None else end
        story, index = [], start
        while index < end:
            token = tokens[index]
            kind = token.type
            if kind == "heading_open":
                level = int(token.tag[1:])
                if level > 3:
                    raise ManualError(f"Use only H1-H3 headings in {chapter.path.name}.")
                title = plain_inline(tokens[index + 1])
                story.append(Heading(self.inline(tokens[index + 1], chapter.path), self.style[f"H{level}"], title, self.source.anchors[(chapter.path.name, index)], level))
                index += 3
            elif kind == "paragraph_open":
                story.append(Paragraph(self.inline(tokens[index + 1], chapter.path), self.style[paragraph_style]))
                index += 3
            elif kind in {"bullet_list_open", "ordered_list_open"}:
                closing = close_at(tokens, index)
                items, cursor = [], index + 1
                while cursor < closing:
                    item_end = close_at(tokens, cursor)
                    items.append(ListItem(self.blocks(chapter, cursor + 1, item_end, "List")))
                    cursor = item_end + 1
                ordered = kind == "ordered_list_open"
                story.append(ListFlowable(
                    items, bulletType="1" if ordered else "bullet",
                    start=(token.attrGet("start") or "1") if ordered else "bulletchar",
                    leftIndent=19, bulletFontName="ManualSans", bulletFontSize=8.5,
                    spaceAfter=7,
                ))
                index = closing + 1
            elif kind == "blockquote_open":
                closing = close_at(tokens, index)
                story.extend([Indenter(11, 11), *self.blocks(chapter, index + 1, closing, "Callout"), Indenter(-11, -11)])
                index = closing + 1
            elif kind in {"fence", "code_block"}:
                lines = []
                for line in token.content.rstrip("\n").split("\n"):
                    lines.extend(textwrap.wrap(line.expandtabs(4), width=98, replace_whitespace=False, drop_whitespace=False, break_on_hyphens=False) or [""])
                story.append(Preformatted("\n".join(lines), self.style["Code"]))
                index += 1
            elif kind == "table_open":
                closing = close_at(tokens, index)
                story.append(self.table(chapter, index, closing))
                index = closing + 1
            elif kind == "hr":
                story.append(HRFlowable(width="100%", thickness=0.6, color=RULE, spaceBefore=6, spaceAfter=10))
                index += 1
            else:
                raise ManualError(f"Unexpected Markdown block {kind!r} in {chapter.path.name}.")
        return story


def build(source: Sources, output: Path) -> dict:
    if output.suffix.lower() != ".pdf":
        raise ManualError("The output filename must have a .pdf extension.")
    register_fonts()
    style = styles()
    renderer = Renderer(source, style)
    toc = ManualContents()
    toc.levelStyles = [
        ParagraphStyle("TOCChapter", fontName="ManualSans-Bold", fontSize=9.5, leading=14, textColor=NAVY, leftIndent=0, firstLineIndent=0, spaceBefore=4),
        ParagraphStyle("TOCSection", fontName="ManualSans", fontSize=8.3, leading=11.3, textColor=INK, leftIndent=14, firstLineIndent=0, spaceBefore=0),
    ]
    toc.tableStyle = TableStyle([
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 0),
        ("RIGHTPADDING", (0, 0), (-1, -1), 0),
        ("TOPPADDING", (0, 0), (-1, -1), 0),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 0),
    ])
    toc.dotsMinLevel = 0
    story = [Spacer(1, 1), NextPageTemplate("Body"), PageBreak()]
    story.extend([
        Paragraph("Contents", style["H1"]),
        Paragraph("Select a chapter or subsection to jump to it. Page numbers match the PDF pages, including the cover and contents.", style["Small"]),
        Paragraph(f"Edition {escape(source.metadata['edition'])} | Published {source.published.isoformat()} | Product source {escape(source.metadata['source_revision'])}", style["Small"]),
        Spacer(1, 8), toc,
    ])
    for chapter in source.chapters:
        story.append(PageBreak())
        story.extend(renderer.blocks(chapter))
    output = output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(suffix=".pdf", dir=output.parent, delete=False) as handle:
            temporary = Path(handle.name)
        document = ManualDocument(str(temporary), source, style)
        document.multiBuild(story, maxPasses=8)
        os.replace(temporary, output)
    finally:
        if temporary is not None and temporary.exists():
            temporary.unlink()
    return {"output": str(output), "chapters": len(source.chapters), "headings": source.heading_count, "links": source.link_count, "pages": document.page}


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-dir", type=Path, default=HERE, help="Directory with manual.json and consecutive NN-*.md chapters.")
    parser.add_argument("--output", type=Path, help="Output PDF path; default: <source-dir>/output/pdf/KnowledgeBridge-User-Manual.pdf.")
    parser.add_argument("--check", action="store_true", help="Validate chapter order, structure, supported syntax, and links without writing a PDF.")
    args = parser.parse_args(argv)
    try:
        source = Sources(args.source_dir)
        if args.check:
            register_fonts()
            renderer = Renderer(source, styles())
            for chapter in source.chapters:
                renderer.blocks(chapter)
            result = {"valid": True, "chapters": len(source.chapters), "headings": source.heading_count, "links": source.link_count}
        else:
            output = args.output or source.directory / "output" / "pdf" / "KnowledgeBridge-User-Manual.pdf"
            result = build(source, output)
        print(json.dumps(result, indent=2))
        return 0
    except (ManualError, OSError, UnicodeError) as exc:
        print(f"Manual build failed: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
