#!/usr/bin/env python3
"""Pilote le GitHub Project « SonarQube Plugin FR » selon le workflow du skill suivi-projet.

Toutes les commandes passent par `gh` (jeton avec le scope `project`). Les identifiants
de champs et d'options sont relus à chaque appel, par leur nom : rien n'est figé ici
qui puisse se périmer.

  projet.py setup                          configure le projet (idempotent)
  projet.py jalon <titre> [--echeance AAAA-MM-JJ] [--description ...]
  projet.py ajouter <issue>                ajoute un ticket du dépôt au projet
  projet.py champs <issue> Champ=Valeur... renseigne des champs (Valeur vide = effacer)
  projet.py sous-ticket <parent> <enfant>  rattache un sous-ticket
  projet.py type <issue> Task|Bug|Feature  règle le type natif du ticket
  projet.py preuve <issue> <png> --legende "..." [--source URL]
  projet.py voir <issue>                   affiche les champs d'un ticket
  projet.py audit [<issue>...]             vérifie les règles ; code 1 au moindre manque
  projet.py vues                           vérifie le regroupement et le tri des vues

Lancer avec `python3 -I` depuis la racine du dépôt.
"""
import argparse
import base64
import datetime
import json
import re
import subprocess
import sys
import urllib.parse

ORG = 'Sonar-l10n-FR'
PROJECT_NUMBER = 1
REPO = 'Sonar-l10n-FR/sonar-l10n-fr'
EVIDENCE_REPO = 'Sonar-l10n-FR/project-evidence'
EVIDENCE_DIR = 'sonar-l10n-fr'

PROJECT_TITLE = 'SonarQube Plugin FR'
PROJECT_DESCRIPTION = 'Suivi du French Pack pour SonarQube : chaque action, ses champs et ses preuves.'

# Status reprend les options d'origine par leur id, pour que les automatisations
# intégrées (« Item added », « Item closed », « Pull request merged »…) restent branchées.
STATUS = [
    (None, 'Backlog', 'GRAY', 'Identifié, pas encore planifié'),
    ('Todo', 'À faire', 'BLUE', 'Planifié, prêt à démarrer'),
    ('In Progress', 'En cours', 'YELLOW', 'En cours de réalisation'),
    (None, 'En revue', 'ORANGE', 'Réalisé, en attente de validation par le mainteneur'),
    (None, 'Bloqué', 'RED', 'Attend un tiers ou une décision ; le blocage est expliqué en commentaire'),
    ('Done', 'Terminé', 'GREEN', 'Livré, prouvé et clos'),
]

SINGLE_SELECT = {
    'Catégorie': [
        ('Montée de version', 'PURPLE', 'Épique : prise en charge d’une nouvelle version de SonarQube'),
        ('Traduction', 'BLUE', 'Ajout ou correction de traductions'),
        ('Build & CI', 'GRAY', 'Maven, Docker, workflows GitHub Actions'),
        ('Outillage', 'GRAY', 'Outils d’extraction et de synchronisation (tools/, src/test)'),
        ('Publication', 'GREEN', 'Tag, release, update-center, Marketplace'),
        ('Bug', 'RED', 'Défaut constaté dans le pack'),
        ('Documentation', 'PINK', 'README, notes de version, guides'),
        ('Communauté', 'ORANGE', 'Communication du projet (annonce de version…), à la demande du mainteneur ; jamais ses échanges personnels'),
        ('Suivi amont', 'YELLOW', 'Correctif ou évolution attendu côté SonarSource'),
        ('Décision', 'PURPLE', 'Choix à arbitrer par le mainteneur'),
    ],
    'Priorité': [
        ('P0 – Urgente', 'RED', 'Bloque les utilisateurs ou une publication'),
        ('P1 – Haute', 'ORANGE', 'À traiter dans la version en cours'),
        ('P2 – Normale', 'YELLOW', 'À traiter quand le reste le permet'),
        ('P3 – Basse', 'GRAY', 'Amélioration sans échéance'),
    ],
    'Taille': [
        ('XS', 'GRAY', 'Moins d’une heure'),
        ('S', 'GREEN', 'Une demi-journée'),
        ('M', 'YELLOW', 'Une journée'),
        ('L', 'ORANGE', 'Deux à trois jours'),
        ('XL', 'RED', 'Plus de trois jours : à découper en sous-tickets'),
    ],
    'Version SonarQube': [
        ('26.10', 'GRAY', 'Community Build 26.10'),
        ('26.11', 'BLUE', 'Community Build 26.11'),
        ('26.12', 'PURPLE', 'Community Build 26.12'),
        ('Toutes versions', 'GRAY', 'Ne dépend pas d’une version de SonarQube'),
    ],
}
DATES = ['Début', 'Fin']
TEXTS = ['Preuves', 'Lien externe']

VIEWS = [
    # (nom, disposition, filtre, champs visibles)
    ('Tableau', 'BOARD_LAYOUT', '',
     ['Title', 'Assignees', 'Catégorie', 'Priorité', 'Taille', 'Version SonarQube']),
    ('Tous les tickets', 'TABLE_LAYOUT', '',
     ['Title', 'Status', 'Catégorie', 'Priorité', 'Taille', 'Version SonarQube', 'Milestone',
      'Début', 'Fin', 'Assignees', 'Preuves', 'Lien externe', 'Parent issue', 'Sub-issues progress']),
    ('Feuille de route', 'ROADMAP_LAYOUT', '',
     ['Title', 'Status', 'Catégorie', 'Version SonarQube', 'Début', 'Fin']),
    ('Historique', 'TABLE_LAYOUT', 'status:Terminé',
     ['Title', 'Catégorie', 'Version SonarQube', 'Milestone', 'Début', 'Fin', 'Preuves', 'Lien externe']),
]

README = """# SonarQube Plugin FR

Suivi du [French Pack pour SonarQube](https://github.com/Sonar-l10n-FR/sonar-l10n-fr).
Chaque action du mainteneur (traduction, build, publication, échange sur le forum, suivi
d’un correctif chez SonarSource) a son ticket, avec tous ses champs renseignés et ses
preuves.

## Statuts

Backlog → À faire → En cours → En revue → Terminé. « Bloqué » est possible à toute étape,
avec la raison en commentaire.

## Champs

| Champ | Contenu |
|---|---|
| Catégorie | Domaine de l’action |
| Priorité | P0 à P3 |
| Taille | XS à XL ; un XL se découpe en sous-tickets |
| Version SonarQube | Version de SonarQube concernée |
| Début / Fin | Dates réelles de démarrage et de clôture |
| Preuves | Dossier des captures dans [project-evidence](https://github.com/Sonar-l10n-FR/project-evidence) |
| Lien externe | Fil du forum, ticket SonarSource, PR d’update-center |
| Milestone | Version du pack qui livre le changement |

## Preuves

Les captures d’écran sont rangées par ticket dans
[Sonar-l10n-FR/project-evidence](https://github.com/Sonar-l10n-FR/project-evidence)
et insérées en commentaire du ticket concerné.
"""

# Réglages de vues que l'API sait lire mais pas écrire : faits une fois dans l'interface,
# vérifiés par `projet.py vues`. (Les dates de la feuille de route ne sont pas lisibles.)
VIEW_EXPECT = {
    'Tableau': {'colonnes': ['Status'], 'groupe': [], 'tri': ['Priorité ASC']},
    'Tous les tickets': {'colonnes': [], 'groupe': ['Version SonarQube'], 'tri': ['Priorité ASC']},
    'Historique': {'colonnes': [], 'groupe': [], 'tri': ['Fin DESC']},
}

# Règles d'audit -------------------------------------------------------------------------
REQUIRED_ALWAYS = ['Status', 'Catégorie', 'Priorité', 'Taille', 'Version SonarQube']
STARTED = {'En cours', 'En revue', 'Bloqué', 'Terminé'}
NEEDS_MILESTONE = {'Montée de version', 'Traduction', 'Build & CI', 'Outillage', 'Publication', 'Bug'}
NEEDS_LINK = {'Communauté', 'Suivi amont'}
BODY_SECTIONS = ['## Contexte', '## Objectif', '## Critères d’acceptation', '## Preuves attendues']
CLOSING_HEADING = '## Bilan'
BLOCKED_MARKER = 'Bloqué par'


# GitHub ----------------------------------------------------------------------------------
def gh(*args, input=None, check=True):
    r = subprocess.run(['gh', *args], input=input, capture_output=True, text=True)
    if check and r.returncode != 0:
        sys.exit('gh %s\n%s' % (' '.join(args[:3]), r.stderr.strip()))
    return r


def graphql(query, **variables):
    payload = json.dumps({'query': query, 'variables': variables})
    out = json.loads(gh('api', 'graphql', '--input', '-', input=payload).stdout)
    if out.get('errors'):
        sys.exit(json.dumps(out['errors'], ensure_ascii=False, indent=1))
    return out['data']


def rest(method, path, body=None, check=True):
    args = ['api', '-X', method, path]
    r = gh(*args, '--input', '-', input=json.dumps(body), check=check) if body is not None \
        else gh(*args, check=check)
    return json.loads(r.stdout) if r.returncode == 0 and r.stdout.strip() else None


PROJECT_Q = '''query($org:String!,$n:Int!){organization(login:$org){projectV2(number:$n){
  id title public shortDescription readme
  fields(first:50){nodes{
    ... on ProjectV2Field{id name dataType}
    ... on ProjectV2SingleSelectField{id name dataType options{id name color description}}
    ... on ProjectV2IterationField{id name dataType}}}
  views(first:20){nodes{id name number layout filter}}}}}'''


def project():
    return graphql(PROJECT_Q, org=ORG, n=PROJECT_NUMBER)['organization']['projectV2']


def fields_by_name(p):
    return {f['name']: f for f in p['fields']['nodes'] if f}


# setup -------------------------------------------------------------------------------------
def cmd_setup(_):
    p = project()
    graphql('''mutation($id:ID!,$t:String!,$d:String!,$r:String!){updateProjectV2(input:{
      projectId:$id,title:$t,shortDescription:$d,readme:$r,public:true}){projectV2{id}}}''',
            id=p['id'], t=PROJECT_TITLE, d=PROJECT_DESCRIPTION, r=README)
    print('projet : titre, description, README, public')

    fields = fields_by_name(p)
    status = fields['Status']
    by_name = {o['name']: o for o in status['options']}
    options = []
    for origin, name, color, desc in STATUS:
        current = by_name.get(name) or (by_name.get(origin) if origin else None)
        opt = {'name': name, 'color': color, 'description': desc}
        if current:
            opt['id'] = current['id']
        options.append(opt)
    graphql('''mutation($f:ID!,$o:[ProjectV2SingleSelectFieldOptionInput!]){updateProjectV2Field(
      input:{fieldId:$f,singleSelectOptions:$o}){projectV2Field{... on ProjectV2SingleSelectField{id}}}}''',
            f=status['id'], o=options)
    print('Status :', ', '.join(o['name'] for o in options))

    for name, opts in SINGLE_SELECT.items():
        wanted = [{'name': n, 'color': c, 'description': d} for n, c, d in opts]
        if name in fields:
            existing = {o['name']: o['id'] for o in fields[name]['options']}
            for o in wanted:
                if o['name'] in existing:
                    o['id'] = existing[o['name']]
            # Options added by hand (a new SonarQube version, say) are kept.
            known = {o['name'] for o in wanted}
            for o in fields[name]['options']:
                if o['name'] not in known:
                    wanted.append({'id': o['id'], 'name': o['name'], 'color': o['color'],
                                   'description': o['description'] or ''})
            graphql('''mutation($f:ID!,$o:[ProjectV2SingleSelectFieldOptionInput!]){updateProjectV2Field(
              input:{fieldId:$f,singleSelectOptions:$o}){projectV2Field{... on ProjectV2SingleSelectField{id}}}}''',
                    f=fields[name]['id'], o=wanted)
        else:
            graphql('''mutation($p:ID!,$n:String!,$o:[ProjectV2SingleSelectFieldOptionInput!]){createProjectV2Field(
              input:{projectId:$p,dataType:SINGLE_SELECT,name:$n,singleSelectOptions:$o}){projectV2Field{
              ... on ProjectV2SingleSelectField{id}}}}''', p=p['id'], n=name, o=wanted)
        print('%s : %s' % (name, ', '.join(o['name'] for o in wanted)))

    for name, kind in [(n, 'DATE') for n in DATES] + [(n, 'TEXT') for n in TEXTS]:
        if name not in fields:
            graphql('''mutation($p:ID!,$n:String!,$t:ProjectV2CustomFieldType!){createProjectV2Field(
              input:{projectId:$p,dataType:$t,name:$n}){projectV2Field{... on ProjectV2Field{id}}}}''',
                    p=p['id'], n=name, t=kind)
        print('%s : %s' % (name, kind))

    p = project()
    fields = fields_by_name(p)
    views = {v['name']: v for v in p['views']['nodes']}
    default = next((v for v in p['views']['nodes'] if v['name'] == 'View 1'), None)
    for name, layout, flt, visible in VIEWS:
        # Roadmaps take no field list: their bars come from the date fields.
        ids = [fields[f]['id'] for f in visible if f in fields] if layout != 'ROADMAP_LAYOUT' else None
        view = views.get(name)
        if not view and default and name == 'Tableau':
            view, default = default, None
        if view:
            if ids is None:
                graphql('''mutation($v:ID!,$n:String!,$l:ProjectV2ViewLayout!,$f:String!){updateProjectV2View(
                  input:{viewId:$v,name:$n,layout:$l,filter:$f}){projectV2View{id}}}''',
                        v=view['id'], n=name, l=layout, f=flt)
            else:
                graphql('''mutation($v:ID!,$n:String!,$l:ProjectV2ViewLayout!,$f:String!,$ids:[ID!]){updateProjectV2View(
                  input:{viewId:$v,name:$n,layout:$l,filter:$f,configuration:{visibleFieldIds:$ids}}){projectV2View{id}}}''',
                        v=view['id'], n=name, l=layout, f=flt, ids=ids)
        else:
            if ids is None:
                created = graphql('''mutation($p:ID!,$n:String!,$l:ProjectV2ViewLayout!){createProjectV2View(
                  input:{projectId:$p,name:$n,layout:$l}){projectV2View{id}}}''', p=p['id'], n=name, l=layout)
            else:
                created = graphql('''mutation($p:ID!,$n:String!,$l:ProjectV2ViewLayout!,$ids:[ID!]){createProjectV2View(
                  input:{projectId:$p,name:$n,layout:$l,configuration:{visibleFieldIds:$ids}}){projectV2View{id}}}''',
                                  p=p['id'], n=name, l=layout, ids=ids)
            if flt:
                graphql('''mutation($v:ID!,$f:String!){updateProjectV2View(input:{viewId:$v,filter:$f}){
                  projectV2View{id}}}''', v=created['createProjectV2View']['projectV2View']['id'], f=flt)
        print('vue : %s (%s) %s' % (name, layout, flt))

    ensure_evidence_repo()


def ensure_evidence_repo():
    if rest('GET', 'repos/%s' % EVIDENCE_REPO, check=False) is None:
        gh('repo', 'create', EVIDENCE_REPO, '--public',
           '--description', 'Captures d’écran de preuve des tickets du French Pack pour SonarQube')
        put_file('README.md', (
            '# Preuves du French Pack pour SonarQube\n\n'
            'Captures d’écran qui prouvent chaque action suivie dans le '
            '[projet GitHub](https://github.com/orgs/%s/projects/%d).\n\n'
            'Rangement : `%s/<numéro du ticket>/<date>-<sujet>.png`, une capture par étape, '
            'insérée en commentaire du ticket.\n' % (ORG, PROJECT_NUMBER, EVIDENCE_DIR)).encode(),
                 'docs: décrire le rangement des preuves')
        print('dépôt de preuves créé :', EVIDENCE_REPO)
    else:
        print('dépôt de preuves :', EVIDENCE_REPO)


def put_file(path, data, message):
    body = {'message': message, 'content': base64.b64encode(data).decode()}
    existing = rest('GET', 'repos/%s/contents/%s' % (EVIDENCE_REPO, urllib.parse.quote(path)), check=False)
    if existing and 'sha' in existing:
        body['sha'] = existing['sha']
    return rest('PUT', 'repos/%s/contents/%s' % (EVIDENCE_REPO, urllib.parse.quote(path)), body)


# jalons ------------------------------------------------------------------------------------
def cmd_jalon(a):
    for m in rest('GET', 'repos/%s/milestones?state=all&per_page=100' % REPO) or []:
        if m['title'] == a.titre:
            print('jalon existant : %s (#%d)' % (m['title'], m['number']))
            return
    body = {'title': a.titre, 'description': a.description or ''}
    if a.echeance:
        body['due_on'] = a.echeance + 'T23:59:59Z'
    m = rest('POST', 'repos/%s/milestones' % REPO, body)
    print('jalon créé : %s (#%d)' % (m['title'], m['number']))


# éléments ----------------------------------------------------------------------------------
ITEM_Q = '''query($owner:String!,$repo:String!,$n:Int!){repository(owner:$owner,name:$repo){issue(number:$n){
  id number title body state url
  issueType{name}
  assignees(first:10){nodes{login}}
  milestone{title}
  parent{number}
  subIssues(first:50){nodes{number state}}
  comments(last:100){nodes{body}}
  projectItems(first:10){nodes{id project{number owner{... on Organization{login}}}
    fieldValues(first:50){nodes{
      ... on ProjectV2ItemFieldSingleSelectValue{name field{... on ProjectV2FieldCommon{name}}}
      ... on ProjectV2ItemFieldTextValue{text field{... on ProjectV2FieldCommon{name}}}
      ... on ProjectV2ItemFieldDateValue{date field{... on ProjectV2FieldCommon{name}}}}}}}}}}'''


def issue(n):
    owner, name = REPO.split('/')
    i = graphql(ITEM_Q, owner=owner, repo=name, n=int(n))['repository']['issue']
    if i is None:
        sys.exit('ticket #%s introuvable dans %s' % (n, REPO))
    item = next((it for it in i['projectItems']['nodes']
                 if it['project']['number'] == PROJECT_NUMBER and it['project']['owner']['login'] == ORG), None)
    values = {}
    if item:
        for v in item['fieldValues']['nodes']:
            if v and v.get('field'):
                values[v['field']['name']] = v.get('name') or v.get('text') or v.get('date')
    return i, item, values


def cmd_ajouter(a):
    i, item, _ = issue(a.issue)
    if item:
        print('#%s déjà dans le projet' % a.issue)
        return item['id']
    p = project()
    r = graphql('''mutation($p:ID!,$c:ID!){addProjectV2ItemById(input:{projectId:$p,contentId:$c}){item{id}}}''',
                p=p['id'], c=i['id'])
    print('#%s ajouté au projet' % a.issue)
    return r['addProjectV2ItemById']['item']['id']


def set_fields(n, assignments):
    i, item, _ = issue(n)
    if not item:
        sys.exit('#%s n’est pas dans le projet : lancer d’abord « ajouter »' % n)
    p = project()
    fields = fields_by_name(p)
    for assignment in assignments:
        name, _, value = assignment.partition('=')
        f = fields.get(name)
        if not f:
            sys.exit('champ inconnu : %s (connus : %s)' % (name, ', '.join(fields)))
        if value == '':
            graphql('''mutation($p:ID!,$i:ID!,$f:ID!){clearProjectV2ItemFieldValue(
              input:{projectId:$p,itemId:$i,fieldId:$f}){projectV2Item{id}}}''', p=p['id'], i=item['id'], f=f['id'])
            print('#%s %s effacé' % (n, name))
            continue
        if f['dataType'] == 'SINGLE_SELECT':
            opt = next((o for o in f['options'] if o['name'] == value), None)
            if not opt:
                sys.exit('%s : option inconnue « %s » (connues : %s)'
                         % (name, value, ', '.join(o['name'] for o in f['options'])))
            val = {'singleSelectOptionId': opt['id']}
        elif f['dataType'] == 'DATE':
            if value == 'aujourd’hui' or value == 'today':
                value = datetime.date.today().isoformat()
            datetime.date.fromisoformat(value)
            val = {'date': value}
        elif f['dataType'] == 'TEXT':
            val = {'text': value}
        elif f['dataType'] == 'NUMBER':
            val = {'number': float(value)}
        else:
            sys.exit('%s : champ natif, à régler avec gh issue edit (assignees, milestone, labels)' % name)
        graphql('''mutation($p:ID!,$i:ID!,$f:ID!,$v:ProjectV2FieldValue!){updateProjectV2ItemFieldValue(
          input:{projectId:$p,itemId:$i,fieldId:$f,value:$v}){projectV2Item{id}}}''',
                p=p['id'], i=item['id'], f=f['id'], v=val)
        print('#%s %s = %s' % (n, name, value))


def cmd_champs(a):
    set_fields(a.issue, a.valeurs)


def cmd_sous_ticket(a):
    parent, _, _ = issue(a.parent)
    child, _, _ = issue(a.enfant)
    graphql('''mutation($p:ID!,$c:ID!){addSubIssue(input:{issueId:$p,subIssueId:$c}){issue{number}}}''',
            p=parent['id'], c=child['id'])
    print('#%s rattaché à #%s' % (a.enfant, a.parent))


def cmd_type(a):
    i, _, _ = issue(a.issue)
    types = graphql('''query($org:String!){organization(login:$org){issueTypes(first:20){nodes{id name}}}}''',
                    org=ORG)['organization']['issueTypes']['nodes']
    t = next((t for t in types if t['name'] == a.type), None)
    if not t:
        sys.exit('type inconnu « %s » (connus : %s)' % (a.type, ', '.join(t['name'] for t in types)))
    graphql('''mutation($i:ID!,$t:ID!){updateIssueIssueType(input:{issueId:$i,issueTypeId:$t}){issue{number}}}''',
            i=i['id'], t=t['id'])
    print('#%s type = %s' % (a.issue, a.type))


def slug(text):
    t = text.lower()
    for a, b in (('àâä', 'a'), ('éèêë', 'e'), ('îï', 'i'), ('ôö', 'o'), ('ùûü', 'u'), ('ç', 'c')):
        t = re.sub('[%s]' % a, b, t)
    return re.sub(r'[^a-z0-9]+', '-', t).strip('-')[:60]


def cmd_preuve(a):
    i, item, values = issue(a.issue)
    if not item:
        sys.exit('#%s n’est pas dans le projet' % a.issue)
    with open(a.png, 'rb') as fh:
        data = fh.read()
    if not data.startswith(b'\x89PNG'):
        sys.exit('%s n’est pas un PNG' % a.png)
    now = datetime.datetime.now()
    path = '%s/%s/%s-%s.png' % (EVIDENCE_DIR, a.issue, now.strftime('%Y-%m-%d-%H%M%S'), slug(a.legende))
    put_file(path, data, 'preuve(#%s): %s' % (a.issue, a.legende))
    raw = 'https://raw.githubusercontent.com/%s/main/%s' % (EVIDENCE_REPO, urllib.parse.quote(path))
    folder = 'https://github.com/%s/tree/main/%s/%s' % (EVIDENCE_REPO, EVIDENCE_DIR, a.issue)
    lines = ['### Preuve : %s' % a.legende, '', '![%s](%s)' % (a.legende, raw), '',
             '_Capture du %s%s. [Toutes les preuves de ce ticket](%s)._'
             % (now.strftime('%d/%m/%Y à %H:%M'), (', source : %s' % a.source) if a.source else '', folder)]
    gh('issue', 'comment', str(a.issue), '-R', REPO, '--body', '\n'.join(lines))
    if values.get('Preuves') != folder:
        set_fields(a.issue, ['Preuves=' + folder])
    print('preuve publiée :', raw)


def cmd_voir(a):
    i, item, values = issue(a.issue)
    print('#%d %s [%s]' % (i['number'], i['title'], i['state']))
    print('  type :', (i['issueType'] or {}).get('name'), '| assignés :',
          ', '.join(n['login'] for n in i['assignees']['nodes']) or '—',
          '| jalon :', (i['milestone'] or {}).get('title'), '| parent :', (i['parent'] or {}).get('number'))
    if not item:
        print('  (hors projet)')
    for k, v in values.items():
        if k != 'Title':
            print('  %s : %s' % (k, v))


def cmd_vues(_):
    views = graphql('''query($org:String!,$n:Int!){organization(login:$org){projectV2(number:$n){views(first:20){nodes{
      name
      groupByFields(first:5){nodes{... on ProjectV2FieldCommon{name}}}
      verticalGroupByFields(first:5){nodes{... on ProjectV2FieldCommon{name}}}
      sortByFields(first:5){nodes{direction field{... on ProjectV2FieldCommon{name}}}}}}}}}''',
                    org=ORG, n=PROJECT_NUMBER)['organization']['projectV2']['views']['nodes']
    bad = 0
    for v in views:
        want = VIEW_EXPECT.get(v['name'])
        if not want:
            continue
        got = {'colonnes': [f['name'] for f in v['verticalGroupByFields']['nodes']],
               'groupe': [f['name'] for f in v['groupByFields']['nodes']],
               'tri': ['%s %s' % (s['field']['name'], s['direction']) for s in v['sortByFields']['nodes']]}
        diffs = ['%s : %s au lieu de %s' % (k, got[k] or 'aucun', want[k] or 'aucun')
                 for k in want if got[k] != want[k]]
        print('%s : %s' % (v['name'], 'conforme' if not diffs else '; '.join(diffs)))
        bad += bool(diffs)
    sys.exit(1 if bad else 0)


def problems(n):
    i, item, v = issue(n)
    out = []
    if not item:
        return ['absent du projet']
    status = v.get('Status')
    cat = v.get('Catégorie')
    for f in REQUIRED_ALWAYS:
        if not v.get(f):
            out.append('champ « %s » vide' % f)
    if not i['assignees']['nodes']:
        out.append('aucune personne assignée')
    if not i['issueType']:
        out.append('type de ticket (Task/Bug/Feature) non défini')
    body = i['body'] or ''
    for s in BODY_SECTIONS:
        if s not in body:
            out.append('section « %s » absente de la description' % s)
    boxes = re.findall(r'^\s*- \[( |x|X)\]', body, re.M)
    if not boxes:
        out.append('aucun critère d’acceptation à cocher')
    if status in STARTED and not v.get('Début'):
        out.append('« Début » vide alors que le ticket a démarré')
    if cat in NEEDS_MILESTONE and not i['milestone']:
        out.append('jalon (version du pack) manquant pour la catégorie %s' % cat)
    if cat in NEEDS_LINK and not v.get('Lien externe'):
        out.append('« Lien externe » vide pour la catégorie %s' % cat)
    comments = [c['body'] for c in i['comments']['nodes']]
    has_evidence = any(EVIDENCE_REPO in c and '![' in c for c in comments)
    if status in ('En revue', 'Terminé'):
        if not v.get('Preuves'):
            out.append('« Preuves » vide au statut %s' % status)
        if not has_evidence:
            out.append('aucune capture de preuve en commentaire au statut %s' % status)
    if status == 'Bloqué' and not any(BLOCKED_MARKER in c for c in comments):
        out.append('statut Bloqué sans commentaire « %s »' % BLOCKED_MARKER)
    if status == 'Terminé':
        if not v.get('Fin'):
            out.append('« Fin » vide au statut Terminé')
        if i['state'] != 'CLOSED':
            out.append('statut Terminé mais ticket encore ouvert')
        if any(b == ' ' for b in boxes):
            out.append('critères d’acceptation non cochés au statut Terminé')
        if not any(CLOSING_HEADING in c for c in comments):
            out.append('commentaire de clôture « %s » absent' % CLOSING_HEADING)
        if any(s['state'] != 'CLOSED' for s in i['subIssues']['nodes']):
            out.append('sous-tickets encore ouverts')
    else:
        if i['state'] == 'CLOSED':
            out.append('ticket clos mais statut %s' % status)
        if v.get('Fin'):
            out.append('« Fin » renseignée alors que le ticket n’est pas terminé')
    if v.get('Taille') == 'XL' and not i['subIssues']['nodes']:
        out.append('taille XL sans sous-tickets')
    return out


def cmd_audit(a):
    numbers = a.issues
    if not numbers:
        items = json.loads(gh('project', 'item-list', str(PROJECT_NUMBER), '--owner', ORG,
                              '--format', 'json', '-L', '500').stdout)['items']
        drafts = [it['title'] for it in items if it['content'].get('type') == 'DraftIssue']
        for t in drafts:
            print('BROUILLON interdit : « %s » (convertir en ticket du dépôt)' % t)
        numbers = sorted(it['content']['number'] for it in items
                         if it['content'].get('type') == 'Issue')
    else:
        drafts = []
    bad = len(drafts)
    for n in numbers:
        ps = problems(n)
        if ps:
            bad += 1
            print('#%s' % n)
            for p in ps:
                print('   - ' + p)
        else:
            print('#%s conforme' % n)
    sys.exit(1 if bad else 0)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest='cmd', required=True)
    sub.add_parser('setup').set_defaults(fn=cmd_setup)
    s = sub.add_parser('jalon')
    s.add_argument('titre')
    s.add_argument('--echeance')
    s.add_argument('--description')
    s.set_defaults(fn=cmd_jalon)
    s = sub.add_parser('ajouter')
    s.add_argument('issue')
    s.set_defaults(fn=cmd_ajouter)
    s = sub.add_parser('champs')
    s.add_argument('issue')
    s.add_argument('valeurs', nargs='+')
    s.set_defaults(fn=cmd_champs)
    s = sub.add_parser('sous-ticket')
    s.add_argument('parent')
    s.add_argument('enfant')
    s.set_defaults(fn=cmd_sous_ticket)
    s = sub.add_parser('type')
    s.add_argument('issue')
    s.add_argument('type', choices=['Task', 'Bug', 'Feature'])
    s.set_defaults(fn=cmd_type)
    s = sub.add_parser('preuve')
    s.add_argument('issue')
    s.add_argument('png')
    s.add_argument('--legende', required=True)
    s.add_argument('--source')
    s.set_defaults(fn=cmd_preuve)
    s = sub.add_parser('voir')
    s.add_argument('issue')
    s.set_defaults(fn=cmd_voir)
    sub.add_parser('vues').set_defaults(fn=cmd_vues)
    s = sub.add_parser('audit')
    s.add_argument('issues', nargs='*')
    s.set_defaults(fn=cmd_audit)
    a = ap.parse_args()
    a.fn(a)


if __name__ == '__main__':
    main()
