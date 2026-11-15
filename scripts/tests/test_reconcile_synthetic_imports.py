"""A repair can add a canonical copy; it never edits/deletes or silently normalizes substantive changes."""
from contextlib import redirect_stdout
import io
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

with patch.object(sys, "path", [str(Path(__file__).resolve().parents[1]), *sys.path]):
    import reconcile_synthetic_imports as repair


class RepairTests(unittest.TestCase):
    def test_preview_and_apply_only_for_blank_line_differences(self):
        class App:
            def request(self, path):
                return [{"id": 1, "status": "COMPLETED"}]
            def content(self, item_id, fresh=False):
                return {"id": item_id, "text": "# Policy\nRule: four hours.\n"}
        document = {"doc_id": "ACME-013", "text": "# Policy\n\nRule: four hours.\n"}
        with patch.object(repair, "upload", return_value={"id": 2}) as upload:
            self.assertEqual(repair.reconcile(App(), [document])[0]["status"], "formatting_only_difference")
            upload.assert_not_called()
            result = repair.reconcile(App(), [document], apply=True)
            self.assertEqual(result[0]["status"], "canonical_copy_queued")
            self.assertEqual(result[0]["existing_item_ids"], [1])
            upload.assert_called_once()
        document["text"] = "# Policy\nRule: five hours.\n"
        with self.assertRaisesRegex(repair.evaluator.EvaluationError, "no formatting-only matching"):
            repair.reconcile(App(), [document], apply=True)

    def test_an_existing_canonical_copy_is_not_imported_again(self):
        class App:
            def request(self, path):
                return [{"id": 1, "status": "COMPLETED"}]
            def content(self, item_id, fresh=False):
                return {"id": item_id, "text": "Canonical body"}
        with patch.object(repair, "upload") as upload:
            result = repair.reconcile(App(), [{"doc_id": "ACME-001", "text": "Canonical body"}], apply=True)
        self.assertEqual(result[0]["status"], "already_matches")
        upload.assert_not_called()


if __name__ == "__main__":
    unittest.main()
