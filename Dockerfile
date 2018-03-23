FROM scratch
ENV REGARDS_HOME /regards

# Copy JARs
ADD storage-plugins/catalog-security-delegation/target/*.jar ${REGARDS_HOME}/plugins/storage/
ADD datasource-plugins/aip-datasource/target/*.jar ${REGARDS_HOME}/plugins/dam/
ADD datasource-plugins/postgresql-datasource/target/*.jar ${REGARDS_HOME}/plugins/dam/

# Testing purpose : CMD [ "/bin/bash" ]
CMD cp /regards/plugins/storage/*.jar /regards-plugins/storage/ && cp /regards/plugins/dam/*.jar /regards-plugins/dam/
# ENTRYPOINT ["/regards/scripts/starter.sh"]
