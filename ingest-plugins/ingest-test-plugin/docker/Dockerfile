FROM alpine:latest
ENV REGARDS_HOME /regards
# Docker volume for plugins
ENV REGARDS_PLUGINS /regards-plugins

# Store JAR
ADD ingest-test-plugin-*.jar ${REGARDS_HOME}/plugins/ingest/

# Testing purpose : CMD [ "/bin/bash" ]
# first lets create the directory hierarchy if needed (mkdir -p) 
CMD mkdir -p ${REGARDS_PLUGINS}/ingest/ && \
	# Remove old plugin
	rm -f ${REGARDS_PLUGINS}/ingest/ingest-test-plugin-*.jar && \
	# Copy JAR
	cp ${REGARDS_HOME}/plugins/ingest/*.jar ${REGARDS_PLUGINS}/ingest/