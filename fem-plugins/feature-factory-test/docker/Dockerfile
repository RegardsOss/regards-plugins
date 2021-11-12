FROM alpine:latest
ENV REGARDS_HOME /regards
# Docker volume for plugins
ENV REGARDS_PLUGINS /regards-plugins

# Store JAR
ADD feature-factory-test-plugin-*.jar ${REGARDS_HOME}/plugins/fem/

# Testing purpose : CMD [ "/bin/bash" ]
# first lets create the directory hierarchy if needed (mkdir -p) 
CMD mkdir -p ${REGARDS_PLUGINS}/fem/ && \
	# Remove old plugin
	rm -f ${REGARDS_PLUGINS}/fem/feature-factory-test-plugin-*.jar && \
	# Copy JAR
	cp ${REGARDS_HOME}/plugins/fem/*.jar ${REGARDS_PLUGINS}/fem/