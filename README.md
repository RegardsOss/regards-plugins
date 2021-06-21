# REGARDS OSS PLUGINS

This repository brings together some of the REGARDS framework plugins.

## Build requirements

Build relies on :
* Maven 3.3+
* OpenJDK 8

Prerequisite tools :
* Elasticsearch 5.4
* PostgreSQL 9.6
* RabbitMQ 3.6.5

Dependencies : 
* regards-oss-backend repository at https://github.com/RegardsOss/regards-oss-backend

## Build

```bash
git clone https://github.com/RegardsOss/regards-plugins.git
cd regards-plugins
mvn install -DskipTests -P install
```
