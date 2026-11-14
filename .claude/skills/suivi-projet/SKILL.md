---
name: suivi-projet
description: Workflow de suivi du French Pack dans le GitHub Project « SonarQube Plugin FR » (Sonar-l10n-FR, projet n° 1). À appliquer à CHAQUE action sur le projet : traduction, montée de version SonarQube, build/CI, outillage, publication (tag, release, update-center, Marketplace), bug, documentation, annonce du projet demandée par le mainteneur, suivi d'un correctif chez SonarSource, décision à arbitrer. Jamais pour les échanges personnels du mainteneur (forum, remerciements, discussions). Crée ou met à jour le ticket avant d'agir, renseigne tous ses champs, publie les captures d'écran de preuve et termine par un audit sans écart. Se déclenche aussi sur « ticket », « projet GitHub », « suivi », « preuve », « historiser », « où en est-on ».
---

# Suivi du projet dans GitHub Projects

Toute action sur le French Pack laisse une trace complète dans le projet
[SonarQube Plugin FR](https://github.com/orgs/Sonar-l10n-FR/projects/1) : un ticket du dépôt
`Sonar-l10n-FR/sonar-l10n-fr`, tous ses champs renseignés, des captures d'écran de preuve
dans [`Sonar-l10n-FR/project-evidence`](https://github.com/Sonar-l10n-FR/project-evidence), et un bilan à la clôture.
**L'audit doit passer sans écart à la fin de chaque tour où un ticket a bougé.**

Outils (depuis la racine du dépôt) :

```sh
P="python3 -I .claude/skills/suivi-projet/scripts/projet.py"
C="sh .claude/skills/suivi-projet/scripts/capture.sh"
```

`$P --help` liste les commandes. Les identifiants de champs sont relus par leur nom à chaque
appel : ne jamais en coder un en dur.

## Règles

1. **Pas d'action sans ticket.** Ouvrir le ticket (ou retrouver l'existant avec `gh issue list`)
   *avant* de commencer, pas après coup.
2. **Hors suivi : les échanges personnels du mainteneur.** Ses messages sur le forum, ses
   remerciements et ses discussions avec la communauté ou SonarSource ne donnent lieu à
   aucun ticket ni à aucune capture, même quand Claude a aidé à les rédiger. La catégorie
   « Communauté » ne sert qu'aux communications du projet (annonce de version…), et
   seulement à la demande du mainteneur.
3. **Jamais de brouillon de projet** : toujours un vrai ticket du dépôt, ajouté au projet.
4. **Tickets en français**, titre à l'infinitif et précis (« Traduire les nouveautés de SonarQube 26.11 »).
5. **Un ticket = un livrable vérifiable.** Ce qui dépasse trois jours (Taille XL) se découpe en
   sous-tickets rattachés à un ticket parent de catégorie « Montée de version » ou équivalent.
6. **Chaque champ est renseigné** selon le tableau ci-dessous ; `$P audit` est la référence.
7. **Chaque passage en « En revue » ou « Terminé » s'appuie sur au moins une capture de preuve.**
8. **Toute question au mainteneur passe par l'outil AskUserQuestion**, option recommandée en
   premier et suffixée « (Recommandé) » ; c'est une règle du workspace. Un ticket « Décision »
   s'arbitre ainsi, puis la réponse est consignée en commentaire du ticket.
9. Créer et mettre à jour les tickets, les champs et les preuves fait partie du workflow
   autorisé. **Publier ailleurs** (message sur le forum, PR sur un dépôt tiers, tag, release,
   commit, push) **reste soumis à l'accord explicite du mainteneur**, comme avant.

## Champs

| Champ | Valeurs | Obligatoire |
|---|---|---|
| Assignees | `Greenystuff` | toujours |
| Type (natif) | `Task`, `Bug`, `Feature` | toujours |
| Status | Backlog, À faire, En cours, En revue, Bloqué, Terminé | toujours |
| Catégorie | Montée de version, Traduction, Build & CI, Outillage, Publication, Bug, Documentation, Communauté, Suivi amont, Décision | toujours |
| Priorité | P0 – Urgente, P1 – Haute, P2 – Normale, P3 – Basse | toujours |
| Taille | XS (< 1 h), S (½ j), M (1 j), L (2-3 j), XL (> 3 j, à découper) | toujours |
| Version SonarQube | 26.10, 26.11, 26.12…, Toutes versions | toujours |
| Milestone | version du pack qui livrera le changement (`26.11.0`…) | catégories Montée de version, Traduction, Build & CI, Outillage, Publication, Bug |
| Lien externe | fil du forum, ticket SonarSource, PR d'update-center | catégories Communauté, Suivi amont (et dès qu'il existe) |
| Début | date réelle de démarrage | dès « En cours » |
| Preuves | dossier du ticket dans project-evidence (posé par `$P preuve`) | dès « En revue » |
| Fin | date réelle de clôture | à « Terminé », et seulement là |
| Parent issue | ticket parent | pour tout sous-ticket |

Nouvelle version de SonarQube : ajouter l'option dans « Version SonarQube » en complétant
`SINGLE_SELECT` dans `scripts/projet.py`, puis `$P setup` (idempotent, conserve les options existantes).
Nouveau jalon : `$P jalon 26.12.0 --description "…"`.

## Cycle de vie

### 1. Ouvrir

Description à partir de [templates/ticket.md](templates/ticket.md), sections toutes remplies :

```sh
gh issue create -R Sonar-l10n-FR/sonar-l10n-fr --assignee Greenystuff \
  --title "…" --body-file /chemin/corps.md [--milestone 26.11.0]
$P ajouter <n>
$P type <n> Task
$P champs <n> "Status=À faire" "Catégorie=…" "Priorité=…" "Taille=…" "Version SonarQube=…" ["Lien externe=…"]
$P sous-ticket <parent> <n>        # si le ticket fait partie d'un ensemble
```

Statut d'entrée : « À faire » si le travail est planifié, « Backlog » sinon.

### 2. Démarrer

```sh
$P champs <n> "Status=En cours" "Début=today"
```

### 3. Avancer

À chaque étape vérifiable, un commentaire court (ce qui a été fait, résultat, commit ou PR
concerné) et, dès qu'il y a quelque chose à voir, une capture (section « Preuves »).
Les commits portent `Refs #<n>`, la PR qui livre porte `Closes #<n>`.

### 4. Bloquer (à toute étape)

```sh
gh issue comment <n> -R Sonar-l10n-FR/sonar-l10n-fr --body "Bloqué par : <qui ou quoi>, <ce qui débloquera>"
$P champs <n> "Status=Bloqué"
```

### 5. Mettre en revue

Le travail est fait mais attend le mainteneur (relecture, commit, publication, arbitrage) :
au moins une preuve publiée, puis `$P champs <n> "Status=En revue"`.

### 6. Terminer

Le passage en « Terminé » se fait par le compte du mainteneur : soit par lui (il fusionne la
PR, ou déplace la carte dans la colonne « Terminé » du tableau), soit par Claude à travers ce
skill, **dès que tous les critères sont remplis et prouvés**. Un critère qui demande une fusion,
un tag, une release ou un message public attend l'accord du mainteneur (règle 9). Quand
GitHub ou le mainteneur a clos le ticket, Claude complète le bilan et « Fin » : `$P audit` le
signale.

1. Cocher tous les critères d'acceptation dans la description (`gh issue edit <n> --body-file …`).
2. Publier le bilan à partir de [templates/bilan.md](templates/bilan.md) : `gh issue comment <n> --body-file …`.
3. `$P champs <n> "Fin=today" "Status=Terminé"`.
4. `gh issue close <n> -R Sonar-l10n-FR/sonar-l10n-fr --reason completed`.
5. Ticket parent : le terminer de même quand tous ses sous-tickets sont clos.

Abandon : bilan expliquant pourquoi, puis `gh issue close <n> --reason "not planned"`,
« Fin » renseignée et statut « Terminé ».

### 7. Auditer

```sh
$P audit            # tout le projet ; code 1 au moindre écart
$P audit <n> <m>    # seulement ces tickets
$P voir <n>         # état d'un ticket
```

Corriger chaque écart signalé avant de rendre la main. Ne jamais contourner une règle :
si une règle ne convient pas, proposer au mainteneur de la changer dans `projet.py`.

## Preuves

Une preuve est une capture d'écran PNG qui montre le résultat, insérée en commentaire
du ticket et rangée dans `project-evidence/sonar-l10n-fr/<n>/`.

```sh
$C <url> /tmp/…/preuve.png [--sonar] [--cliquer Ignorer] [--texte "texte à encadrer"] [--selecteur css] [--pleine-page]
# Relire l'image (outil Read) AVANT publication : le dépôt de preuves est public.
$P preuve <n> /tmp/…/preuve.png --legende "Ce que montre la capture" --source <url capturée>
```

`--texte` encadre en rouge l'élément à prouver ; `--cliquer Ignorer` ferme le bandeau
promotionnel de SonarQube. Les captures se font en `fr-FR`, ce qui active le pack.

| Catégorie | Preuve attendue |
|---|---|
| Traduction, Bug | l'écran de la recette en français, libellé concerné encadré |
| Montée de version, Build & CI | « Infos système » de la recette sur la bonne version ; exécution GitHub Actions verte |
| Outillage | sortie de l'outil (fichier rendu ou page) montrant le résultat |
| Publication | page de la release, PR d'update-center fusionnée, fiche du pack dans la Marketplace |
| Communauté | l'annonce du projet une fois publiée (jamais un échange personnel du mainteneur) |
| Suivi amont | l'écran qui montre le défaut, puis le même écran corrigé ; le fil ou le ticket amont |
| Documentation | la page rendue sur GitHub |
| Décision | le commentaire qui consigne la décision, puis l'effet de la décision |

Interdits dans une capture : jetons, mots de passe, adresses de courriel, données
personnelles d'un tiers. En cas de doute, recadrer avec `--selecteur`.

### Préparer la recette

La stack `recette/docker-compose.yml` (port 9020) sert aux captures. SonarQube impose deux
écrans au premier passage ; les régler une fois par instance (à refaire après un `down -v`) :

```sh
export SQ_PASSWORD='<mot de passe local de la recette>'
curl -s -u admin:admin -X POST http://localhost:9020/api/users/change_password \
  --data-urlencode login=admin --data-urlencode previousPassword=admin --data-urlencode "password=$SQ_PASSWORD"
curl -s -u "admin:$SQ_PASSWORD" -X POST http://localhost:9020/api/settings/set \
  --data-urlencode key=sonar.plugins.risk.consent --data-urlencode value=ACCEPTED
```

Le jar à prouver : `tools/mvn.sh -B clean verify`, `docker cp target/sonar-l10n-fr-plugin-*.jar
sqfr-sonarqube:/opt/sonarqube/extensions/plugins/`, `docker compose -f recette/docker-compose.yml restart sonarqube`.

## Configuration du projet

`$P setup` (idempotent) pose le titre, la description, le README, la visibilité publique, les
options de Status (en conservant leurs identifiants, dont dépendent les automatisations
intégrées), les champs personnalisés, les quatre vues (Tableau, Tous les tickets, Feuille de
route, Historique) et crée le dépôt de preuves s'il manque.

L'API lit le regroupement et le tri des vues mais ne sait pas les écrire : ils se règlent une
fois dans l'interface (menu ▾ de l'onglet de la vue, puis « Save changes ») et `$P vues` les
vérifie. Attendu :

| Vue | Réglage |
|---|---|
| Tableau | colonnes par Status (défaut), sans filtre : « Terminé » visible ; tri Priorité croissante |
| Tous les tickets | groupé par Version SonarQube ; tri Priorité croissante |
| Feuille de route | dates : début = Début, cible = Fin (non lisible par l'API, à vérifier à l'œil) |
| Historique | tri Fin décroissante |

Automatisations intégrées (menu ⋯ du projet > Workflows), à régler aussi dans l'interface :
« Pull request linked to issue » doit poser **En revue**, pas « En cours » ; sinon chaque PR
déclasse les tickets qu'elle cite. « Item added to project » pose « À faire » (l'ouverture du
ticket règle ensuite le bon statut), « Item closed » et « Pull request merged » posent « Terminé ».
Après la fusion d'une PR, rédiger le bilan et renseigner « Fin » des tickets qu'elle a clos :
l'automatisation ferme le ticket mais ne remplit rien.
