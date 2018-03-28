FROM alpine:latest
ENV REGARDS_HOME /regards

# Copy JARs
ADD storage-plugin-2.0.0-SNAPSHOT.jar ${REGARDS_HOME}/plugins/storage/
ADD aip-datasource-2.0.0-SNAPSHOT.jar ${REGARDS_HOME}/plugins/dam/
ADD postgresql-datasource-2.0.0-SNAPSHOT.jar ${REGARDS_HOME}/plugins/dam/

# Testing purpose : CMD [ "/bin/bash" ]
CMD cp ${REGARDS_HOME}/plugins/storage/*.jar /regards-plugins/storage/ && cp ${REGARDS_HOME}/plugins/dam/*.jar /regards-plugins/dam/
# ENTRYPOINT ["/regards/scripts/starter.sh"]
