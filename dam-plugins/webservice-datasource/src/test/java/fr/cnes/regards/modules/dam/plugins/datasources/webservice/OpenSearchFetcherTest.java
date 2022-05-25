package fr.cnes.regards.modules.dam.plugins.datasources.webservice;

import fr.cnes.regards.modules.dam.domain.datasources.CrawlingCursor;
import fr.cnes.regards.modules.dam.plugins.datasources.webservice.configuration.WebserviceConfiguration;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.TreeMap;

@RunWith(SpringJUnit4ClassRunner.class)
public class OpenSearchFetcherTest {

    @Test
    public void testBuildURL() {
        String testDescriptor = "http://server1.com:3615/descriptor.xml";
        // init page = 10, config size = 35 (overrides page), no update date by parameters, first query parameter (no query separator)
        Assert.assertEquals("http://server1.com:3615/myURL?pageNumber=75&pageSize=35",
                            new OpenSearchFetcher(new WebserviceConfiguration("http://server1.com:3615/myURL",
                                                                              testDescriptor,
                                                                              "pageNumber",
                                                                              "pageSize",
                                                                              "lastUpdate",
                                                                              10,
                                                                              35,
                                                                              null),
                                                  null,
                                                  null).getFetchURL(new CrawlingCursor(65, 55),
                                                                    null));// init page = 10, no update date by parameters, first query parameter (no query separator)

        // init page = 1, no config page size, no update date by configuration, query separator but no query
        Assert.assertEquals("https://server2.fr:3616/somewhere/here?index=1&count=20",
                            new OpenSearchFetcher(new WebserviceConfiguration("https://server2.fr:3616/somewhere/here?",
                                                                              testDescriptor,
                                                                              "index",
                                                                              "count",
                                                                              null,
                                                                              1,
                                                                              null,
                                                                              null),
                                                  null,
                                                  null).getFetchURL(new CrawlingCursor(0, 20), OffsetDateTime.now()));

        // init page = 0, config size = 50, update date OK, already some query (but not & parameter)
        Assert.assertEquals(
            "https://server2.fr:3616/somewhere/here?a=abc&b=bcd&index=0&count=50&update=2019-02-18T15:30:44.372Z",
            new OpenSearchFetcher(new WebserviceConfiguration("https://server2.fr:3616/somewhere/here?a=abc&b=bcd",
                                                              testDescriptor,
                                                              "index",
                                                              "count",
                                                              "update",
                                                              0,
                                                              50,
                                                              null), null, null).getFetchURL(new CrawlingCursor(0, 20),
                                                                                             OffsetDateTime.parse(
                                                                                                 "2019-02-18T15:30:44.372Z")));

        // init page = 1, no config page size, update date OK, already some query ending with &
        Assert.assertEquals("http://server1.fr/here?a=abc&page=9&size=40&from=2019-02-18T15:30:44.372Z",
                            new OpenSearchFetcher(new WebserviceConfiguration("http://server1.fr/here?a=abc&",
                                                                              testDescriptor,
                                                                              "page",
                                                                              "size",
                                                                              "from",
                                                                              1,
                                                                              null,
                                                                              null),
                                                  null,
                                                  null).getFetchURL(new CrawlingCursor(8, 40),
                                                                    OffsetDateTime.parse("2019-02-18T15:30:44.372Z")));

        // with some parameters (we keep them ordered for test)
        Map<String, Object> params = new TreeMap<>();
        params.put("param1", 2);
        params.put("param2", true);
        params.put("param3", "any");
        Assert.assertEquals(
            "https://server2.fr:3616/somewhere/here?a=abc&b=bcd&index=0&count=50&update=2019-02-18T15:30:44.372Z&param1=2&param2=true&param3=any",
            new OpenSearchFetcher(new WebserviceConfiguration("https://server2.fr:3616/somewhere/here?a=abc&b=bcd",
                                                              testDescriptor,
                                                              "index",
                                                              "count",
                                                              "update",
                                                              0,
                                                              50,
                                                              params), null, null).getFetchURL(new CrawlingCursor(0, 20),
                                                                                               OffsetDateTime.parse(
                                                                                                   "2019-02-18T15:30:44.372Z")));
    }

}
