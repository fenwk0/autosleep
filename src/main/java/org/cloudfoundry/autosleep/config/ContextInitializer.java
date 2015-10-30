package org.cloudfoundry.autosleep.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.Cloud;
import org.springframework.cloud.CloudException;
import org.springframework.cloud.CloudFactory;
import org.springframework.cloud.service.ServiceInfo;
import org.springframework.cloud.service.common.RedisServiceInfo;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * Will handle automatic profile assignment.
 * If no profile is given, in-memory persistence will be used.
 * If a profile is given, will check if should point to "{PROFILENAME}-local" or "{PROFILENAME}-cloud" profile
 */
@Slf4j
public class ContextInitializer implements ApplicationContextInitializer<GenericApplicationContext> {

    /**
     * Will point to in-memory storage.
     */
    private static final String DEFAULT_PROFILE = "default";
    private static final List<String> validLocalProfiles = Collections.singletonList("redis");
    private static final Map<Class<? extends ServiceInfo>, String> autorizedPersistenceProfiles =
            new HashMap<>();

    static {
        autorizedPersistenceProfiles.put(RedisServiceInfo.class, "redis");
    }


    @Override
    public void initialize(GenericApplicationContext applicationContext) {
        log.debug("----------------------- app context initialization , set persistence profile  --------------------");
        ConfigurableEnvironment appEnvironment = applicationContext.getEnvironment();

        String[] persistenceProfiles;

        log.debug("Checking if cloud context");
        if (getCloud() != null) {
            log.debug("\t -> App in a cloud context, checking available services");
            persistenceProfiles = getCloudProfile(getCloud());
        } else {
            log.debug("\t -> App in a local context, checking if profile given in environment variable");
            persistenceProfiles = getActiveProfile(appEnvironment);
        }

        if (persistenceProfiles == null) {
            log.debug("\t -> No profile given or no available service -> setting default profile");
            persistenceProfiles = new String[]{DEFAULT_PROFILE};
        }

        for (String persistenceProfile : persistenceProfiles) {
            appEnvironment.addActiveProfile(persistenceProfile);
        }
    }

    /**
     * Check if one of the authorized profile is available in the cloud configuration.
     *
     * @param cloud Contextual cloud
     * @return the two profils to activate if available ( "profile" and "profile-cloud")
     */
    public String[] getCloudProfile(Cloud cloud) {
        if (cloud == null) {
            return null;
        }

        List<String> availableProfiles = new ArrayList<String>();
        List<ServiceInfo> availableServices = cloud.getServiceInfos();

        log.info("Found serviceInfos: " + StringUtils.collectionToCommaDelimitedString(availableServices));

        for (ServiceInfo serviceInfo : availableServices) {
            if (autorizedPersistenceProfiles.containsKey(serviceInfo.getClass())) {
                availableProfiles.add(autorizedPersistenceProfiles.get(serviceInfo.getClass()));
            }
        }

        if (availableProfiles.size() > 1) {
            throw new IllegalStateException(
                    "Only one service of the following types may be bound to this application: "
                            + autorizedPersistenceProfiles.values().toString() + ". "
                            + "These services are bound to the application: ["
                            + StringUtils.collectionToCommaDelimitedString(availableProfiles) + "]");
        }

        if (availableProfiles.size() > 0) {
            return createProfileNames(availableProfiles.get(0), "cloud");
        }

        return null;
    }

    private Cloud getCloud() {
        try {
            CloudFactory cloudFactory = new CloudFactory();
            return cloudFactory.getCloud();
        } catch (CloudException ce) {
            return null;
        }
    }

    /**
     * Check parameter given via env var, and that it is listed as authorized local profile.
     *
     * @param appEnvironment app context env
     * @return the two profils to activate if available ( "profile" and "profile-local")
     */
    private String[] getActiveProfile(ConfigurableEnvironment appEnvironment) {
        List<String> serviceProfiles = new ArrayList<String>();

        for (String profile : appEnvironment.getActiveProfiles()) {
            if (validLocalProfiles.contains(profile)) {
                serviceProfiles.add(profile);
            }
        }

        if (serviceProfiles.size() > 1) {
            throw new IllegalStateException("Only one active Spring profile may be set among the following: "
                    + validLocalProfiles.toString() + ". "
                    + "These profiles are active: ["
                    + StringUtils.collectionToCommaDelimitedString(serviceProfiles) + "]");
        }

        if (serviceProfiles.size() > 0) {
            return createProfileNames(serviceProfiles.get(0), "local");
        }

        return null;
    }

    private String[] createProfileNames(String baseName, String suffix) {
        String[] profileNames = {baseName, baseName + "-" + suffix};
        log.info("Setting profile names: " + StringUtils.arrayToCommaDelimitedString(profileNames));
        return profileNames;
    }
}
