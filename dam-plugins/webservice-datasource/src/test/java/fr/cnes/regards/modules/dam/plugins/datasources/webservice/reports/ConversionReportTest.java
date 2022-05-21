package fr.cnes.regards.modules.dam.plugins.datasources.webservice.reports;

import fr.cnes.regards.framework.jpa.utils.RegardsTransactional;
import fr.cnes.regards.framework.test.integration.AbstractRegardsServiceTransactionalIT;
import fr.cnes.regards.framework.urn.DataType;
import fr.cnes.regards.modules.dam.domain.entities.StaticProperties;
import fr.cnes.regards.modules.model.dto.properties.PropertyType;
import fr.cnes.regards.modules.templates.service.TemplateService;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.SortedMap;

/**
 * Conversion report tests, with template rendering
 *
 * @author Raphaël Mechali
 */
@TestPropertySource(locations = { "classpath:test.properties" },
    properties = { "spring.jpa.properties.hibernate.default_schema=public" })
@RegardsTransactional
public class ConversionReportTest extends AbstractRegardsServiceTransactionalIT {

    @Autowired
    private TemplateService templateService;

    private static void assertFeatureAndErrorsDisplayed(String builtNotification, FeatureErrors feat) {
        Assert.assertTrue("feat#" + feat.getIndex() + " index must be displayed",
                          builtNotification.contains(String.valueOf(feat.getIndex())));
        if (feat.getLabel() != null) {
            Assert.assertTrue("feat#" + feat.getIndex() + "label must be displayed",
                              builtNotification.contains(feat.getLabel()));
        }
        if (feat.getProviderId() != null) {
            Assert.assertTrue("feat#" + feat.getIndex() + " providerId must be displayed",
                              builtNotification.contains(feat.getProviderId()));
        }
        for (FeatureConversionError error : feat.getErrors()) {
            // escape error non HTML chars (StringEscapeUtils.escapeHtml not working there - not sure of the work performed by freemarker so just replace the quote char)
            String msgForHTML = error.getMessage().replaceAll("'", "&#39;");
            Assert.assertTrue("feat# " + feat.getIndex() + " error '" + msgForHTML
                                  + "' must be displayed. It was not found in report:\n" + builtNotification,
                              builtNotification.contains(msgForHTML));
        }
    }

    @Test
    public void testURLRenderNoIssue() {
        ConversionReport report = new ConversionReport();
        String notification = report.buildNotificationReport("http://i.dont.exit.com/features", templateService);
        Assert.assertTrue("URL must be displayed", notification.contains("http://i.dont.exit.com/features"));
    }

    @Test
    public void testOnlyBlockingIssues() {
        /* Create 3 features with blocking (2 firsts with only one error, second one with 4 errors) and check:
            - That blocking issues section is shown with the 3 features visible
            - That non blocking issues section is hidden
         */
        ConversionReport report = new ConversionReport();
        report.addFeatureConversionError(5,
                                         "Feat5",
                                         "ProviderFeat1",
                                         FeatureConversionError.getMandatoryAttributeNotFoundError("my.attr1",
                                                                                                   "source.myAttr1"));
        report.addFeatureConversionError(8,
                                         "Feat8",
                                         null,
                                         FeatureConversionError.getMandatoryAttributeNotFoundError(StaticProperties.FEATURE_PROVIDER_ID,
                                                                                                   "source.my.providerId"));
        report.addFeatureConversionError(11,
                                         null,
                                         null,
                                         FeatureConversionError.getMandatoryAttributeNotFoundError(StaticProperties.FEATURE_LABEL,
                                                                                                   "source.attr.label"));
        report.addFeatureConversionError(11,
                                         null,
                                         null,
                                         FeatureConversionError.getMandatoryAttributeNotFoundError(StaticProperties.FEATURE_PROVIDER_ID,
                                                                                                   "source.my.providerId"));
        report.addFeatureConversionError(11,
                                         null,
                                         null,
                                         FeatureConversionError.getInvalidValueOnPathError("my.attr1",
                                                                                           true,
                                                                                           "source.frag.myAttr1",
                                                                                           "frag",
                                                                                           "46"));
        report.addFeatureConversionError(11,
                                         null,
                                         null,
                                         FeatureConversionError.getValueNotConvertibleError("my.attr2",
                                                                                            PropertyType.INTEGER_ARRAY,
                                                                                            true,
                                                                                            "source.frag.myAttr1",
                                                                                            false));

        String notification = report.buildNotificationReport("http://i.dont.exit.com/features", templateService);

        Assert.assertTrue("URL must be displayed", notification.contains("http://i.dont.exit.com/features"));

        SortedMap<Integer, FeatureErrors> blockingErrors = report.getBlockingErrors();
        Assert.assertEquals("There should be 3 features with errors", blockingErrors.size(), 3);
        Assert.assertTrue("Blocking errors must be displayed", notification.contains("Major conversion issues"));
        for (FeatureErrors feat : blockingErrors.values()) {
            switch (feat.getIndex()) {
                case 5:
                    Assert.assertEquals("There should be 1 error for feat 5", 1, feat.getErrors().size());
                    break;
                case 8:
                    Assert.assertEquals("There should be 1 error for feat 8", 1, feat.getErrors().size());
                    break;
                case 11:
                    Assert.assertEquals("There should be 4 error for feat 11", 4, feat.getErrors().size());
                    break;
                default:
                    Assert.fail("Unexpected feature index" + feat.getIndex());
            }
            // check each feature error is displayed
            assertFeatureAndErrorsDisplayed(notification, feat);
        }

        Assert.assertTrue("There should be no features with non blocking errors",
                          report.getNonBlockingErrors().isEmpty());
        Assert.assertFalse("Non blocking errors should be hidden", notification.contains("Minor conversion issues"));
    }

    @Test
    public void testOnlyNonBlockingIssues() {
        /* Create 2 features with non blocking  issues (first with 1 error, second with 2 errors) and check:
            - That blocking issues section is shown with the 3 features visible
            - That non blocking issues section is hidden
         */
        ConversionReport report = new ConversionReport();
        report.addFeatureConversionError(18,
                                         "Feat18",
                                         "ProviderFeat18",
                                         FeatureConversionError.getValueNotConvertibleError("f1.myAttr1",
                                                                                            PropertyType.INTEGER_ARRAY,
                                                                                            false,
                                                                                            "my.attr1.path",
                                                                                            25));
        report.addFeatureConversionError(55,
                                         "Feat55",
                                         "ProviderFeat55",
                                         FeatureConversionError.getValueNotConvertibleError("f1.myAttr1",
                                                                                            PropertyType.INTEGER_ARRAY,
                                                                                            false,
                                                                                            "my.attr1.path",
                                                                                            false));
        report.addFeatureConversionError(55,
                                         "Feat55",
                                         "ProviderFeat55",
                                         FeatureConversionError.getInvalidValueOnPathError("fX.myAttr2",
                                                                                           false,
                                                                                           "my.attr2.path",
                                                                                           "attr2",
                                                                                           "abcd"));

        String notification = report.buildNotificationReport("http://i.dont.exit.com/features", templateService);

        Assert.assertTrue("URL must be displayed", notification.contains("http://i.dont.exit.com/features"));

        Assert.assertTrue("There should be no features with blocking errors", report.getBlockingErrors().isEmpty());
        Assert.assertFalse("Blocking errors should be hidden", notification.contains("Major conversion issues"));

        SortedMap<Integer, FeatureErrors> nonBlockingErrors = report.getNonBlockingErrors();
        Assert.assertTrue("Non blocking errors should be displayed", notification.contains("Minor conversion issues"));
        Assert.assertEquals("There should be 2 features with non blocking errors", 2, nonBlockingErrors.size());
        for (FeatureErrors feat : nonBlockingErrors.values()) {
            switch (feat.getIndex()) {
                case 18:
                    Assert.assertEquals("There should be 1 error for feat 18", 1, feat.getErrors().size());
                    break;
                case 55:
                    Assert.assertEquals("There should be 2 error for feat 55", 2, feat.getErrors().size());
                    break;
                default:
                    Assert.fail("Unexpected feature index" + feat.getIndex());
            }
            // check each feature error is displayed
            assertFeatureAndErrorsDisplayed(notification, feat);
        }

    }

    @Test
    public void testAllIssuesAreDisplayed() {
        /* Create features from tests above and check all sections and issues are displayed (note that index are different to keep it simple to test) */
        ConversionReport report = new ConversionReport();
        report.addFeatureConversionError(5,
                                         "Feat5",
                                         "ProviderFeat1",
                                         FeatureConversionError.getMandatoryAttributeNotFoundError("my.attr1",
                                                                                                   "source.myAttr1"));
        report.addFeatureConversionError(8,
                                         "Feat8",
                                         null,
                                         FeatureConversionError.getMandatoryAttributeNotFoundError(StaticProperties.FEATURE_PROVIDER_ID,
                                                                                                   "source.my.providerId"));
        report.addFeatureConversionError(11,
                                         null,
                                         null,
                                         FeatureConversionError.getMandatoryAttributeNotFoundError(StaticProperties.FEATURE_LABEL,
                                                                                                   "source.attr.label"));
        report.addFeatureConversionError(11,
                                         null,
                                         null,
                                         FeatureConversionError.getMandatoryAttributeNotFoundError(StaticProperties.FEATURE_PROVIDER_ID,
                                                                                                   "source.my.providerId"));
        report.addFeatureConversionError(11,
                                         null,
                                         null,
                                         FeatureConversionError.getInvalidValueOnPathError("my.attr1",
                                                                                           true,
                                                                                           "source.frag.myAttr1",
                                                                                           "frag",
                                                                                           "46"));
        report.addFeatureConversionError(11,
                                         null,
                                         null,
                                         FeatureConversionError.getValueNotConvertibleError("my.attr2",
                                                                                            PropertyType.INTEGER_ARRAY,
                                                                                            true,
                                                                                            "source.frag.myAttr1",
                                                                                            false));
        report.addFeatureConversionError(18,
                                         "Feat18",
                                         "ProviderFeat18",
                                         FeatureConversionError.getValueNotConvertibleError("f1.myAttr1",
                                                                                            PropertyType.INTEGER_ARRAY,
                                                                                            false,
                                                                                            "my.attr1.path",
                                                                                            25));
        report.addFeatureConversionError(55,
                                         "Feat55",
                                         "ProviderFeat55",
                                         FeatureConversionError.getValueNotConvertibleError("f1.myAttr1",
                                                                                            PropertyType.INTEGER_ARRAY,
                                                                                            false,
                                                                                            "my.attr1.path",
                                                                                            false));
        report.addFeatureConversionError(55,
                                         "Feat55",
                                         "ProviderFeat55",
                                         FeatureConversionError.getInvalidValueOnPathError("fX.myAttr2",
                                                                                           false,
                                                                                           "my.attr2.path",
                                                                                           "attr2",
                                                                                           "abcd"));

        String notification = report.buildNotificationReport("http://i.dont.exit.com/features", templateService);

        Assert.assertTrue("URL must be displayed", notification.contains("http://i.dont.exit.com/features"));
        Assert.assertTrue("Blocking errors must be displayed", notification.contains("Major conversion issues"));
        Assert.assertTrue("Non blocking errors should be displayed", notification.contains("Minor conversion issues"));
    }

    @Test
    public void testTenOnlyFeatureErrorsByTypeDisplayed() {
        ConversionReport report = new ConversionReport();
        for (int i = 0; i < 100; i++) {
            // Blocking error
            report.addFeatureConversionError(i,
                                             "Feat" + (i + 1),
                                             "ProviderFeat" + (i + 1),
                                             FeatureConversionError.getMandatoryAttributeNotFoundError("my.attr1",
                                                                                                       "source.myAttr1"));
            // Non blocking error
            report.addFeatureConversionError(i,
                                             "Feat" + (i + 1),
                                             "ProviderFeat" + (i + 1),
                                             FeatureConversionError.getInvalidImageMimeTypeFound(
                                                 "http://www.myImageStock.com/image.tiff",
                                                 DataType.QUICKLOOK_MD,
                                                 "source.test.quicklook",
                                                 "image/tiff"));
        }
        String notification = report.buildNotificationReport("http://i.dont.exit.com/features", templateService);
        // check some blocking and some minor are shown
        Assert.assertTrue("Some blocking errors should be shown", notification.contains("my.attr1"));
        Assert.assertTrue("Some minor errors should be shown", notification.contains("QUICKLOOK_MD"));
        // check only the first five features are shown
        for (int i = 0; i < 100; i++) {
            if (i < 5) {
                Assert.assertTrue("Feature at " + i + "should be shown", notification.contains("Feat" + (i + 1)));
            } else {
                Assert.assertFalse("Feature at " + i + "should be hidden", notification.contains("Feat" + (i + 1)));
            }
        }
        // Check hidden count is displayed
        Assert.assertTrue("Hidden errors count should be shown", notification.contains("95"));
    }

}
