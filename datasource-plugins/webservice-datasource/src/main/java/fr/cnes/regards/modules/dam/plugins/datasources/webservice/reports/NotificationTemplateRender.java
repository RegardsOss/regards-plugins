package fr.cnes.regards.modules.dam.plugins.datasources.webservice.reports;

import fr.cnes.regards.framework.utils.RsRuntimeException;
import fr.cnes.regards.modules.dam.plugins.datasources.webservice.WebserviceDatasourcePlugin;
import fr.cnes.regards.modules.dam.plugins.datasources.webservice.reports.FeatureErrors;
import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders notification template using freemarker (static helper)
 *
 * @author Raphaël Mechali
 */
public class NotificationTemplateRender {

    /**
     * Class logger
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(WebserviceDatasourcePlugin.class);
    /**
     * Template file name
     */
    private static String templateFileName = "notification-template.ftl";
    /**
     * Templates folder path in JAR
     */
    private static String templatesFolder = "templates";
    /**
     * Template to use for render
     */
    private final Template template;

    /**
     * Prevents external instantiation
     */
    public NotificationTemplateRender() {
        template = getTemplate();
    }

    /**
     * Builds configuration and returns template file
     *
     * @return template file
     */
    private static Template getTemplate() {
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_25);
        configuration.setTemplateLoader(new StringTemplateLoader());
        configuration.setDefaultEncoding("UTF-8");
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setLogTemplateExceptions(false);

        // Resolve template from class path
        try {
            configuration.setDirectoryForTemplateLoading(new File(new ClassPathResource(templatesFolder).getURI()));
            return configuration.getTemplate(templateFileName);
        } catch (IOException e) {
            throw new RsRuntimeException("Failed retrieving or parsing notification template", e);
        }
    }

    /**
     * Renders notification template
     *
     *
     * @param pageURL
     * @param blockingErrors    blocking errors
     * @param nonBlockingErrors non blocking errors
     * @return rendered template
     */
    public String renderNotificationTemplate(String pageURL, Collection<FeatureErrors> blockingErrors, Collection<FeatureErrors> nonBlockingErrors) {
        // create template values
        Map<String, Object> root = new HashMap<>();
        root.put("pageURL", pageURL);
        root.put("blockingErrors", blockingErrors);
        root.put("nonBlockingErrors", nonBlockingErrors);
        // Attempt rendering the template
        StringWriter resultsWriter = new StringWriter();
        try {
            template.process(root, resultsWriter);
            return resultsWriter.toString();
        } catch (TemplateException | IOException e) {
            LOGGER.error("Error during notification template render", e);
        }
        return null;
    }

}
