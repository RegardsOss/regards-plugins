package fr.cnes.regards.modules.dam.plugins.datasources.webservice.reports;

import fr.cnes.regards.modules.dam.domain.entities.StaticProperties;
import fr.cnes.regards.modules.dam.domain.models.attributes.AttributeModel;
import fr.cnes.regards.modules.dam.domain.models.attributes.AttributeType;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Notification template render tests
 *
 * @author Raphaël Mechali
 */
@RunWith(SpringJUnit4ClassRunner.class)
public class NotificationTemplateRenderTest {

    private static FeatureErrors buildFeatureErrors(int index, String label, String providerId, FeatureConversionError... errors) {
        FeatureErrors feat = new FeatureErrors(index, label, providerId);
        for (FeatureConversionError err : errors) {
            feat.addError(err);
        }
        return feat;
    }

    private static void assertFeatureDisplayed(String builtNotification, FeatureErrors feat, int featIndex) {
        Assert.assertTrue("feat[" + featIndex + "] index must be displayed", builtNotification.contains(String.valueOf(feat.getIndex())));
        if (feat.getLabel() != null) {
            Assert.assertTrue("feat[" + featIndex + "]label must be displayed", builtNotification.contains(feat.getLabel()));
        }
        if (feat.getProviderId() != null) {
            Assert.assertTrue("feat[" + featIndex + "] providerId must be displayed", builtNotification.contains(feat.getProviderId()));
        }
    }

    @Test
    public void testInitializationAndClassPath() {
        new NotificationTemplateRender(); // class loader should fire an exception on failure
    }

    @Test
    public void testURLRenderNoIssue() {
        NotificationTemplateRender notificationTemplateRender = new NotificationTemplateRender();
        String notification = notificationTemplateRender.renderNotificationTemplate("http://i.dont.exit.com/features",
                new ArrayList<>(), new ArrayList<>());
        Assert.assertTrue("URL must be displayed", notification.contains("http://i.dont.exit.com/features"));
    }


    @Test
    public void testOnlyBlockingIssues() {
        /* Create 3 features with blocking (2 firsts with only one error, second one with 4 errors) and check:
            - That blocking issues section is shown with the 3 features visible
            - That non blocking issues section is hidden
         */
        List<FeatureErrors> blockingErrors = Arrays.asList(
                buildFeatureErrors(5, "Feat5", "ProviderFeat1",
                        FeatureConversionError.getMandatoryAttributeNotFoundError("my.attr1", "source.myAttr1")),
                buildFeatureErrors(8, "Feat8", null,
                        FeatureConversionError.getMandatoryAttributeNotFoundError(StaticProperties.FEATURE_PROVIDER_ID, "source.my.providerId")),
                buildFeatureErrors(11, null, null,
                        FeatureConversionError.getMandatoryAttributeNotFoundError(StaticProperties.FEATURE_LABEL, "source.attr.label"),
                        FeatureConversionError.getMandatoryAttributeNotFoundError(StaticProperties.FEATURE_PROVIDER_ID, "source.my.providerId"),
                        FeatureConversionError.getInvalidValueOnPathError("my.attr1", true,  "source.frag.myAttr1", "frag", "46"),
                        FeatureConversionError.getValueNotConvertibleError("my.attr2", AttributeType.INTEGER_ARRAY, true,  "source.frag.myAttr1", false)));

        String notification = new NotificationTemplateRender().renderNotificationTemplate("http://i.dont.exit.com/features",
                blockingErrors, new ArrayList<>());

        Assert.assertTrue("URL must be displayed", notification.contains("http://i.dont.exit.com/features"));
        Assert.assertTrue("Blocking errors must be displayed", notification.contains("Major conversion issues"));
        for (int i = 0; i < blockingErrors.size(); i++) {
            assertFeatureDisplayed(notification, blockingErrors.get(i), i);
        }
        Assert.assertFalse("Non blocking errors should be hidden", notification.contains("Minor conversion issues"));
    }

    @Test
    public void testOnlyNonBlockingIssues() {
        /* Create 2 features with non blocking  issues (first with 1 error, second with 2 errors) and check:
            - That blocking issues section is shown with the 3 features visible
            - That non blocking issues section is hidden
         */
        List<FeatureErrors> nonBlockingErrors = Arrays.asList(
                buildFeatureErrors(18, "Feat18", "ProviderFeat18",
                        FeatureConversionError.getValueNotConvertibleError("f1.myAttr1", AttributeType.INTEGER_ARRAY, false, "my.attr1.path", 25)),
                buildFeatureErrors(55, "Feat55", "ProviderFeat55",
                        FeatureConversionError.getValueNotConvertibleError("f1.myAttr1", AttributeType.INTEGER_ARRAY, false, "my.attr1.path", false),
                        FeatureConversionError.getInvalidValueOnPathError("fX.myAttr2", false, "my.attr2.path", "attr2", "abcd")));

        String notification = new NotificationTemplateRender().renderNotificationTemplate("http://i.dont.exit.com/features",
                new ArrayList<>(), nonBlockingErrors);


        Assert.assertTrue("URL must be displayed", notification.contains("http://i.dont.exit.com/features"));
        Assert.assertFalse("Blocking errors must be hidden", notification.contains("Major conversion issues"));
        Assert.assertTrue("Non blocking errors should be displayed", notification.contains("Minor conversion issues"));
        for (int i = 0; i < nonBlockingErrors.size(); i++) {
            assertFeatureDisplayed(notification, nonBlockingErrors.get(i), i);
        }
    }

    @Test
    public void testAllIssuesAreDisplayed() {
        /* Create features from tests above and check all sections and issues are displayed (note that index are different to keep it simple to test) */
        List<FeatureErrors> blockingErrors = Arrays.asList(
                buildFeatureErrors(5, "Feat5", "ProviderFeat1",
                        FeatureConversionError.getMandatoryAttributeNotFoundError("my.attr1", "source.myAttr1")),
                buildFeatureErrors(8, "Feat8", null,
                        FeatureConversionError.getMandatoryAttributeNotFoundError(StaticProperties.FEATURE_PROVIDER_ID, "source.my.providerId")),
                buildFeatureErrors(11, null, null,
                        FeatureConversionError.getMandatoryAttributeNotFoundError(StaticProperties.FEATURE_LABEL, "source.attr.label"),
                        FeatureConversionError.getMandatoryAttributeNotFoundError(StaticProperties.FEATURE_PROVIDER_ID, "source.my.providerId"),
                        FeatureConversionError.getInvalidValueOnPathError("my.attr1", true,  "source.frag.myAttr1", "frag", "46"),
                        FeatureConversionError.getValueNotConvertibleError("my.attr2", AttributeType.INTEGER_ARRAY, true,  "source.frag.myAttr1", false)));
        List<FeatureErrors> nonBlockingErrors = Arrays.asList(
                buildFeatureErrors(18, "Feat18", "ProviderFeat18",
                        FeatureConversionError.getValueNotConvertibleError("f1.myAttr1", AttributeType.INTEGER_ARRAY, false, "my.attr1.path", 25)),
                buildFeatureErrors(55, "Feat55", "ProviderFeat55",
                        FeatureConversionError.getValueNotConvertibleError("f1.myAttr1", AttributeType.INTEGER_ARRAY, false, "my.attr1.path", false),
                        FeatureConversionError.getInvalidValueOnPathError("fX.myAttr2", false, "my.attr2.path", "attr2", "abcd")));

        String notification = new NotificationTemplateRender().renderNotificationTemplate("http://i.dont.exit.com/features",
                blockingErrors, nonBlockingErrors);

        Assert.assertTrue("URL must be displayed", notification.contains("http://i.dont.exit.com/features"));
        Assert.assertTrue("Blocking errors must be displayed", notification.contains("Major conversion issues"));
        Assert.assertTrue("Non blocking errors should be displayed", notification.contains("Minor conversion issues"));
    }

}

