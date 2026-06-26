# RandomChestSpawner - Plugin Minecraft

Un plugin Spigot/Paper qui fait apparaitre des coffres avec des objets aleatoires a des emplacements varies pour les joueurs en ligne.

## Fonctionnalites

- **Apparition aleatoire de coffres**: Fait apparaitre un coffre avec 2 a 7 objets aleatoires
- **Distance centree**: Apparait au centre de tous les joueurs connectes, minimum 200 blocs
- **Distance intelligente**: Maximum 2000 blocs (ou plus si les joueurs sont tres eloignes)
- **Placement au sol**: Place les coffres **sur le sol** sur le bloc solide le plus haut (jamais en lair)
- **Limites du monde**: Coordonnees limitees a -10000 et 10000 en X/Z
- **Notifications chat**: Previens tous les joueurs quand un coffre apparait
- **Disparition apres pillage**: Le coffre disparait quand il est vide
- **Gestion automatique**: Demarre automatiquement quand un joueur rejoint
- **Apparition periodique**: Fait apparaitre un nouveau coffre toutes les 30 minutes

## Requirements

- Java 17+
- Serveur Spigot ou Paper 1.21+

## Compilation

```bash
mvn clean package
```

Le JAR compile sera dans `target/RandomChestSpawner-1.0.0.jar`.

## Installation

1. Telechargez `RandomChestSpawner-1.0.0.jar` depuis la page des releases
2. Copiez-le dans le dossier `plugins` de votre serveur
3. Redemarrez votre serveur

## Utilisation

- Quand un joueur rejoint, un coffre apparait a un emplacement aleatoire
- Des coffres supplementaires apparaissent toutes les 30 minutes
- Les coffres contiennent 2 a 7 objets aleatoires
- Les coordonnees sont annoncees dans le chat
- Le coffre spawn entre 200 et 2000 blocs des joueurs

## Licence

Projet open source sous licence MIT.
