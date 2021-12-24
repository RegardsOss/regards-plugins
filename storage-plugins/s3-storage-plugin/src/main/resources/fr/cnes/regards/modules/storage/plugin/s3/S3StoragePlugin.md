Plugin S3 Storage Online
========================

Ce plugin a pour objet de stocker des fichiers dans un espace de stockage
répondant à une API AWS S3.

Le plugin permet :
- d'enregistrer des objets dans un serveur S3
- de lire le contenu d'objets depuis un serveur S3
- de supprimer des objets d'un serveur S3.

Le plugin se paramètre ainsi :
- `endpoint`: adresse http(s) du serveur S3
- `region`: zone géographique pour les serveurs S3 ciblés
- `bucket`: nom du bucket dans lequel stocker les objets
- `key`/`secret`: identifiants d'utilisateur
- `rootPath`: chemin de base dans le bucket pour tous les objets — ceci permet de potentiellement utiliser un même bucket pour plusieurs instances de plugin de stockage S3, en fournissant un namespace compartimentant les objets dans des zones séparées
- `multipartThresholdMb`: seuil en Mb au-delà duquel le plugin utilise l'API multipart pour enregistrer des objets (et taille en Mb des parts)

Elements de conception
======================

### Fonctionnement adapté au mode asynchrone

La librairie Java officielle fournie par Amazon pour la connexion aux API
S3 offre deux modes de fonctionnement :
- synchrones (les appels au serveur bloquent le thread d'exécution jusqu'à la réception de la réponse)
- asynchrone (les appels au serveur ne bloquent pas le thread et un appel à une fonction callback fournie est exécutée lors de réception des données).

Actuellement, le composant Regards rs-storage fonctionne uniquement en mode
synchrone, cependant on peut facilement simuler un fonctionnement synchrone
avec une API asynchrone (l'inverse n'étant pas vrai). Pour cette raison, on utilise l'API asynchrone de l'API S3, ceci permettant de s'adapter aux deux modes.

### Couche API bas-niveau

La couche "API bas-niveau" est donc l'API S3 fournie par le composant maven `software.amazon.awssdk:s3`. On utilise le client asynchrone `S3AsyncClient` pour se connecter aux serveurs S3.

### Couche API haut-niveau

Cette couche offre les avantages suivants :
- elle fournit une abstraction au-dessus de l'API S3 permettant de se concentrer
  sur les cas d'utilisation métier du plugin,
- utilisation de la librairie Reactor (intégrée à Spring) afin de pouvoir facilement basculer entre les modes synchrone/asynchrone (le paramétrage des schedulers, l'utilisation des méthodes telles que `Mono#block()` permettent de retrouver un comportement synchrone),
- les difficultés liées à la gestion d'erreurs du client bas niveau sont cachées (une instance du client bas niveau devient parfois, du fait d'erreurs réseau, inutilisable ; il faut donc recréer un client bas niveau, cette logique peut alourdir inutilement le code si elle n'est pas isolée du code du plugin).

La classe `S3AsyncClientReactorWrapper` fournit le point d'entrée de l'API haut niveau.

La classe `S3ClientReloader` fournit le moyen de toujours obtenir un client de l'API bas niveau en état de marche.

#### Commandes et résultats

Les interfaces `StorageCommand` et `StorageCommandResult` permettent de définir les opérations disponibles pour le plugin, ainsi que leurs résultats possibles. Les quatre types de commandes définies dans `StorageCommand` sont:
- `Check`: pour vérifier la présence d'un objet dans S3
- `Read`: pour récupérer le contenu d'un objet stocké dans S3
- `Write`: pour stocker un objet dans S3 à partir d'un flux de données
- `Delete`: pour supprimer un objet déjà stocké dans S3

Chaque type de commande a des types de résultats associés, par exemple la commande `Check` est associée aux résultats implémentant `CheckResult`:
- `CheckPresent` (si l'objet est présent dans S3),
- `CheckAbsent` (si l'objet est absent),
- `UnreachableStorage` (si le serveur S3 n'est pas joignable).
  De même pour les autres types de commande.

### Couche plugin

Le plugin utilise uniquement l'API haut niveau. Pour chaque opération demandée, il transforme
la requête/l'ensemble de requêtes reçues en un ensemble de commandes de haut niveau et appelle la fonction correspondante dans l'API de haut niveau. Il reçoit les résultats sous forme de `Mono` de l'API Reactor, et bloque dessus pour simuler un mode d'exécution synchrone. En fonction du type de résultat de la commande, il peut notifier le `progressManager` correspondant de la manière appropriée.

Plugin NearLine
===============

Le plugin NearLine n'est pas encore réalisé, mais aura probablement besoin d'utiliser _in fine_ :
- la propriété "storageClass" dans les requêtes de type `PutObjecRequest` ou `CreateMultipartUploadRequest`), potentiellement alimentée par une valeur paramétrable dans le plugin (différents fournisseurs de service S3 peuvent avoir des noms différents pour les classes de stockage, par exemple sur [Flexible Engine](https://cloud.orange-business.com/offres/infrastructure-iaas/flexible-engine/fonctionnalites/object-storage-service/))
- l'opération `RestoreObjectRequest`, permettant de récupérer temporairement le contenu d'un objet stocké avec une classe de stockage "froide" (archivage de longue durée avec temps de restauration).

Pour permettre de répondre à ce besoin, deux types de commandes sont ajoutées :
- `Archive`: fait exactement la même chose que `Write`, mais ajoute un storageClass différent à l'objet. Ce `storageClass` est un paramètre fourni à l'API haut niveau, via un paramètre de plugin.
- `Restore`: appelle l'API bas niveau `RestoreObjectRequest`, et boucle tant que la restauration n'est pas complète. une fois la restauration complète, se comporte comme une commande `Read`.
