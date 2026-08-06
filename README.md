| `/auth unregister <joueur>` | Désenregistrer un joueur. |
| `/auth reset <joueur>` | Réinitialiser le mot de passe d'un joueur. |
| `/auth whitelist <add/remove/list/on/off>` | Gérer la whitelist. |
| `/auth set <max-attempts/timeout> <valeur>` | Configurer la sécurité. |

## Configuration (`config.yml`)

```yaml
# Intervalle en secondes entre chaque message
interval: 300

# Le lien de don (remplacé par $link)
donation-link: "https://buy.stripe.com/14k8Al4ki5tl8HmfZb"

# Messages de diffusion
messages:
  - "&d&lArthoNetwork &7» &fSoutenez le serveur : &d$link"

# Configuration Authentification
auth:
  whitelist:
    kick-message: "&cVous n'êtes pas sur la whitelist !"
  messages:
    title: "&cAuthentification Requise"
    # ... autres messages configurables
```

## Installation

1. Téléchargez la dernière version depuis l'onglet [Releases](https://github.com/TeALO36/Artho-Plugin/releases).
2. Glissez le fichier `.jar` dans le dossier `plugins` de votre serveur.
3. Redémarrez votre serveur.

## Module "Variantes Liées" (Linked Variants)

> 📖 **[Guide de configuration complet de A à Z → VARIANTES.md](VARIANTES.md)**
> — construire son resource pack, déclarer une variante, catalogue de tous les
> déclencheurs, exemples, limites de Minecraft et dépannage.


Système **générique** pour donner une identité visuelle et/ou sonore unique à
une **entité précise** (pas à un type d'entité entier) — par exemple un mob
"spécial"/nommé qui a son propre skin (via resource pack) et son propre son
de rencontre. Le plugin ne fournit ni resource pack ni fichiers audio/texture :
il expose juste les points d'accroche (custom-model-data, clé de son) que
*votre* resource pack peut définir. Toute la logique (écouteurs, raycast) est
encapsulée dans une "Feature" **désactivée par défaut**.

### Activer le module

Dans `config.yml` :

```yaml
features:
  linked-variants:
    enabled: false        # passez à true, ou utilisez la commande ci-dessous
    first-sight:
      max-distance: 10          # portée du raycast en blocs
      check-interval-ticks: 10  # fréquence de vérification (20 ticks = 1s)
```

Ou en jeu (sans redémarrage) :

```
/variant enable
/variant disable
/variant status
```

Tant que `enabled: false`, **aucun** écouteur n'est enregistré et **aucun**
raycast ne tourne — le coût est nul, pas juste masqué.

### Déclarer une variante générique

Chaque variante est un fichier YAML indépendant dans
`plugins/Artho-Plugin/variants/<id>.yml` (le nom du fichier = son id). Un
exemple commenté (`example_variant.yml`) est généré automatiquement au
premier démarrage si le dossier est vide.

```yaml
entity-type: ZOMBIE          # obligatoire, valeur de org.bukkit.entity.EntityType
display-name: "&5&lExemple"  # optionnel, informatif

visual:                      # optionnel : identité visuelle
  item: PLAYER_HEAD          # Material Bukkit à placer dans le slot d'équipement
  custom-model-data: 100001  # entier libre, à faire correspondre dans votre pack
  equipment-slot: HEAD       # HEAD, CHEST, LEGS, FEET, HAND, OFF_HAND

first-sight:                 # optionnel : événement au premier regard
  enabled: true
  sound:
    key: "arthonetwork:variant.example.first_sight"  # clé de son (vanilla ou custom pack)
    volume: 1.0
    pitch: 1.0
    category: MASTER
```

Une fois le fichier en place, appliquez-la à une entité précise en la
regardant en jeu puis :

```
/variant set <id>       # assigne la variante à l'entité visée (< 20 blocs)
/variant remove         # retire la variante de l'entité visée
/variant info            # affiche la variante de l'entité visée (debug)
/variant list            # liste les variantes chargées
/variant reload          # recharge le dossier variants/ depuis le disque
```

### Fonctionnement du "First Sight"

- Chaque tick de vérification (`check-interval-ticks`), le plugin fait un
  raycast (entités + blocs, donc bloqué par les murs) depuis les yeux de
  chaque joueur en ligne, jusqu'à `max-distance` blocs.
- Si le rayon touche une entité qui porte une variante avec `first-sight.enabled: true`,
  et que ce joueur n'a **jamais** eu de "First Sight" pour **cette entité
  précise** (UUID), le son configuré est joué et l'événement est marqué comme
  vu.
- Le marquage "vu" est stocké par joueur et par UUID d'entité (pas juste par
  variante) : deux zombies différents portant la même variante déclenchent
  chacun leur propre "First Sight". La liaison variante ↔ entité est stockée
  dans le `PersistentDataContainer` de l'entité ; la mémoire "déjà vu" est
  stockée dans le `PersistentDataContainer` du joueur (persiste entre les
  sessions, mise en cache en mémoire pendant que le joueur est connecté).
