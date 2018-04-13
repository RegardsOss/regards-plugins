FROM alpine:latest
ENV REGARDS_HOME /regards

# Copy JARs
ADD catalog-security-delegation-2.0.0-SNAPSHOT-shaded.jar ${REGARDS_HOME}/plugins/storage/
ADD aip-datasource-2.0.0-SNAPSHOT-shaded.jar ${REGARDS_HOME}/plugins/dam/
ADD postgresql-datasource-2.0.0-SNAPSHOT-shaded.jar ${REGARDS_HOME}/plugins/dam/

# Testing purpose : CMD [ "/bin/bash" ]
# first lets create the directory hierarchy if needed (mkdir -p) and then lets copy the jars
CMD mkdir -p /regards-plugins/storage/ && mkdir -p /regards-plugins/dam/ && cp ${REGARDS_HOME}/plugins/storage/*.jar /regards-plugins/storage/ && cp ${REGARDS_HOME}/plugins/dam/*.jar /regards-plugins/dam/
# ENTRYPOINT ["/regards/scripts/starter.sh"]
