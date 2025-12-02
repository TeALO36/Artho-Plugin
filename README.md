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
