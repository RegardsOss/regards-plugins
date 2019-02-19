<#ftl output_format="HTML">
<#setting datetime_format='yyyy-MM-dd HH:mm:ss z'>
<p>
The Opensearch webservice datasource plugin encountered errors while converting features from page <a href=${pageURL}>${pageURL}</a>.
</p>
<#if (blockingErrors?size > 0)>
    <p>Major conversion issues below <b>prevented corresponding features conversion</b>
        <ul>
            <@renderFeatureIssues features=blockingErrors />
        </ul>
    </p>
</#if>
<#if (nonBlockingErrors?size > 0)>
    <p>Minor conversion issues below were <b>ignored during conversion</b>
        <ul>
            <@renderFeatureIssues features=nonBlockingErrors />
        </ul>
    </p>
</#if>

<#-- Render a single feature errors: --> 
<#macro renderFeatureIssues features>
    <#list features as featureErrors>
        <#-- Render single error line -->
        <#if (featureErrors.errors?size == 1)>
            <li>In feature <@renderFeatureLabel featureErrors=featureErrors />, ${featureErrors.errors[0].message}</li>
        </#if>
        <#if (featureErrors.errors?size > 1)>
        <#-- Render line with sub list of errors  -->
            <li>In feature <@renderFeatureLabel featureErrors=featureErrors />:
                <ul>
                    <#list featureErrors.errors as error>
                        <li>${error.message}</li>
                    </#list>
                </ul>
            </li>
        </#if>
        
    </#list>
</#macro>

<#-- Renders feature label with all available information chinks -->
<#macro renderFeatureLabel featureErrors>
    <b>#${featureErrors.index}</b>&nbsp;
    <#if (featureErrors.label?? && featureErrors.providerId??)>
        (label: <b>${featureErrors.label}</b>, providerId: <b>${featureErrors.providerId}</b>)
        <#return/>
    </#if>
    <#if featureErrors.label??>
        (label: <b>${featureErrors.label}</b>)
    </#if>
    <#-- Nota: we know here that providerId cannot be set as label is not -->
</#macro>
