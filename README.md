# REGARDS plugins

This repository brings together some of the REGARDS framework plugins :
* **AipDataSourcePlugin** : this plugin allows data extraction from REGARDS AIP storage
* **DefaultPostgreConnectionPlugin** : this plugin allows the connection to a PostgreSql database
* **PostgreDataSourcePlugin** : this plugin allows data extraction from a PostgreSql database
* **PostgreDataSourceFromSingleTablePlugin** : this plugins allows introspection and data extraction from a PostgreSql database
* **CatalogSecurityDelegation** : this plugin handling the security thanks to REGARDS catalog

This plugins **AipDataSourcePlugin**, **DefaultPostgreConnectionPlugin**, **PostgreDataSourcePlugin** and **PostgreDataSourceFromSingleTablePlugin** are useful for the REGARDS DAM microservice.

The plugin **CatalogSecurityDelegation** is useful for the REGARDS Storage microservice.

## Build requirements

Build relies on :
* Maven 3.3+
* OpenJDK 8

Prerequisite tools :
* Elasticsearch 5.4
* PostgreSQL 9.6
* RabbitMQ 3.6.5

Dependencies : 
* The REGARDS microservice framework and the DAM microservice should be generated.

## Build

```bash
git clone https://github.com/RegardsOss/regards-plugins.git
cd regards-plugins
mvn install -DskipTests -P install
```
