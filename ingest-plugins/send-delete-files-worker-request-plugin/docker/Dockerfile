FROM alpine:latest
ENV REGARDS_HOME /regards
# Docker volume for plugins
ENV REGARDS_PLUGINS /regards-plugins
ENV REGARDS_MS ingest
ENV PLUGIN_ID send-delete-files-worker-request-plugin

# Store JAR
ADD ${PLUGIN_ID}-*-shaded.jar ${REGARDS_HOME}/plugins/

# Testing purpose : CMD [ "/bin/bash" ]
# first lets create the directory hierarchy if needed (mkdir -p)
CMD mkdir -p ${REGARDS_PLUGINS}/${REGARDS_MS}/ && \
	# Remove old plugin
	rm -f ${REGARDS_PLUGINS}/${REGARDS_MS}/${PLUGIN_ID}-*.jar && \
	# Copy JAR
	cp ${REGARDS_HOME}/plugins/${PLUGIN_ID}-*.jar ${REGARDS_PLUGINS}/${REGARDS_MS}/
