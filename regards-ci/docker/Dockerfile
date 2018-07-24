FROM alpine:latest
ENV REGARDS_HOME /regards

# Copy JARs
ADD catalog-security-delegation-3.0.0-SNAPSHOT-shaded.jar ${REGARDS_HOME}/plugins/storage/
ADD aip-datasource-3.0.0-SNAPSHOT-shaded.jar ${REGARDS_HOME}/plugins/dam/
ADD postgresql-datasource-3.0.0-SNAPSHOT-shaded.jar ${REGARDS_HOME}/plugins/dam/

# Testing purpose : CMD [ "/bin/bash" ]
# first lets create the directory hierarchy if needed (mkdir -p) 
CMD mkdir -p /regards-plugins/storage/ && mkdir -p /regards-plugins/dam/ && \
	# Remove old storage plugins
	rm -f /regards-plugins/storage/catalog-security-delegation-2*.jar && \
	# Remove old dam plugins
	rm -f /regards-plugins/dam/aip-datasource-2*.jar && \
	rm -f /regards-plugins/dam/postgresql-datasource-2*.jar && \
	# then lets copy the jars 
	cp ${REGARDS_HOME}/plugins/storage/*.jar /regards-plugins/storage/ && \
	cp ${REGARDS_HOME}/plugins/dam/*.jar /regards-plugins/dam/
