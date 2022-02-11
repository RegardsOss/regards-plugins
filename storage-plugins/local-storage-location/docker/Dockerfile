FROM alpine:latest
ENV REGARDS_HOME /regards
# Docker volume for plugins
ENV REGARDS_PLUGINS /regards-plugins

# Store JAR
ADD local-storage-location-*.jar ${REGARDS_HOME}/plugins/storage/

# Testing purpose : CMD [ "/bin/bash" ]
# first lets create the directory hierarchy if needed (mkdir -p) 
CMD mkdir -p ${REGARDS_PLUGINS}/storage/ && \
	# Remove old plugin
	rm -f ${REGARDS_PLUGINS}/storage/local-storage-location-*.jar && \
	# Copy JAR
	cp ${REGARDS_HOME}/plugins/storage/*.jar ${REGARDS_PLUGINS}/storage/