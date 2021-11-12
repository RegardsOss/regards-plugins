FROM alpine:latest
ENV REGARDS_HOME /regards
# Docker volume for plugins
ENV REGARDS_PLUGINS /regards-plugins

# Store JAR
ADD chronos-recipient-sender-*.jar ${REGARDS_HOME}/plugins/notifier/

# Testing purpose : CMD [ "/bin/bash" ]
# first lets create the directory hierarchy if needed (mkdir -p) 
CMD mkdir -p ${REGARDS_PLUGINS}/notifier/ && \
	# Remove old plugin
	rm -f ${REGARDS_PLUGINS}/notifier/chronos-recipient-sender-*.jar && \
	# Copy JAR
	cp ${REGARDS_HOME}/plugins/notifier/*.jar ${REGARDS_PLUGINS}/notifier/