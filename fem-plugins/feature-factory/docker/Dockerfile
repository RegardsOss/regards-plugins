FROM alpine:latest
ENV REGARDS_HOME /regards
# Docker volume for plugins
ENV REGARDS_PLUGINS /regards-plugins

# Store JAR
ADD feature-factory-plugin-*.jar ${REGARDS_HOME}/plugins/fem/
COPY datatypes ${REGARDS_HOME}/plugins/fem/datatypes

# Testing purpose : CMD [ "/bin/bash" ]
# first lets create the directory hierarchy if needed (mkdir -p) 
CMD mkdir -p ${REGARDS_PLUGINS}/fem/datatypes && \
	# Remove old plugin
	rm -f ${REGARDS_PLUGINS}/fem/feature-factory-plugin-*.jar && \
    rm -f ${REGARDS_PLUGINS}/fem/datatypes/* && \
	# Copy JAR
	cp ${REGARDS_HOME}/plugins/fem/*.jar ${REGARDS_PLUGINS}/fem/ && \
    cp ${REGARDS_HOME}/plugins/fem/datatypes/* ${REGARDS_PLUGINS}/fem/datatypes/