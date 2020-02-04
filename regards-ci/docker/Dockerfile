FROM alpine:latest
ENV REGARDS_HOME /regards

# Copy JARs
# Storage
ADD local-storage-location-GEODE-shaded.jar ${REGARDS_HOME}/plugins/storage/
# Dam
ADD aip-datasource-GEODE-shaded.jar ${REGARDS_HOME}/plugins/dam/
ADD postgresql-datasource-GEODE-shaded.jar ${REGARDS_HOME}/plugins/dam/
ADD webservice-datasource-GEODE-shaded.jar ${REGARDS_HOME}/plugins/dam/
# Ingest
ADD enhanced-descriptive-aip-generation-GEODE-shaded.jar ${REGARDS_HOME}/plugins/ingest/
ADD ingest-test-plugins-GEODE-shaded.jar ${REGARDS_HOME}/plugins/ingest/
# Dataprovider
ADD custom-command-file-validation-GEODE-shaded.jar ${REGARDS_HOME}/plugins/dataprovider/

# Testing purpose : CMD [ "/bin/bash" ]
# first lets create the directory hierarchy if needed (mkdir -p) 
CMD mkdir -p /regards-plugins/storage/ && mkdir -p /regards-plugins/dam/ && mkdir -p /regards-plugins/ingest/ && \
	# Remove old storage plugins
	rm -f /regards-plugins/storage/local-storage-location-*.jar && \
	# Remove old dam plugins
	rm -f /regards-plugins/dam/aip-datasource-*.jar && \
	rm -f /regards-plugins/dam/postgresql-datasource-*.jar && \
	# Remove old dataprovider plugins
	rm -f /regards-plugins/dataprovider/custom-command-file-validation*.jar && \
        # Remove old ingest plugins
	rm -f /regards-plugins/ingest/enhanced-descriptive-aip-generation*.jar && \
	rm -f /regards-plugins/ingest/ingest-test-plugins*.jar && \
	# then lets copy the jars 
	cp ${REGARDS_HOME}/plugins/storage/*.jar /regards-plugins/storage/ && \
	cp ${REGARDS_HOME}/plugins/dam/*.jar /regards-plugins/dam/ && \
	cp ${REGARDS_HOME}/plugins/ingest/*.jar /regards-plugins/ingest/ && \
	cp ${REGARDS_HOME}/plugins/dataprovider/*.jar /regards-plugins/dataprovider/
