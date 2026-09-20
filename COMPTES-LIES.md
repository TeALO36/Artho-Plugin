# Comptes liés Bedrock ↔ Java

`/linkaccount` fusionne un compte **Bedrock** (PS5, Xbox, mobile…) et un compte **Java** en **un seul personnage**.

Une fois lié, le compte Bedrock se connecte **avec l'UUID et le pseudo du compte Java**. Pour le serveur, c'est le joueur Java : homes, inventaire, coffre de l'Ender, XP, succès, statistiques et position sont donc *ceux du compte Java*. Il n'y a **rien à synchroniser**, donc aucune synchro qui puisse se désynchroniser.

## Utilisation

Depuis le compte Bedrock (le cas courant, ex. la PS5) :

```
/linkaccount java <PseudoJava> <MotDePasseJava>
```

Le premier argument désigne la plateforme du compte **destination** : celui dont on veut récupérer le personnage. Son mot de passe (celui du `/register` Java) prouve qu'il vous appartient. Le plugin vous déconnecte alors quelques secondes ; **à la reconnexion vous jouez votre personnage Java**, et c'est définitif :

- **Bedrock** : plus aucun mot de passe. Une connexion Bedrock est authentifiée par Xbox Live, on ne peut pas l'usurper en tapant un pseudo, contrairement à un compte Java non premium.
- **Java** : le mot de passe habituel (`/login`).

Le lien se fait aussi dans l'autre sens, depuis le compte Java : `/linkaccount bedrock <PseudoBedrock> <MotDePasse>`. Le compte Bedrock doit alors avoir fait son propre `/register`, donc le sens Bedrock → Java est le plus simple.

`/linkaccount` fonctionne **avant** la connexion (comme `/login`) : un joueur Bedrock neuf n'a pas à créer un mot de passe juste pour pouvoir se lier.

| Commande | Effet |
|---|---|
| `/linkaccount <bedrock\|java> <pseudo> <mdp>` | Lier ce compte à un compte existant de l'autre plateforme |
| `/linkaccount status` | Voir l'état de sa liaison |
| `/unlinkaccount` | Séparer ses comptes (chaque compte retrouve son propre personnage) |
| `/unlinkaccount <pseudo>` | Opérateurs : délier le compte d'un joueur |
| `/artho link list` | Opérateurs : lister les liens et vérifier qu'ils sont bien connus de Floodgate |
| `/artho link unlink <pseudo>` | Opérateurs : délier, utilisable depuis la console |

## Ce qu'il faut savoir

- **Un seul des deux à la fois.** Les deux comptes sont le *même* joueur : se connecter sur l'un déconnecte l'autre (« logged in from another location »).
- **L'ancien personnage Bedrock est remplacé** par celui du compte Java. Il n'est pas perdu : ses fichiers (inventaire, succès, stats) sont copiés dans `plugins/Artho-Plugin/link-backups/<uuid>-<date>/`, et un admin peut les restaurer.
- **Les homes sont transmis** au compte Java (copie, pas déplacement : en cas de `/unlinkaccount`, l'ancien personnage les retrouve). Un home dont le nom existe déjà, ou qui dépasserait `teleport.max-homes`, n'est pas transmis ; le plugin le dit.
- **Le pseudo affiché change** : le compte Bedrock apparaît avec le pseudo Java, sans le préfixe `.`.

## Comment ça marche

Deux enregistrements qui doivent rester d'accord, tenus par `AccountLinkService` :

1. **`linked-accounts.yml`** : la trace du plugin (qui est lié à qui).
2. **La base de liaison de Floodgate** (`plugins/floodgate/linked-players.db`) : c'est *elle* qui change l'identité à chaque connexion.

Au démarrage, `AccountLinkService.reconcile()` **rejoue** le premier dans le second : une base Floodgate absente ou vidée est réparée au lieu de faire perdre tous les liens. Une opération qui échoue d'un côté est annulée de l'autre.

## Installation côté serveur

Il faut la liaison native de Floodgate **et** son extension de base de données (jar séparé, publié par GeyserMC) :

1. Télécharger `floodgate-sqlite-database.jar` (projet `floodgatedb` sur `download.geysermc.org`, même version que Floodgate) dans `plugins/floodgate/`.
2. Dans `plugins/floodgate/config.yml` :

```yaml
player-link:
  enabled: true
  require-link: false
  enable-own-linking: true
  allowed: false                 # les commandes de Floodgate ne servent pas, /linkaccount est celle d'Artho
  type: sqlite
  enable-global-linking: false   # OBLIGATOIRE sur un serveur non premium, voir ci-dessous
```

3. Redémarrer.

> **`enable-global-linking` doit rester à `false` sur un serveur en `online-mode=false`.** Le lien global associe un gamertag à un vrai compte Java Mojang : un joueur Bedrock verrait son identité remplacée par celle d'un compte premium homonyme, avec un UUID qui n'existe pas ici.

### Conflit de noms avec Floodgate

Dès que sa liaison est activée, Floodgate enregistre ses propres `/linkaccount` et `/unlinkaccount`, et dans le dispatcher **elles passent devant** celles de `plugin.yml`. `LinkCommandRouter` prend donc les deux commandes avant le dispatcher pour les joueurs. La console et RCON ne passent pas par là : d'où `/artho link …`, qui n'a pas de concurrent.

## Configuration (`features.account-link`)

```yaml
features:
  account-link:
    migrate-homes: true    # transmettre les homes de l'ancien compte Bedrock
    backup-on-link: true   # copier l'ancien personnage dans link-backups/
    reconnect-delay: 4     # secondes avant la reconnexion forcée
```

## Sécurité

- Les arguments de `/linkaccount` contiennent un mot de passe : la commande est interceptée avant le journal du serveur, qui n'en garde que `/linkaccount *****`, et `ConsoleFilter` la filtre en plus.
- Les tentatives de mot de passe comptent dans la même limite par IP que `/login` (blocage temporaire puis bannissement).
- Les messages d'erreur ne distinguent pas « compte inexistant » de « mauvais mot de passe ».
