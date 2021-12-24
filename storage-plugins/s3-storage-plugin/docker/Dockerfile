FROM alpine:latest
ENV REGARDS_HOME /regards
# Docker volume for plugins
ENV REGARDS_PLUGINS /regards-plugins

# Store S3 JAR
ADD s3-storage-plugin-*.jar ${REGARDS_HOME}/plugins/s3/

# Testing purpose : CMD [ "/bin/bash" ]
# first lets create the directory hierarchy if needed (mkdir -p) 
CMD mkdir -p ${REGARDS_PLUGINS}/storage/ && \
	# Remove old STAC plugin
	rm -f ${REGARDS_PLUGINS}/storage/s3-storage-plugin-*.jar && \
	# Copy JAR
	cp ${REGARDS_HOME}/plugins/s3/*.jar ${REGARDS_PLUGINS}/storage/