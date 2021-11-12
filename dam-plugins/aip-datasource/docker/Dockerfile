FROM alpine:latest
ENV REGARDS_HOME /regards
# Docker volume for plugins
ENV REGARDS_PLUGINS /regards-plugins

# Store JAR
ADD aip-datasource-*.jar ${REGARDS_HOME}/plugins/dam/

# Testing purpose : CMD [ "/bin/bash" ]
# first lets create the directory hierarchy if needed (mkdir -p) 
CMD mkdir -p ${REGARDS_PLUGINS}/dam/ && \
	# Remove old plugin
	rm -f ${REGARDS_PLUGINS}/dam/aip-datasource-*.jar && \
	# Copy JAR
	cp ${REGARDS_HOME}/plugins/dam/*.jar ${REGARDS_PLUGINS}/dam/