FROM alpine:latest
ENV REGARDS_HOME /regards
# Docker volume for plugins
ENV REGARDS_PLUGINS /regards-plugins

# Store JAR
ADD custom-command-file-validation-*.jar ${REGARDS_HOME}/plugins/dataprovider/

# Testing purpose : CMD [ "/bin/bash" ]
# first lets create the directory hierarchy if needed (mkdir -p) 
CMD mkdir -p ${REGARDS_PLUGINS}/dataprovider/ && \
	# Remove old plugin
	rm -f ${REGARDS_PLUGINS}/dataprovider/custom-command-file-validation-*.jar && \
	# Copy JAR
	cp ${REGARDS_HOME}/plugins/dataprovider/*.jar ${REGARDS_PLUGINS}/dataprovider/