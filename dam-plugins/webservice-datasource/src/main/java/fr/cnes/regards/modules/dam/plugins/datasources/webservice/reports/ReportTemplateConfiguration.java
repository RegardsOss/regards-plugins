package fr.cnes.regards.modules.dam.plugins.datasources.webservice.reports;

import fr.cnes.regards.modules.templates.domain.Template;
import fr.cnes.regards.modules.templates.service.TemplateConfigUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class ReportTemplateConfiguration {

    /**
     * Database key for webservice conversion report template key
     */
    public static final String CONVERSION_REPORT_KEY = "WEBSERVICE_DATASOURCE_CONVERSION_REPORT_TEMPLATE";

    @Bean
    public Template conversionReportTemplate() throws IOException {
        return TemplateConfigUtil.readTemplate(CONVERSION_REPORT_KEY, "templates/notification-template.ftl");
    }


}
