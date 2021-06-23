FROM alpine:latest
ENV REGARDS_HOME /regards

# Copy JARs
# Storage
ADD local-storage-location-1.6.0-SNAPSHOT-shaded.jar ${REGARDS_HOME}/plugins/storage/
# Dam
ADD aip-datasource-1.6.0-SNAPSHOT-shaded.jar ${REGARDS_HOME}/plugins/dam/
ADD feature-datasource-1.6.0-SNAPSHOT-shaded.jar ${REGARDS_HOME}/plugins/dam/
ADD postgresql-datasource-1.6.0-SNAPSHOT-shaded.jar ${REGARDS_HOME}/plugins/dam/
ADD webservice-datasource-1.6.0-SNAPSHOT-shaded.jar ${REGARDS_HOME}/plugins/dam/
# Ingest
ADD enhanced-descriptive-aip-generation-1.6.0-SNAPSHOT-shaded.jar ${REGARDS_HOME}/plugins/ingest/
ADD ingest-test-plugins-1.6.0-SNAPSHOT-shaded.jar ${REGARDS_HOME}/plugins/ingest/
# Dataprovider
ADD custom-command-file-validation-1.6.0-SNAPSHOT-shaded.jar ${REGARDS_HOME}/plugins/dataprovider/
# Catalog
ADD download-metalink-plugin-1.6.0-SNAPSHOT-shaded.jar ${REGARDS_HOME}/plugins/catalog/
ADD download-plugin-1.6.0-SNAPSHOT-shaded.jar ${REGARDS_HOME}/plugins/catalog/
ADD fem-driver-plugin-1.6.0-SNAPSHOT-shaded.jar ${REGARDS_HOME}/plugins/catalog/
# FEM
ADD feature-factory-plugin-1.6.0-SNAPSHOT-shaded.jar ${REGARDS_HOME}/plugins/fem/
COPY datatypes ${REGARDS_HOME}/plugins/fem/datatypes
ADD feature-factory-test-plugin-1.6.0-SNAPSHOT-shaded.jar ${REGARDS_HOME}/plugins/fem/
# Notifier
ADD chronos-recipient-sender-1.6.0-SNAPSHOT-shaded.jar ${REGARDS_HOME}/plugins/notifier/

# Testing purpose : CMD [ "/bin/bash" ]
# first lets create the directory hierarchy if needed (mkdir -p) 
CMD mkdir -p /regards-plugins/storage/ && \
    mkdir -p /regards-plugins/dam/ && \
    mkdir -p /regards-plugins/ingest/ && \
    mkdir -p /regards-plugins/dataprovider/ && \
    mkdir -p /regards-plugins/catalog/ && \
    mkdir -p /regards-plugins/fem/datatypes && \
    mkdir -p /regards-plugins/notifier/ && \
	# Remove old storage plugins
	rm -f /regards-plugins/storage/local-storage-location-*.jar && \
	# Remove old dam plugins
	rm -f /regards-plugins/dam/aip-datasource-*.jar && \
	rm -f /regards-plugins/dam/feature-datasource-*.jar && \
	rm -f /regards-plugins/dam/postgresql-datasource-*.jar && \
	rm -f /regards-plugins/dam/webservice-datasource-*.jar && \
	# Remove old dataprovider plugins
	rm -f /regards-plugins/dataprovider/custom-command-file-validation*.jar && \
	# Remove old catalog plugins
	rm -f /regards-plugins/catalog/download-metalink-plugin*.jar && \
	rm -f /regards-plugins/catalog/download-plugin*.jar && \
	rm -f /regards-plugins/catalog/fem-driver-plugin*.jar && \
	# Remove old fem plugins
	rm -f /regards-plugins/fem/feature-factory-plugin*.jar && \
	rm -f /regards-plugins/fem/datatypes/* && \
	# Remove old notifier plugins
	rm -f /regards-plugins/notifier/chronos-recipient-sender*.jar && \
	# Remove old ingest plugins
	rm -f /regards-plugins/ingest/enhanced-descriptive-aip-generation*.jar && \
	rm -f /regards-plugins/ingest/ingest-test-plugins*.jar && \
	# copy JARs
	cp ${REGARDS_HOME}/plugins/storage/*.jar /regards-plugins/storage/ && \
	cp ${REGARDS_HOME}/plugins/dam/*.jar /regards-plugins/dam/ && \
	cp ${REGARDS_HOME}/plugins/ingest/*.jar /regards-plugins/ingest/ && \
	cp ${REGARDS_HOME}/plugins/dataprovider/*.jar /regards-plugins/dataprovider/ && \
	cp ${REGARDS_HOME}/plugins/catalog/*.jar /regards-plugins/catalog/ && \
	cp ${REGARDS_HOME}/plugins/fem/*.jar /regards-plugins/fem/ && \
	cp ${REGARDS_HOME}/plugins/fem/datatypes/* /regards-plugins/fem/datatypes && \
	cp ${REGARDS_HOME}/plugins/notifier/*.jar /regards-plugins/notifier/
