package repro;

import com.azure.spring.cloud.autoconfigure.implementation.appconfiguration.AzureAppConfigurationProperties;
import com.azure.spring.cloud.autoconfigure.implementation.context.properties.AzureGlobalProperties;
import com.azure.spring.cloud.autoconfigure.implementation.properties.utils.AzureGlobalPropertiesUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class AzureAppConfigurationCredentialMergeTest {

    private static final String CLIENT_ID = "repro-client-id";
    private static final String CLIENT_SECRET = "repro-client-secret";
    private static final String TENANT_ID = "repro-tenant-id";

    @Test
    void loadPropertiesShouldPreserveAppConfigurationCredentialsWhenGlobalPropertiesAreUnset() {
        // given
        var source = new MapConfigurationPropertySource(Map.of(
            "spring.cloud.azure.appconfiguration.credential.client-id", CLIENT_ID,
            "spring.cloud.azure.appconfiguration.credential.client-secret", CLIENT_SECRET,
            "spring.cloud.azure.appconfiguration.profile.tenant-id", TENANT_ID
        ));
        var binder = new Binder(source);
        var globalProperties = binder.bind(AzureGlobalProperties.PREFIX, Bindable.of(AzureGlobalProperties.class))
            .orElseGet(AzureGlobalProperties::new);
        var appConfigurationProperties = binder
            .bind(AzureAppConfigurationProperties.PREFIX, Bindable.of(AzureAppConfigurationProperties.class))
            .orElseGet(AzureAppConfigurationProperties::new);

        assertThat(appConfigurationProperties.getCredential().getClientId()).isEqualTo(CLIENT_ID);
        assertThat(appConfigurationProperties.getCredential().getClientSecret()).isEqualTo(CLIENT_SECRET);
        assertThat(appConfigurationProperties.getProfile().getTenantId()).isEqualTo(TENANT_ID);

        // when
        var loadedProperties = AzureGlobalPropertiesUtils.loadProperties(globalProperties, appConfigurationProperties);

        // then
        assertAll(
            () -> assertThat(loadedProperties.getCredential().getClientId())
                .as("App Configuration client ID")
                .isEqualTo(CLIENT_ID),
            () -> assertThat(loadedProperties.getCredential().getClientSecret())
                .as("App Configuration client secret")
                .isEqualTo(CLIENT_SECRET),
            () -> assertThat(loadedProperties.getProfile().getTenantId())
                .as("App Configuration tenant ID")
                .isEqualTo(TENANT_ID)
        );
    }
}
