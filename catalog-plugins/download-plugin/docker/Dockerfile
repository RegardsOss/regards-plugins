FROM alpine:latest
ENV REGARDS_HOME /regards
# Docker volume for plugins
ENV REGARDS_PLUGINS /regards-plugins

# Store JAR
ADD download-plugin-*.jar ${REGARDS_HOME}/plugins/catalog/

# Testing purpose : CMD [ "/bin/bash" ]
# first lets create the directory hierarchy if needed (mkdir -p) 
CMD mkdir -p ${REGARDS_PLUGINS}/catalog/ && \
	# Remove old plugin
	rm -f ${REGARDS_PLUGINS}/catalog/download-plugin-*.jar && \
	# Copy JAR
	cp ${REGARDS_HOME}/plugins/catalog/*.jar ${REGARDS_PLUGINS}/catalog/