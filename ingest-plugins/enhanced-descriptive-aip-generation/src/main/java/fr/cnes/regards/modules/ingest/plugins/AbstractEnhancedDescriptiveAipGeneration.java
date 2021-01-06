package fr.cnes.regards.modules.ingest.plugins;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.framework.oais.urn.OAISIdentifier;
import fr.cnes.regards.framework.oais.urn.OaisUniformResourceName;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.modules.ingest.domain.exception.AIPGenerationException;
import fr.cnes.regards.modules.ingest.domain.plugin.IAipGeneration;
import fr.cnes.regards.modules.ingest.domain.sip.SIPEntity;
import fr.cnes.regards.modules.ingest.dto.aip.AIP;

/**
 *
 * Base for EnhancedDescriptiveAipGeneration plugins. Allows to add one descriptive information. Always or one out of two generated AIPs
 *
 * @author Sylvain VISSIERE-GUERINET
 */
public abstract class AbstractEnhancedDescriptiveAipGeneration implements IAipGeneration {

    /**
     * Integer counter used to know if AIP should receive the new information
     */
    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    @PluginParameter(label = "Name of descriptive information to add")
    protected String descriptiveInfoName;

    @PluginParameter(label = "Always",
            description = "Is the information added all the time or only one AIP out of two?")
    protected Boolean always;

    @Override
    public List<AIP> generate(SIPEntity sip, String tenant, EntityType entityType) throws AIPGenerationException {
        OaisUniformResourceName sipIdUrn = sip.getSipIdUrn();
        AIP aip = AIP.build(sip.getSip(),
                            new OaisUniformResourceName(OAISIdentifier.AIP,
                                                        entityType,
                                                        tenant,
                                                        sipIdUrn.getEntityId(),
                                                        sip.getVersion(), null, null),
                            Optional.of(sipIdUrn),
                            sip.getProviderId(),
                            sip.getVersion());
        if (COUNTER.get() == Integer.MAX_VALUE) {
            COUNTER.set(Integer.MIN_VALUE);
        }
        COUNTER.incrementAndGet();
        if (always || ((COUNTER.get() % 2) == 0)) {
            addDescriptiveInformation(aip);
        }
        List<AIP> aips = new ArrayList<>();
        aips.add(aip);
        return aips;
    }

    protected abstract void addDescriptiveInformation(AIP aip);

}
