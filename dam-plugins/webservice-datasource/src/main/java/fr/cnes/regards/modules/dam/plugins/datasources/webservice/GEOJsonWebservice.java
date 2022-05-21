package fr.cnes.regards.modules.dam.plugins.datasources.webservice;

import fr.cnes.regards.framework.feign.annotation.RestClient;
import fr.cnes.regards.framework.geojson.FeatureWithPropertiesCollection;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Describes a generic OpenSearch API returning GEO JSON data
 *
 * @author Raphaël Mechali
 */
@RestClient(name = "oauth2", contextId = "test-oauth2")
@RequestMapping(value = "/", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
public interface GEOJsonWebservice {

    @GetMapping
    ResponseEntity<FeatureWithPropertiesCollection> get();

}
