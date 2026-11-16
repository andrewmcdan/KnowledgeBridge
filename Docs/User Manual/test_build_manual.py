"""Regression checks for source validation and complete PDF conversion."""

import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest


HERE = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("knowledgebridge_manual_builder", HERE / "build_manual.py")
builder = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = builder
SPEC.loader.exec_module(builder)

try:
    from pypdf import PdfReader
except ImportError:
    PdfReader = None


class ManualBuilderTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.directory = self.root / "Docs" / "User Manual"
        self.directory.mkdir(parents=True)
        metadata = json.loads((HERE / "manual.json").read_text(encoding="utf-8"))
        (self.directory / "manual.json").write_text(json.dumps(metadata), encoding="utf-8")

    def write(self, filename, content):
        path = self.directory / filename
        path.write_bytes(content.encode("utf-8"))
        return path

    def source(self, content="# 1. Example\n\nA complete paragraph.\n"):
        self.write("01-example.md", content)
        return builder.Sources(self.directory)

    def test_current_manual_has_all_ordered_sections_and_valid_links(self):
        source = builder.Sources(HERE)
        self.assertEqual(len(source.chapters), 17)
        self.assertEqual(len(source.chapter_targets), 17)
        self.assertGreater(source.link_count, 15)
        self.assertEqual([chapter.path.name[:2] for chapter in source.chapters], [f"{n:02}" for n in range(1, 18)])

    def test_missing_or_duplicate_numbers_cannot_drop_or_reorder_a_section(self):
        self.source()
        self.write("03-gap.md", "# 3. Gap\n")
        with self.assertRaisesRegex(builder.ManualError, "unique and consecutive"):
            builder.Sources(self.directory)
        (self.directory / "03-gap.md").unlink()
        self.write("01-duplicate.md", "# 1. Duplicate\n")
        with self.assertRaisesRegex(builder.ManualError, "unique and consecutive"):
            builder.Sources(self.directory)

    def test_unnumbered_sections_and_wrong_or_extra_h1_titles_are_rejected(self):
        self.source()
        self.write("unlisted.md", "# A section\n")
        with self.assertRaisesRegex(builder.ManualError, "filename"):
            builder.Sources(self.directory)
        (self.directory / "unlisted.md").unlink()
        for content in ("# 2. Wrong number\n", "# 1. Example\n\n# Another chapter\n", "Introduction\n\n# 1. Example\n"):
            with self.subTest(content=content), self.assertRaises(builder.ManualError):
                self.source(content)

    def test_lf_and_final_newline_are_checked_on_original_bytes(self):
        for content in ("# 1. Example\r\n", "# 1. Example"):
            with self.subTest(content=content), self.assertRaisesRegex(builder.ManualError, "LF line endings"):
                self.source(content)

    def test_forward_chapter_and_fragment_links_become_distinct_pdf_destinations(self):
        self.write("01-example.md", "# 1. Example\n\n[Next](02-next.md) and [Detail](02-next.md#detail).\n")
        self.write("02-next.md", "# 2. Next\n\n## Detail\n\nFirst.\n\n## Detail\n\nSecond.\n")
        source = builder.Sources(self.directory)
        first = self.directory / "01-example.md"
        self.assertEqual(source.resolve_link(first, "02-next.md"), "#02-next--2-next")
        self.assertEqual(source.resolve_link(first, "02-next.md#detail"), "#02-next--detail")
        self.assertEqual(source.resolve_link(first, "02-next.md#detail-1"), "#02-next--detail-1")

    def test_missing_files_fragments_and_unsafe_links_do_not_silently_render(self):
        for target in ("missing.md", "01-example.md#missing", "../../../outside.md"):
            with self.subTest(target=target), self.assertRaises(builder.ManualError):
                self.source(f"# 1. Example\n\n[Broken]({target})\n")
        source = self.source()
        with self.assertRaisesRegex(builder.ManualError, "scheme"):
            source.resolve_link(self.directory / "01-example.md", "javascript:alert(1)")

    def test_repository_references_are_checked_and_pinned_not_machine_file_links(self):
        (self.root / "README.md").write_text("Project\n", encoding="utf-8")
        source = self.source("# 1. Example\n\n[Project](../../README.md)\n")
        href = source.resolve_link(source.chapters[0].path, "../../README.md")
        self.assertIn(f"/blob/{source.metadata['source_revision']}/README.md", href)
        self.assertTrue(href.startswith("https://"))

    def test_unsupported_images_fail_instead_of_disappearing(self):
        with self.assertRaisesRegex(builder.ManualError, "image"):
            self.source("# 1. Example\n\n![A figure](figure.png)\n")

    def test_non_pdf_output_cannot_overwrite_markdown_source(self):
        source = self.source()
        path = source.chapters[0].path
        before = path.read_bytes()
        with self.assertRaisesRegex(builder.ManualError, "extension"):
            builder.build(source, path)
        self.assertEqual(path.read_bytes(), before)

    def test_pdf_contains_formatted_blocks_numbered_lists_and_working_destinations(self):
        self.write("01-example.md", """# 1. Example

## Formatting

**Strong evidence** and *emphasis*, `literal <text> & data`, and [next](02-next.md).

3. A deliberately non-default list number.
4. A second step with a nested list:
   - Nested evidence.

> Important: a readable callout.

| Format | Meaning |
| --- | --- |
| PDF | Preserved table content |

```powershell
Write-Output "A & B < C>"
```
""")
        self.write("02-next.md", "# 2. Next\n\n[Back](01-example.md#formatting).\n")
        output = self.root / "output" / "manual.pdf"
        result = builder.build(builder.Sources(self.directory), output)
        self.assertEqual(result["chapters"], 2)
        self.assertTrue(output.read_bytes().startswith(b"%PDF-"))
        self.assertGreaterEqual(result["pages"], 4)
        if PdfReader is None:
            return
        reader = PdfReader(output)
        text = "\n".join(page.extract_text() for page in reader.pages)
        for expected in ("Contents", "Strong evidence", "Nested evidence", "Preserved table content", 'Write-Output "A & B < C>"', "2. Next"):
            self.assertIn(expected, text)
        self.assertIn(chr(0x2022), text, "Use a bullet glyph present in the embedded font, not a missing-glyph box.")
        self.assertNotIn("Placeholder for table of contents", text)
        self.assertIn("3", reader.pages[2].extract_text())
        self.assertTrue(reader.outline)
        link_annotations = [annotation.get_object() for page in reader.pages for annotation in page.get("/Annots", []) if annotation.get_object().get("/Subtype") == "/Link"]
        self.assertGreaterEqual(len(link_annotations), 3)
        self.assertTrue(all("/Dest" in annotation or "/A" in annotation for annotation in link_annotations))

    @unittest.skipIf(PdfReader is None, "Optional pypdf PDF-structure assertions require pypdf.")
    def test_generated_manual_has_one_bookmark_for_every_contents_entry(self):
        # Exercise the production manual, not a hand-selected subset of its content.
        source = builder.Sources(HERE)
        output = self.root / "full-manual.pdf"
        result = builder.build(source, output)
        reader = PdfReader(output)

        def flatten(entries):
            for entry in entries:
                if isinstance(entry, list):
                    yield from flatten(entry)
                else:
                    yield entry

        entries = list(flatten(reader.outline))
        self.assertEqual(len(entries), source.heading_count)
        for chapter in source.chapters:
            bookmark = next(entry for entry in entries if entry.title == chapter.title)
            page = reader.pages[reader.get_destination_page_number(bookmark)]
            self.assertIn(chapter.title, " ".join(page.extract_text().split()))
        self.assertEqual(len(reader.pages), result["pages"])


if __name__ == "__main__":
    unittest.main()
