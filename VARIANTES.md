# Variantes Liées — Guide de configuration de A à Z

Ce module permet de donner une **identité unique** (son et/ou apparence) à des
entités précises de votre serveur : un mob sur 50 qui a une texture spéciale, un
son qui ne se déclenche qu'au premier regard, un bruit quand on frappe telle
créature, une ambiance sonore selon la profondeur…

Le plugin ne fournit **aucun son ni aucune texture**. Il fournit les *points
d'accroche* ; c'est votre resource pack qui décide de ce qu'on entend et voit.
Tout se configure en YAML, sans toucher au code.

---

## Sommaire

1. [Concepts en 2 minutes](#1-concepts-en-2-minutes)
2. [Activer le module](#2-activer-le-module)
3. [Construire son resource pack](#3-construire-son-resource-pack)
4. [Déclarer une variante](#4-déclarer-une-variante)
5. [Catalogue des déclencheurs](#5-catalogue-des-déclencheurs)
6. [Déclencheurs globaux (non liés à une entité)](#6-déclencheurs-globaux)
7. [Commandes](#7-commandes)
8. [Exemples complets](#8-exemples-complets)
9. [Ce que Minecraft permet — et ne permet pas](#9-ce-que-minecraft-permet--et-ne-permet-pas)
10. [Dépannage](#10-dépannage)

---

## 1. Concepts en 2 minutes

| Terme | Ce que c'est |
|---|---|
| **Variante** | Un fichier YAML décrivant une identité : quel type d'entité, quels sons, quel déclencheur. |
| **Marquage** | Le fait qu'une entité précise porte une variante. Stocké dans l'entité elle-même, survit aux redémarrages. |
| **Déclencheur** | L'événement qui joue le son : premier regard, coup, clic droit, mort, proximité… |
| **Variante native** | Une variation d'apparence que Minecraft connaît déjà (couleur de cheval, etc.). Seul moyen *vanilla* de faire qu'une entité soit visuellement différente d'une autre du même type. |

Deux façons de marquer une entité :

- **Automatique** : `spawn-chance: 50` → 1 chance sur 50 à chaque apparition naturelle.
- **Manuelle** : viser l'entité et taper `/variant set <id>`.

> Les sons sont toujours joués **au joueur concerné uniquement**, jamais diffusés
> à tout le monde. Deux joueurs ont chacun leur propre audio.

---

## 2. Activer le module

Le module est **désactivé par défaut**. Tant qu'il l'est, aucun écouteur n'est
enregistré et aucune tâche ne tourne : le coût est nul.

Dans `plugins/Artho-Plugin/config.yml` :

```yaml
features:
  linked-variants:
    enabled: true
```

Ou en jeu, sans redémarrage :

```
/variant enable
/variant status
/variant disable
```

---

## 3. Construire son resource pack

### 3.1 Structure minimale

Remplacez `monserveur` par le nom de votre choix (le *namespace*). Évitez
`minecraft` pour vos ajouts, afin de ne pas écraser le jeu de base.

```
MonPack/
├── pack.mcmeta
└── assets/
    └── monserveur/
        ├── sounds.json
        └── sounds/
            └── mob/
                └── chauvesouris_cri.ogg
```

### 3.2 `pack.mcmeta`

```json
{
  "pack": {
    "pack_format": 88,
    "description": "Mon pack"
  }
}
```

> `pack_format` **dépend de la version de Minecraft**. `88` correspond à la
> 26.2. Pour une autre version, récupérez la valeur `pack_version.resource_major`
> dans le `version.json` du client, ou consultez le wiki Minecraft. Un mauvais
> numéro fait afficher « pack incompatible » (mais il fonctionne quand même la
> plupart du temps).

### 3.3 Format audio — le piège classique

Minecraft **n'accepte que du OGG Vorbis**. Et pour qu'un son soit *positionnel*
(qu'on l'entende venir d'une direction), il doit être **mono**. Un fichier
stéréo sera joué « dans la tête » du joueur, sans direction.

Conversion depuis n'importe quel format :

```bash
ffmpeg -i source.mp3 -ac 1 -ar 44100 -c:a libvorbis -q:a 5 sortie.ogg
```

`-ac 1` = mono. C'est le paramètre à ne pas oublier.

### 3.4 `sounds.json`

Il fait le lien entre un **nom d'événement** (utilisé dans vos variantes) et un
**fichier**.

```json
{
  "mob.chauvesouris": {
    "category": "neutral",
    "sounds": [
      { "name": "monserveur:mob/chauvesouris_cri" }
    ]
  }
}
```

- La clé (`mob.chauvesouris`) est libre. Vous l'utiliserez sous la forme
  `monserveur:mob.chauvesouris`.
- `name` est le **chemin du fichier** sans `.ogg`, relatif à `sounds/`.
- Plusieurs entrées dans `sounds` = Minecraft en tire une au hasard à chaque
  lecture. Pratique pour varier.
- Ajoutez `"stream": true` pour les sons longs (> ~5 s, musiques) : ils sont lus
  en flux au lieu d'être chargés en mémoire.

`category` contrôle quel curseur de volume du joueur s'applique :
`master`, `music`, `record`, `weather`, `block`, `hostile`, `neutral`, `player`,
`ambient`, `voice`.

### 3.5 Distribuer le pack

Le pack doit être téléchargeable **par les clients**, donc via une URL qu'ils
peuvent atteindre. Avec l'image `itzg/minecraft-server` :

```yaml
environment:
  RESOURCE_PACK: "http://mon-ip:25580/MonPack.zip"
  RESOURCE_PACK_SHA1: "<sha1 du zip>"
  RESOURCE_PACK_ENFORCE: "FALSE"
```

Le SHA1 se calcule avec `sha1sum MonPack.zip`. **Il doit être mis à jour à chaque
modification du pack**, sinon les clients gardent l'ancienne version en cache.

> Zippez le **contenu** du dossier (pack.mcmeta à la racine du zip), pas le
> dossier lui-même.

---

## 4. Déclarer une variante

Un fichier = une variante, dans `plugins/Artho-Plugin/variants/`.
**Le nom du fichier (sans `.yml`) est l'identifiant** utilisé dans les commandes.

`variants/chauvesouris_criarde.yml` :

```yaml
# --- Obligatoire ---
entity-type: BAT              # valeur de l'enum Bukkit EntityType

# --- Optionnel ---
display-name: "&5Chauve-souris criarde"
spawn-chance: 25              # 1 chance sur 25 à l'apparition ; 0 ou absent = manuel uniquement
native-variant: ""            # apparence native (voir §5.6)

# --- Déclencheurs (au moins un, sinon la variante ne fait rien) ---
on-damage:
  enabled: true
  sound:
    key: "monserveur:mob.chauvesouris"
    volume: 1.0
    pitch: 1.0
    category: NEUTRAL
```

### Champs communs à tous les sons

| Champ | Défaut | Rôle |
|---|---|---|
| `key` | — | **Obligatoire.** Nom de l'événement défini dans `sounds.json`, ou un son vanilla (`minecraft:entity.wither.spawn`). |
| `volume` | `1.0` | Au-dessus de 1.0, augmente la *portée* plutôt que le volume réel. |
| `pitch` | `1.0` | De `0.5` (grave) à `2.0` (aigu). |
| `category` | `MASTER` | Curseur de volume concerné (voir §3.4). |

---

## 5. Catalogue des déclencheurs

Chaque déclencheur s'active avec `enabled: true` et se configure indépendamment.
Une même variante peut en cumuler plusieurs.

### 5.1 `on-damage` — quand un joueur frappe l'entité

```yaml
on-damage:
  enabled: true
  sound: { key: "monserveur:mob.chauvesouris", volume: 1.0, pitch: 1.0, category: NEUTRAL }
```

Se déclenche **à chaque coup**, joué au joueur qui frappe.

### 5.2 `on-interact` — clic droit sur l'entité

```yaml
on-interact:
  enabled: true
  sound: { key: "monserveur:mob.salut", volume: 1.0, pitch: 1.0, category: NEUTRAL }
```

### 5.3 `on-death` — quand l'entité meurt

```yaml
on-death:
  enabled: true
  sound: { key: "monserveur:mob.victoire", volume: 1.0, pitch: 1.0, category: PLAYER }
```

Joué au joueur qui a porté le coup fatal. Ne se déclenche pas pour une mort
naturelle (chute, feu…).

### 5.4 `first-sight` — au premier regard, **une seule fois**

```yaml
first-sight:
  enabled: true
  sound: { key: "monserveur:mob.rencontre", volume: 1.0, pitch: 1.0, category: RECORD }
```

Le plugin lance un rayon depuis les yeux de chaque joueur. Si le rayon touche
l'entité **sans obstacle** et à moins de `max-distance` blocs, le son part.

Ce déclenchement est mémorisé **par joueur et par entité individuelle** : le
joueur ne le réentendra jamais pour *cette* entité, mais l'entendra à nouveau
sur une autre entité portant la même variante. La mémoire survit aux
redémarrages.

Réglages globaux dans `config.yml` :

```yaml
features:
  linked-variants:
    first-sight:
      max-distance: 10           # portée du rayon, en blocs
      check-interval-ticks: 10   # fréquence de vérification (20 ticks = 1 s)
```

### 5.5 `ambient` — en boucle tant qu'un joueur est à proximité

```yaml
ambient:
  enabled: true
  range: 20             # rayon d'audibilité en blocs
  interval-ticks: 240   # délai entre deux lectures (20 ticks = 1 s)
  sound: { key: "monserveur:mob.bourdonnement", volume: 0.8, pitch: 1.0, category: HOSTILE }
```

> **Réglez `interval-ticks` sur au moins la durée de votre son**, sinon les
> lectures se superposent. Un son de 12 s → `interval-ticks: 240` minimum.

### 5.6 `native-variant` — changer l'apparence

C'est le **seul moyen vanilla** de faire qu'une entité précise ait une texture
différente d'une autre du même type. Le principe : Minecraft connaît déjà
plusieurs apparences pour certains mobs ; on en réserve une, on la retexture
dans le pack, et le plugin la force sur les entités marquées.

Actuellement pris en charge : **cheval** (`Horse.Color`), valeurs possibles :
`WHITE`, `CREAMY`, `CHESTNUT`, `BROWN`, `BLACK`, `GRAY`, `DARK_BROWN`.

```yaml
entity-type: HORSE
spawn-chance: 50
native-variant: CREAMY
```

Puis dans le pack, remplacez
`assets/minecraft/textures/entity/horse/horse_creamy.png`.

Le plugin **écarte automatiquement** les chevaux non marqués de la couleur
réservée, pour qu'un cheval naturel ne porte pas votre texture par accident.

> Pour les autres mobs, laissez ce champ vide et utilisez uniquement des sons.
> Voir §9 pour ce qui est possible ou non.

---

## 6. Déclencheurs globaux

Ceux-ci ne sont liés à aucune entité et se configurent directement dans
`config.yml`.

### 6.1 `depth` — franchir une couche vers le bas

```yaml
features:
  linked-variants:
    depth:
      enabled: true
      chance: 5                # 1 chance sur 5 à chaque franchissement
      levels: [40, 0, -40]     # altitudes Y surveillées
      sound:
        key: "monserveur:ambient.grotte"
        volume: 1.0
        pitch: 1.0
        category: AMBIENT
```

Se déclenche à l'instant où le joueur **descend** sous une des altitudes listées.
Remonter ne déclenche rien.

### 6.2 `eat` — manger avec une condition de vie

```yaml
features:
  linked-variants:
    eat:
      enabled: true
      chance: 5                # 1 chance sur 5
      min-health-ratio: 0.75   # uniquement au-dessus de 3/4 de vie
      food-only: true          # false = déclenche aussi sur potions et lait
      sound:
        keys:                  # tirage aléatoire dans la liste
          - "monserveur:eat.son1"
          - "monserveur:eat.son2"
        volume: 1.0
        pitch: 1.0
        category: PLAYER
```

`min-health-ratio` va de `0.0` (toujours) à `1.0` (vie pleine uniquement).

`food-only: true` (défaut) limite le déclenchement à la nourriture. Mis à
`false`, il inclut aussi les potions et le lait, que Minecraft considère comme
« consommés ».

---

## 7. Commandes

Permission : `arthoplugin.variant.admin`.

| Commande | Effet |
|---|---|
| `/variant enable` / `disable` / `status` | Allumer / éteindre le module. |
| `/variant list` | Lister les variantes chargées. |
| `/variant reload` | Recharger le dossier `variants/` sans redémarrer. |
| `/variant set <id>` | Marquer l'entité visée (< 20 blocs). |
| `/variant remove` | Retirer la variante de l'entité visée. |
| `/variant info` | Afficher la variante de l'entité visée. |

`/variant reload` recharge les fichiers YAML, **pas** le resource pack : pour ce
dernier, il faut mettre à jour le SHA1 et faire reconnecter les joueurs.

---

## 8. Exemples complets

### 8.1 Chauve-souris qui crie quand on la frappe

`variants/chauvesouris.yml` :

```yaml
entity-type: BAT
display-name: "&5Chauve-souris criarde"
spawn-chance: 10
on-damage:
  enabled: true
  sound: { key: "monserveur:mob.cri_chauvesouris", volume: 1.0, pitch: 1.0, category: NEUTRAL }
```

`sounds.json` :

```json
{
  "mob.cri_chauvesouris": {
    "category": "neutral",
    "sounds": [{ "name": "monserveur:mob/cri_chauvesouris" }]
  }
}
```

Fichier : `assets/monserveur/sounds/mob/cri_chauvesouris.ogg` (mono).

### 8.2 Boss rare : apparence + musique de rencontre + râle de mort

```yaml
entity-type: HORSE
display-name: "&6Le Destrier"
spawn-chance: 100
native-variant: CREAMY

first-sight:
  enabled: true
  sound: { key: "monserveur:boss.theme", volume: 1.0, pitch: 1.0, category: RECORD }

on-death:
  enabled: true
  sound: { key: "monserveur:boss.mort", volume: 1.0, pitch: 0.8, category: PLAYER }
```

### 8.3 Mob d'ambiance sonore

```yaml
entity-type: PHANTOM
spawn-chance: 3
first-sight:
  enabled: false
ambient:
  enabled: true
  range: 20
  interval-ticks: 240
  sound: { key: "monserveur:mob.bourdonnement", volume: 0.8, pitch: 1.0, category: HOSTILE }
```

---

## 9. Ce que Minecraft permet — et ne permet pas

C'est là que la plupart du temps est perdu. À lire **avant** de concevoir.

### ✅ Possible

| Objectif | Méthode |
|---|---|
| Un mob sur N joue un son | `spawn-chance` + n'importe quel déclencheur. Fonctionne sur **tous** les types d'entité. |
| Un mob sur N a une **apparence** différente | Uniquement via `native-variant`, donc uniquement pour les mobs ayant des variantes natives (cheval, et selon la version : grenouille, vache, chat, lama, perroquet…). |
| Un **bloc** sur N a une apparence différente | Pas besoin du plugin : utilisez les **variantes pondérées** de blockstate dans le pack (voir ci-dessous). |
| Changer l'apparence de **tous** les mobs d'un type | Remplacez simplement la texture dans `assets/minecraft/textures/entity/…`. |

**Variantes pondérées de blocs** — exemple pour qu'un lit rouge sur 5 soit
différent, dans `assets/minecraft/blockstates/red_bed.json` :

```json
{
  "variants": {
    "facing=north,part=foot": [
      { "model": "minecraft:block/red_bed_foot", "weight": 4 },
      { "model": "monserveur:block/red_bed_foot_special", "weight": 1 }
    ]
  }
}
```

Le tirage se fait **par position de bloc**. Attention : deux blocs distincts
(la tête et le pied d'un lit) sont tirés **indépendamment** — pour un résultat
cohérent, ne variez qu'une des deux moitiés.

### ❌ Impossible en vanilla

| Objectif | Pourquoi |
|---|---|
| Donner une texture unique à **un** mob dont le type n'a pas de variantes natives (golem de fer, creeper…) | Les textures d'entité sont associées au *type*, pas à l'instance. Aucun mécanisme vanilla ne permet de les différencier. |
| Un skin totalement custom sur un mob | Nécessite un mod côté client (OptiFine CEM, Entity Model Features…) ou la technique de l'`ItemDisplay` monté sur un mob invisible — qui fige l'animation. |
| Faire varier une texture d'entité aléatoirement | Idem : pas de variantes pondérées pour les entités, seulement pour les blocs. |

**Contournements** pour un mob visuellement unique sans variante native :
donnez-lui un nom affiché, un effet de brillance, ou des particules — ce sont les
seuls marqueurs visuels réellement par-instance en vanilla.

---

## 10. Dépannage

| Symptôme | Cause probable |
|---|---|
| Aucun son | Le module est éteint (`/variant status`), ou le déclencheur n'a pas `enabled: true`. |
| Aucun son, et rien dans les logs | La `key` ne correspond à aucune entrée de `sounds.json`, ou le pack n'est pas appliqué côté client. |
| Son audible mais sans direction | Le `.ogg` est en **stéréo**. Reconvertir en mono (`-ac 1`). |
| Le pack ne se met pas à jour | Le `RESOURCE_PACK_SHA1` n'a pas été changé : le client sert son cache. |
| « Pack incompatible » | `pack_format` ne correspond pas à la version du serveur. |
| Les sons se superposent | `interval-ticks` d'`ambient` est plus court que la durée du son. |
| `first-sight` ne se redéclenche pas | C'est le comportement voulu : une fois par joueur **et par entité**. Testez sur une autre entité. |
| Le son est joué trop rarement | Vérifiez `spawn-chance` : `50` signifie 1 sur 50, pas 50 %. |
| Variante ignorée au chargement | `entity-type` invalide. Le nom exact figure dans les logs au démarrage. |

Les erreurs de chargement sont toujours signalées dans la console au démarrage et
lors d'un `/variant reload`, préfixées par `[Variants]`.
