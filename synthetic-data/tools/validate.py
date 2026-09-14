"""Validate the checked-in corpus and evaluation fixtures using Python's standard library."""
import hashlib
import json
import re
import sys
from collections import Counter
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EXPECTED_CATEGORIES = {'01-company': 5, '02-people': 5, '03-policies': 8, '04-projects': 7, '05-meetings': 8, '06-architecture': 5, '07-incidents': 4, '08-products': 4, '09-training': 2, '10-finance-vendors': 2}
EXPECTED_QUESTIONS = {'direct_lookup': 15, 'multi_document': 10, 'process_policy': 10, 'entity_relationship': 5, 'unsupported_gap': 8, 'ambiguous': 4, 'contradictory_source': 4, 'outdated_source': 4}
STATUSES = {'current', 'outdated', 'incomplete', 'intentionally_contradictory'}

def validate():
    errors = []
    def check(condition, message):
        if not condition:
            errors.append(message)
    catalog = json.loads((ROOT / 'reference/catalog.json').read_text(encoding='utf-8'))
    records = catalog['documents']
    ids = {record['id'] for record in records}
    paths = {record['path'] for record in records}
    actual = {path.relative_to(ROOT).as_posix() for path in (ROOT / 'documents').rglob('*.md')}
    check(len(records) == 50 and catalog['document_count'] == 50, 'Expected 50 catalog records')
    check(len(ids) == 50 and len(paths) == 50, 'Duplicate document ID or path')
    check(ids == {f'ACME-{index:03}' for index in range(1, 51)}, 'Document ID sequence differs')
    check(actual == paths, f'Catalog/file mismatch: {actual ^ paths}')
    check(Counter(record['category'] for record in records) == EXPECTED_CATEGORIES, 'Document category distribution differs')
    word_counts = []
    for record in records:
        path = ROOT / record['path']
        if not path.is_file():
            continue
        check(path.resolve().is_relative_to((ROOT / 'documents').resolve()), f'{path}: outside ingestion folder')
        text = path.read_text(encoding='utf-8')
        parts = text.split('---\n', 2)
        check(len(parts) == 3 and parts[0] == '', f'{path}: missing front matter')
        if len(parts) != 3:
            continue
        metadata = {}
        try:
            for line in parts[1].splitlines():
                key, value = line.split(': ', 1)
                metadata[key] = json.loads(value)
            for field in ['id','title','document_type','owner','created','updated','department','status','synthetic','related_documents','related_people','related_systems','projects']:
                check(field in metadata, f'{path}: missing {field}')
            for field in ['id','title','document_type','owner','updated','status','related_documents']:
                check(metadata.get(field) == record[field], f'{path}: catalog mismatch for {field}')
            check(metadata['synthetic'] is True, f'{path}: synthetic flag missing')
            check(metadata['status'] in STATUSES, f'{path}: invalid status')
            check(date.fromisoformat(metadata['created']) <= date.fromisoformat(metadata['updated']) <= date.fromisoformat(catalog['as_of']), f'{path}: invalid chronology')
            check(all(other in ids and other != record['id'] for other in metadata['related_documents']), f'{path}: invalid related ID')
            if metadata['status'] == 'outdated':
                check(metadata.get('superseded_by') in ids, f'{path}: missing supersession')
        except (ValueError, KeyError, TypeError) as error:
            errors.append(f'{path}: metadata error: {error}')
        check(hashlib.sha256(path.read_bytes()).hexdigest() == record['sha256'], f'{path}: content changed; review facts then refresh catalog hash')
        body = parts[2].split('## Related documents')[0]
        words = len(body.split())
        word_counts.append(words)
        check(words >= 140, f'{path}: unexpectedly short ({words} words)')
        for target in re.findall(r'\]\(([^)]+)\)', text):
            resolved = (path.parent / target).resolve()
            check(resolved.is_relative_to((ROOT / 'documents').resolve()) and resolved.is_file(), f'{path}: invalid document link {target}')
        check('reference/' not in text, f'{path}: reference material linked from ingestion corpus')
        check(bool(record['expected_facts']), f'{path}: expected facts missing')
    questions = [json.loads(line) for line in (ROOT / 'reference/evaluation-questions.jsonl').read_text(encoding='utf-8').splitlines() if line.strip()]
    check(len(questions) == 60, 'Expected 60 evaluation questions')
    check({item['id'] for item in questions} == {f'Q-{index:03}' for index in range(1, 61)}, 'Question ID sequence differs')
    check(Counter(item['category'] for item in questions) == EXPECTED_QUESTIONS, 'Question category distribution differs')
    for item in questions:
        sources = item['required_supporting_documents']
        check(bool(sources) and all(source in ids for source in sources), f"{item['id']}: missing/unknown evidence")
        check(all(source in ids for source in item['expected_cited_sources']), f"{item['id']}: invalid citation")
        check(item['multiple_documents_required'] == (len(sources) > 1), f"{item['id']}: multiple-source flag mismatch")
        check(item['disposition'] in {'answer','declare_gap','clarify'}, f"{item['id']}: invalid disposition")
        check(bool(item['expected_answer']), f"{item['id']}: missing answer")
        check(item['as_of'] == catalog['as_of'], f"{item['id']}: reference date mismatch")
    # Stable arithmetic baselines used in the budget and organization fixtures.
    overview_record = next(record for record in records if record['id'] == 'ACME-001')
    overview = (ROOT / overview_record['path']).read_text(encoding='utf-8')
    department_counts = re.findall(r'(Engineering|IT Operations|Customer Success|Sales and Marketing|Finance and Procurement|People Operations) \((\d+)\)', overview)
    check(len(department_counts) == 6 and sum(int(count) for _, count in department_counts) == 120, 'Company department counts do not sum to 120')
    budget_record = next(record for record in records if record['id'] == 'ACME-050')
    budget = (ROOT / budget_record['path']).read_text(encoding='utf-8')
    rows = re.findall(r'^\| (Cedar|Aurora|Ledger|Compass|Total) \| ([\d,]+) \| ([\d,]+) \| ([\d,]+) \|', budget, re.MULTILINE)
    check(len(rows) == 5, 'Budget table rows missing')
    values = {name: tuple(int(number.replace(',', '')) for number in numbers) for name,*numbers in rows}
    for name, (envelope, committed, remaining) in values.items():
        check(envelope - committed == remaining, f'{name}: budget balance mismatch')
    if 'Total' in values:
        check(tuple(sum(value[index] for name,value in values.items() if name != 'Total') for index in range(3)) == values['Total'], 'Budget total mismatch')
    if errors:
        print('\n'.join('ERROR: '+error for error in errors))
        return 1
    print(f'PASS: 50 documents, 10 categories, {sum(word_counts):,} body words; 60 evaluation questions.')
    print(f'Body word range: {min(word_counts)}-{max(word_counts)}. IDs, metadata, dates, links, hashes, sources, and budget arithmetic valid.')
    print('Semantic correctness and model answer quality still require human review; no live ingestion was performed.')
    return 0

if __name__ == '__main__':
    try:
        sys.exit(validate())
    except (OSError, ValueError, KeyError, TypeError) as error:
        print(f'ERROR: {error}')
        sys.exit(1)
