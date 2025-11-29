# ArthoDonation

Plugin Minecraft pour ArthoNetwork permettant de diffuser des messages de dons configurables avec support de couleurs et variables.

## Fonctionnalités

- **Diffusion Automatique** : Messages diffusés à intervalle régulier.
- **Lien Configurable** : Utilisez la variable `$link` dans vos messages pour afficher le lien de don.
- **Gestion en Jeu** : Commandes pour ajouter des messages ou changer le lien sans toucher aux fichiers.
- **Support Couleurs** : Utilise les codes couleurs classiques de Minecraft (`&`).

## Commandes

Permission requise : `arthodonation.admin`

| Commande | Description |
|----------|-------------|
| `/don add <message>` | Ajoute un nouveau message à la liste de diffusion. |
| `/don link <url>` | Modifie le lien de don (remplace `$link`). |
| `/don reset` | Réinitialise la configuration aux valeurs par défaut. |
| `/don reload` | Recharge la configuration depuis le fichier. |

## Configuration (`config.yml`)

```yaml
# Intervalle en secondes entre chaque message
interval: 300

# Le lien de don (remplacé par $link)
donation-link: "https://buy.stripe.com/14k8Al4ki5tl8HmfZb"

# Liste des messages
messages:
  - "&6[ArthoNetwork] &eSoutenez le serveur ! &b$link"
```

## Installation

1. Téléchargez la dernière version depuis l'onglet [Releases](https://github.com/TeALO36/Artho-Plugin/releases).
2. Glissez le fichier `.jar` dans le dossier `plugins` de votre serveur.
3. Redémarrez votre serveur.
