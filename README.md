# REGARDS OSS PLUGINS

This repository contains REGARDS plugins that can be used on the REGARDS microservices available in the REGARDS Backend repository.
REGARDS plugins are shaded jars that only contain plugins and framework utils binaries, see [Maven Shade Plugin](https://maven.apache.org/plugins/maven-shade-plugin/).

# Build

## Requirements

### The build relies on

* Maven v3.8.4+
* JDK Eclipse Temurin v17.0.3+

#### For docker images generations
* Docker engine v27+ (https://docs.docker.com/engine/install/rhel/)

### Prerequisite tools

* Elasticsearch 7.17.22
* PostgreSQL 11
* RabbitMQ 3.11

### Repository dependency
* regards-oss-backend repository at https://github.com/RegardsOss/regards-backend

### Environment prerequisites

#### For compilation, generation and unit testing 
To compile, generate, and perform unit testing, a computer or virtual machine with the following specifications is required:
* CPU : 64-bit with at least 4 threads, clocked at 2.5 GHz or higher (e.g., Intel Core i5 8th generation or equivalent) 
* RAM : 12 GB or more
* Disk space : 50 GB available
* Operating System : Red Hat Enterprise Linux 8.x (64-bit)

#### For integration testing
For integration tests a computer or virtual machine with the following specifications is required:
* CPU : 64-bit with at least 4 threads, clocked at 2.5 GHz or higher (e.g., Intel Core i5 8th generation or equivalent) 
* RAM : 16 GB or more
* Disk space : 50 GB available
* Operating System : Red Hat Enterprise Linux 8.x (64-bit)

### Maven Configuration
#### Environment variables
The following environment variables are required: 
 - `REGARDS_HOME`: Used by the compilation process to locate the source files.
 - `MAVEN_HOME`: Defines the Maven home directory for configuration and dependencies repository.
 - `REGARDS_DOCKER_IMAGE_TAG`: Tag used to generate the REGARDS Docker image (default: latest).

#### settings.xml
The dependencies repository has to be configured in the .m2/settings.xml
```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
    <localRepository>${MAVEN_HOME}/repository</localRepository>
    <interactiveMode>true</interactiveMode>
    <offline>false</offline>
</settings>
``` 

## How to

### Build the app locally 

```bash
cd <build_directory>
git clone https://github.com/RegardsOss/regards-plugins.git
cd regards-plugins
export REGARDS_HOME=<build_directory>/regards-backend/
export MAVEN_HOME=<build_directory>/maven
mvn install -DskipTests -P install
```

#### Expected results
Expected shaded jars for version X.Y.Z are: 

- <build_directory>/regards-plugins/ingest-plugins/send-delete-files-worker-request-plugin/target/send-delete-files-worker-request-plugin-X.Y.Z-SNAPSHOT-shaded.jar
- <build_directory>/regards-plugins/ingest-plugins/ingest-test-plugin/target/ingest-test-plugin-X.Y.Z-SNAPSHOT-shaded.jar
- <build_directory>/regards-plugins/ingest-plugins/enhanced-descriptive-aip-generation-plugin/target/enhanced-descriptive-aip-generation-plugin-X.Y.Z-SNAPSHOT-shaded.jar
- <build_directory>/regards-plugins/catalog-plugins/fem-driver/fem-driver-plugin/target/fem-driver-plugin-X.Y.Z-SNAPSHOT-shaded.jar
- <build_directory>/regards-plugins/catalog-plugins/download-plugin/target/download-plugin-X.Y.Z-SNAPSHOT-shaded.jar
- <build_directory>/regards-plugins/catalog-plugins/stac/stac-plugin/target/stac-plugin-X.Y.Z-SNAPSHOT-shaded.jar
- <build_directory>/regards-plugins/catalog-plugins/download-metalink-plugin/target/download-metalink-plugin-X.Y.Z-SNAPSHOT-shaded.jar
- <build_directory>/regards-plugins/catalog-plugins/export-csv-plugin/target/export-csv-plugin-X.Y.Z-SNAPSHOT-shaded.jar
- <build_directory>/regards-plugins/dataprovider-plugins/radical-product-name-plugin/target/radical-product-name-plugin-X.Y.Z-SNAPSHOT-shaded.jar
- <build_directory>/regards-plugins/dataprovider-plugins/custom-command-file-validation-plugin/target/custom-command-file-validation-plugin-X.Y.Z-SNAPSHOT-shaded.jar
- <build_directory>/regards-plugins/notifier-plugins/rabbitmq-sender-plugin/target/rabbitmq-sender-plugin-X.Y.Z-SNAPSHOT-shaded.jar
- <build_directory>/regards-plugins/notifier-plugins/dissemination-ack-sender-plugin/target/dissemination-ack-sender-plugin-X.Y.Z-SNAPSHOT-shaded.jar
- <build_directory>/regards-plugins/notifier-plugins/worker-manager-sender-plugin/target/worker-manager-sender-plugin-X.Y.Z-SNAPSHOT-shaded.jar
- <build_directory>/regards-plugins/notifier-plugins/lta-request-sender-plugin/target/lta-request-sender-plugin-X.Y.Z-SNAPSHOT-shaded.jar
- <build_directory>/regards-plugins/storage-plugins/s3-glacier-mock/s3-glacier-mock-plugin/target/s3-glacier-mock-plugin-X.Y.Z-SNAPSHOT-shaded.jar
- <build_directory>/regards-plugins/storage-plugins/s3-glacier-storage/s3-glacier-storage-plugin/target/s3-glacier-storage-plugin-X.Y.Z-SNAPSHOT-shaded.jar
- <build_directory>/regards-plugins/storage-plugins/local-storage-location/local-storage-location-plugin/target/local-storage-location-plugin-X.Y.Z-SNAPSHOT-shaded.jar
- <build_directory>/regards-plugins/storage-plugins/s3-storage/s3-storage-plugin/target/s3-storage-plugin-X.Y.Z-SNAPSHOT-shaded.jar
- <build_directory>/regards-plugins/processing-plugins/simple-shell-plugin/target/simple-shell-plugin-X.Y.Z-SNAPSHOT-shaded.jar
- <build_directory>/regards-plugins/dam-plugins/feature-datasource-plugin/target/feature-datasource-plugin-X.Y.Z-SNAPSHOT-shaded.jar
- <build_directory>/regards-plugins/dam-plugins/postgresql-datasource-plugin/target/postgresql-datasource-plugin-X.Y.Z-SNAPSHOT-shaded.jar
- <build_directory>/regards-plugins/dam-plugins/mock-datasources-plugin/target/mock-datasources-plugin-X.Y.Z-SNAPSHOT-shaded.jar
- <build_directory>/regards-plugins/dam-plugins/webservice-datasource-plugin/target/webservice-datasource-plugin-X.Y.Z-SNAPSHOT-shaded.jar
- <build_directory>/regards-plugins/dam-plugins/aip-datasource-plugin/target/aip-datasource-plugin-X.Y.Z-SNAPSHOT-shaded.jar
- <build_directory>/regards-plugins/dam-plugins/batch-retry-test-plugin/target/batch-retry-test-plugin-X.Y.Z-SNAPSHOT-shaded.jar

### Build the docker images

#### Plugin base image

All REGARDS plugins docker images are based on the `regards-plugin` image.  
This image is accessible through the REGARDS github docker registry: `ghcr.io/regardsoss`.

The base docker image and registries are defined in the parent pom in the artifact regards-oss-backend. If you want to use an alternate docker registry, you can either edit the regards-oss-backend pom.xml to change the registry (the default being the regards official github package repository) or override it in the root pom of regards-oss-plugins:
 - `docker.registry.host` : ghcr.io/regardsoss

 #### Generation

To generate the docker images, REGARDS uses the [maven jib plugin](https://github.com/GoogleContainerTools/jib/tree/master/jib-maven-plugin).

```bash
cd <build_directory>
git clone https://github.com/RegardsOss/regards-plugins
export REGARDS_HOME=<build_directory>/regards-backend
cd regards-plugins
mvn clean package jib:dockerBuild -P delivery,docker -B -Dfile.encoding=UTF-8 -Dmaven.test.skip -DimageTag=${REGARDS_DOCKER_IMAGE_TAG:=latest}
```

#### Expected results 

You can list locally generated docker images with the following  commands:
```bash
docker images --format "{{.Repository}}:{{.Tag}}" | egrep ".*/rs-.*:${REGARDS_DOCKER_IMAGE_TAG:=latest}$" | sort
```

Expected results with `tag` = `REGARDS_DOCKER_IMAGE_TAG` or `latest` if no one is specified: 
- <docker.registry.host>/rs-send-delete-files-worker-request-plugin:<tag>
- <docker.registry.host>/rs-ingest-test-plugin:<tag>
- <docker.registry.host>/rs-enhanced-descriptive-aip-generation-plugin:<tag>
- <docker.registry.host>/rs-fem-driver-plugin:<tag>
- <docker.registry.host>/rs-download-plugin:<tag>
- <docker.registry.host>/rs-stac-plugin:<tag>
- <docker.registry.host>/rs-download-metalink-plugin:<tag>
- <docker.registry.host>/rs-export-csv-plugin:<tag>
- <docker.registry.host>/rs-radical-product-name-plugin:<tag>
- <docker.registry.host>/rs-custom-command-file-validation-plugin:<tag>
- <docker.registry.host>/rs-rabbitmq-sender-plugin:<tag>
- <docker.registry.host>/rs-dissemination-ack-sender-plugin:<tag>
- <docker.registry.host>/rs-worker-manager-sender-plugin:<tag>
- <docker.registry.host>/rs-lta-request-sender-plugin:<tag>
- <docker.registry.host>/rs-s3-glacier-mock-plugin:<tag>
- <docker.registry.host>/rs-s3-glacier-storage-plugin:<tag>
- <docker.registry.host>/rs-local-storage-location-plugin:<tag>
- <docker.registry.host>/rs-s3-storage-plugin:<tag>
- <docker.registry.host>/rs-simple-shell-plugin:<tag>
- <docker.registry.host>/rs-feature-datasource-plugin:<tag>
- <docker.registry.host>/rs-postgresql-datasource-plugin:<tag>
- <docker.registry.host>/rs-mock-datasources-plugin:<tag>
- <docker.registry.host>/rs-webservice-datasource-plugin:<tag>
- <docker.registry.host>/rs-aip-datasource-plugin:<tag>
- <docker.registry.host>/rs-batch-retry-test-plugin:<tag>

## Tests environment requirements

### Unit test

There's no prerequisites to run REGARDS unit tests. Once compiled you can run the tests with the command: 
```bash
cd <build_directory>/regards-plugin
mvn test
```

#### For integration testing

For integration tests a computer or virtual machine with the following specifications is required:
* CPU : 64-bit with at least 4 threads, clocked at 2.5 GHz or higher (e.g., Intel Core i5 8th generation or equivalent) 
* RAM : 16 GB or more
* Disk space : 50 GB available
* Operating System : Red Hat Enterprise Linux 8.x (64-bit)


The 4 following COTS are required to run REGARDS Integration tests:
 - Postgres
 - Elasticsearch
 - Rabbitmq
 - MinIO
Depending on the tests you want to run, all COTS may not be required.

## Sources

- authentication-plugins: Plugins defining an identification protocol. [See the available plugins in the doc !](https://regardsoss.github.io/docs/next/development/backend/services/authentication/contributor-guides/plugins)
- catalog-plugins: Plugins adding search and catalog functionnalities. [See the available plugins in the doc !](https://regardsoss.github.io/docs/next/development/services/catalog/plugins/listing)
- dam-plugins: Plugins defining a datasource used to populate the ElasticSearch database [See the available plugins in the doc !](https://regardsoss.github.io/docs/next/development/services/dam/plugins/overview)
- dataprovider-plugins: Plugins performing the different steps of the acquisition workflow [See the available plugins in the doc !](https://regardsoss.github.io/docs/next/development/backend/services/dataprovider/plugins/listing)
- ingest-plugins: Plugins performing the different steps of the ingestion workflow [See the available plugins in the doc !](https://regardsoss.github.io/docs/next/development/backend/services/ingest/plugins/listing)
- notifier-plugins: Plugins used to events to a specific recipient [See the available plugins in the doc !](https://regardsoss.github.io/docs/next/development/backend/services/notifier/plugins/listing)
- processing-plugins: Plugins used to launch external executables from the processing microservice. [See the available plugins in the doc !](https://regardsoss.github.io/docs/next/development/backend/services/processing/plugins)
- regards-ci: This module contains all configuration files and scripts for Jenkins CI/CD.
- storage-plugins: Plugins accessing storage locations. [See the available plugins in the doc !](https://regardsoss.github.io/docs/next/development/backend/framework/modules/plugins)