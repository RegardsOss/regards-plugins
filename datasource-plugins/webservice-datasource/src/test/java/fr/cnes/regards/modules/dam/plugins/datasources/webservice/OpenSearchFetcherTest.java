package fr.cnes.regards.modules.dam.plugins.datasources.webservice;

import fr.cnes.regards.modules.dam.plugins.datasources.webservice.configuration.WebserviceConfiguration;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.time.OffsetDateTime;

@RunWith(SpringJUnit4ClassRunner.class)
public class OpenSearchFetcherTest {

    @Test
    public void testBuildURL() {
        // init page = 10, config size = 35 (overrides page), no update date by parameters, first query parameter (no query separator)
        Assert.assertEquals("http://server1.com:3615/myURL?pageNumber=75&pageSize=35", new OpenSearchFetcher(
                new WebserviceConfiguration("http://server1.com:3615/myURL", "pageNumber", "pageSize", "lastUpdate", 10, 35),
                null, null).getFetchURL(PageRequest.of(65, 55), null));// init page = 10, no update date by parameters, first query parameter (no query separator)

        // init page = 1, no config page size, no update date by configuration, query separator but no query
        Assert.assertEquals("https://server2.fr:3616/somewhere/here?index=1&count=20", new OpenSearchFetcher(
                new WebserviceConfiguration("https://server2.fr:3616/somewhere/here?", "index", "count", null, 1, null),
                null, null).getFetchURL(PageRequest.of(0, 20), OffsetDateTime.now()));

        // init page = 0, config size = 50, update date OK, already some query (but not & parameter)
        Assert.assertEquals("https://server2.fr:3616/somewhere/here?a=abc&b=bcd&index=0&count=50&update=2019-02-18T15:30:44.372Z", new OpenSearchFetcher(
                new WebserviceConfiguration("https://server2.fr:3616/somewhere/here?a=abc&b=bcd", "index", "count", "update", 0, 50),
                null, null).getFetchURL(PageRequest.of(0, 20), OffsetDateTime.parse("2019-02-18T15:30:44.372Z")));

        // init page = 1, no config page size, update date OK, already some query ending with &
        Assert.assertEquals("http://server1.fr/here?a=abc&page=9&size=40&from=2019-02-18T15:30:44.372Z", new OpenSearchFetcher(
                new WebserviceConfiguration("http://server1.fr/here?a=abc&", "page", "size", "from", 1, null),
                null, null).getFetchURL(PageRequest.of(8, 40), OffsetDateTime.parse("2019-02-18T15:30:44.372Z")));
    }

}
