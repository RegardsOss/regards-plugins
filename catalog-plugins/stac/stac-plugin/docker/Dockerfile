FROM alpine:latest
ENV REGARDS_HOME /regards
# Docker volume for plugins
ENV REGARDS_PLUGINS /regards-plugins

# Store STAC JAR
ADD stac-plugin-*.jar ${REGARDS_HOME}/plugins/stac/

# Testing purpose : CMD [ "/bin/bash" ]
# first lets create the directory hierarchy if needed (mkdir -p) 
CMD mkdir -p ${REGARDS_PLUGINS}/catalog/ && \
	# Remove old STAC plugin
	rm -f ${REGARDS_PLUGINS}/catalog/stac-plugin-*.jar && \
	# Copy JAR
	cp ${REGARDS_HOME}/plugins/stac/*.jar ${REGARDS_PLUGINS}/catalog/