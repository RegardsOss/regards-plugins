package fr.cnes.regards.modules.dam.plugins.datasources.webservice;

import fr.cnes.regards.framework.feign.annotation.RestClient;
import fr.cnes.regards.framework.geojson.FeatureWithPropertiesCollection;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * Describes a generic OpenSearch API returning GEOJSON data
 *
 * @author Raphaël Mechali
 */
@RestClient(name = "oauth2", contextId = "test-oauth2")
@RequestMapping(value = "/", consumes = MediaType.APPLICATION_JSON_UTF8_VALUE,
        produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
public interface GEOJsonWebservice {

    @RequestMapping(method = RequestMethod.GET)
    ResponseEntity<FeatureWithPropertiesCollection> get();

}
