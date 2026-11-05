"""Seed a Jira Cloud test site with the synthetic Acme work items, using only Python's standard library.

Reads JIRA_SITE_URL, JIRA_EMAIL, and JIRA_API_TOKEN from the environment, for example:

    set -a; . ./.env; set +a; python3 synthetic-data/tools/seed_jira.py

Re-running is safe: each work item carries a `kbseed-<seedId>` label, and existing items are skipped.
The target project must already exist. Pass --dry-run to print the plan without writing anything.
"""
import base64
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SEED_FILE = ROOT / 'jira/acme-jira-seed.json'
SEED_LABEL = 'kb-seed'


class Jira:
    def __init__(self, site, email, token):
        self.site = site.rstrip('/')
        credentials = base64.b64encode(f'{email}:{token}'.encode('utf-8')).decode('ascii')
        self.headers = {'Authorization': f'Basic {credentials}', 'Accept': 'application/json', 'Content-Type': 'application/json'}

    def call(self, method, path, body=None):
        data = None if body is None else json.dumps(body).encode('utf-8')
        request = urllib.request.Request(self.site + path, data=data, method=method, headers=self.headers)
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                text = response.read().decode('utf-8')
                return json.loads(text) if text else None
        except urllib.error.HTTPError as error:
            detail = error.read().decode('utf-8', errors='replace')[:500]
            raise SystemExit(f'{method} {path} failed with HTTP {error.code}: {detail}') from None


def inline(text):
    """Split **bold** runs into ADF text nodes."""
    nodes = []
    for index, part in enumerate(re.split(r'\*\*(.+?)\*\*', text)):
        if part:
            node = {'type': 'text', 'text': part}
            if index % 2:
                node['marks'] = [{'type': 'strong'}]
            nodes.append(node)
    return nodes


def to_adf(markdown):
    """Convert the small Markdown subset used in the seed file (paragraphs, ## headings, - lists) to ADF."""
    content = []
    for block in markdown.strip().split('\n\n'):
        lines = block.split('\n')
        if all(line.startswith('- ') for line in lines):
            items = [{'type': 'listItem', 'content': [{'type': 'paragraph', 'content': inline(line[2:])}]} for line in lines]
            content.append({'type': 'bulletList', 'content': items})
        elif block.startswith('## '):
            content.append({'type': 'heading', 'attrs': {'level': 2}, 'content': inline(block[3:])})
        else:
            paragraph = []
            for number, line in enumerate(lines):
                if number:
                    paragraph.append({'type': 'hardBreak'})
                paragraph.extend(inline(line))
            content.append({'type': 'paragraph', 'content': paragraph})
    return {'type': 'doc', 'version': 1, 'content': content}


def existing_keys(jira, project):
    """Map seedId to work item key for items already seeded in the project."""
    found, token = {}, None
    while True:
        query = {'jql': f'project = {project} AND labels = {SEED_LABEL}', 'fields': 'labels', 'maxResults': '100'}
        if token:
            query['nextPageToken'] = token
        page = jira.call('GET', '/rest/api/3/search/jql?' + urllib.parse.urlencode(query))
        for issue in page['issues']:
            for label in issue['fields']['labels']:
                if label.startswith('kbseed-'):
                    found[label[len('kbseed-'):]] = issue['key']
        token = page.get('nextPageToken')
        if page.get('isLast', True) or not token:
            return found


def transition(jira, key, status):
    transitions = jira.call('GET', f'/rest/api/3/issue/{key}/transitions')['transitions']
    match = next((t for t in transitions if t['to']['name'].lower() == status.lower()), None)
    if match is None:
        raise SystemExit(f'{key}: no transition to status {status!r}')
    jira.call('POST', f'/rest/api/3/issue/{key}/transitions', {'transition': {'id': match['id']}})


def seed(dry_run):
    seed_data = json.loads(SEED_FILE.read_text(encoding='utf-8'))
    project = seed_data['project']['key']
    missing = [name for name in ('JIRA_SITE_URL', 'JIRA_EMAIL', 'JIRA_API_TOKEN') if not os.environ.get(name)]
    if missing:
        raise SystemExit('Missing environment variables: ' + ', '.join(missing))
    jira = Jira(os.environ['JIRA_SITE_URL'], os.environ['JIRA_EMAIL'], os.environ['JIRA_API_TOKEN'])
    keys = existing_keys(jira, project)
    created = 0
    # Epics come first in the file, so parents exist before their children.
    for item in seed_data['items']:
        seed_id = item['seedId']
        if seed_id in keys:
            print(f'skip   {keys[seed_id]} {seed_id}')
            continue
        if dry_run:
            print(f'create {seed_id}: [{item["type"]}] {item["summary"]} -> {item["status"]}')
            continue
        fields = {
            'project': {'key': project},
            'issuetype': {'name': item['type']},
            'summary': item['summary'],
            'description': to_adf(item['description']),
            'priority': {'name': item['priority']},
            'labels': [SEED_LABEL, f'kbseed-{seed_id}', *item['labels']],
        }
        if item.get('parent'):
            fields['parent'] = {'key': keys[item['parent']]}
        key = jira.call('POST', '/rest/api/3/issue', {'fields': fields})['key']
        keys[seed_id] = key
        for comment in item['comments']:
            jira.call('POST', f'/rest/api/3/issue/{key}/comment', {'body': to_adf(comment)})
        if item['status'] != 'To Do':
            transition(jira, key, item['status'])
        created += 1
        print(f'create {key} {seed_id}')
    if created and not dry_run:
        for link in seed_data['links']:
            jira.call('POST', '/rest/api/3/issueLink', {
                'type': {'name': link['type']},
                'inwardIssue': {'key': keys[link['to']]},
                'outwardIssue': {'key': keys[link['from']]},
            })
        print(f'linked {len(seed_data["links"])} pairs')
    print(f'done: {created} created, {len(seed_data["items"]) - created} skipped or planned')


if __name__ == '__main__':
    seed('--dry-run' in sys.argv[1:])
