package repro;

import com.azure.data.appconfiguration.ConfigurationClientBuilder;
import com.azure.identity.ClientSecretCredential;
import com.azure.spring.cloud.appconfiguration.config.implementation.AppConfigurationReplicaClientFactory;
import com.azure.spring.cloud.core.customizer.AzureServiceClientBuilderCustomizer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.bootstrap.BootstrapRegistry;
import org.springframework.context.ConfigurableApplicationContext;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AzureAppConfigurationBootstrapContextTest {

    private static final String CLIENT_ID = "repro-client-id";
    private static final String CLIENT_SECRET = "repro-client-secret";
    private static final String TENANT_ID = "repro-tenant-id";

    @Test
    void springBootShouldUseLocalClientSecretCredentialToLoadAppConfiguration() {
        // given
        AtomicReference<Class<?>> credentialTypeAtBuilderConfiguration = new AtomicReference<>();
        AtomicReference<Throwable> bootstrapFailure = new AtomicReference<>();

        SpringApplication application = new SpringApplication(ReproducerApplication.class);
        application.setDefaultProperties(Map.of(
            "spring.main.web-application-type", "none",
            "spring.main.banner-mode", "off"
        ));
        application.addBootstrapRegistryInitializer(registry -> registerCredentialProbe(
            registry, credentialTypeAtBuilderConfiguration, bootstrapFailure));

        // when
        ConfigurableApplicationContext context = application.run();
        try {
            // then
            assertThat(context.getEnvironment()
                .getProperty("spring.cloud.azure.appconfiguration.credential.client-id"))
                .isEqualTo(CLIENT_ID);
            assertThat(context.getEnvironment()
                .getProperty("spring.cloud.azure.appconfiguration.credential.client-secret"))
                .isEqualTo(CLIENT_SECRET);
            assertThat(context.getEnvironment()
                .getProperty("spring.cloud.azure.appconfiguration.profile.tenant-id"))
                .isEqualTo(TENANT_ID);
            assertThat(bootstrapFailure.get()).isNull();
            assertThat(credentialTypeAtBuilderConfiguration.get())
                .as("TokenCredential configured by the SDK before the Azure service client customizer runs")
                .isEqualTo(ClientSecretCredential.class);
        } finally {
            context.close();
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void registerCredentialProbe(BootstrapRegistry registry,
        AtomicReference<Class<?>> credentialTypeAtBuilderConfiguration,
        AtomicReference<Throwable> bootstrapFailure) {
        Class<AzureServiceClientBuilderCustomizer<ConfigurationClientBuilder>> customizerClass =
            (Class) AzureServiceClientBuilderCustomizer.class;
        registry.registerIfAbsent(customizerClass, BootstrapRegistry.InstanceSupplier.of(
            (AzureServiceClientBuilderCustomizer<ConfigurationClientBuilder>) builder ->
                credentialTypeAtBuilderConfiguration.set(configuredCredentialType(builder))));

        // The store is disabled in application.yml, so ConfigData bootstrap registers the SDK factories
        // without requesting data. Force one client construction here to observe the configured credential;
        // building a client does not make an HTTP request.
        registry.addCloseListener(event -> {
            try {
                AppConfigurationReplicaClientFactory factory = event.getBootstrapContext()
                    .get(AppConfigurationReplicaClientFactory.class);
                Object connectionManager = factory.getConnections().values().iterator().next();
                Method getAvailableClients = connectionManager.getClass().getDeclaredMethod("getAvailableClients");
                getAvailableClients.setAccessible(true);
                getAvailableClients.invoke(connectionManager);
            } catch (Throwable exception) {
                bootstrapFailure.set(exception);
            }
        });
    }

    private static Class<?> configuredCredentialType(ConfigurationClientBuilder builder) {
        try {
            Field credentialField = ConfigurationClientBuilder.class.getDeclaredField("tokenCredential");
            credentialField.setAccessible(true);
            Object credential = credentialField.get(builder);
            return credential == null ? null : credential.getClass();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not inspect the credential configured on the App Configuration client", exception);
        }
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    static class ReproducerApplication {
    }
}
