# Artho-Plugin

Plugin Minecraft pour ArthoNetwork permettant de diffuser des messages de dons configurables avec support de couleurs et variables.

## Fonctionnalités

- **Diffusion Automatique** : Messages diffusés à intervalle régulier.
- **Lien Configurable** : Utilisez la variable `$link` dans vos messages pour afficher le lien de don.
- **Gestion en Jeu** : Commandes pour ajouter des messages ou changer le lien sans toucher aux fichiers.
- **Support Couleurs** : Utilise les codes couleurs classiques de Minecraft (`&`).

## Compatibilité

Ce plugin est compatible avec les versions de Minecraft **1.13 à 1.20.1**.

## Commandes

### Général
| Commande | Description |
|----------|-------------|
| `/ping` | Affiche votre latence (ping). |
| `/lag joueur` | Analyse les entités et chunks autour de vous. |
| `/lag serveur` | Affiche l'utilisation RAM et le nombre total d'entités. |
| `/server add <texte>` | Proposer une fonctionnalité. |
| `/server list` | Voir la liste des suggestions actives. |

### Authentification
| Commande | Description |
|----------|-------------|
| `/register <mdp> <confirm>` | S'enregistrer sur le serveur. |
| `/login <mdp>` | Se connecter. |
| `/changepassword <nouveau> <confirm>` | Changer son mot de passe (Requis après reset). |

### Administration (Permission: `arthoplugin.admin`)
| Commande | Description |
|----------|-------------|
| `/don add <message>` | Ajoute un nouveau message à la liste de diffusion. |
| `/don link <url>` | Modifie le lien de don. |
| `/don enable/disable` | Active/Désactive les messages de don. |
| `/don reset/reload` | Réinitialise ou recharge la configuration. |
| `/server remove <id>` | Supprimer une suggestion. |
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
