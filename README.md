# Spring Cloud Azure App Configuration credential repro

Standalone reproducers for [Azure SDK for Java issue #47238](https://github.com/Azure/azure-sdk-for-java/issues/47238). The default Maven profile uses Spring Cloud Azure `7.5.0`; `-Pazure-74` uses the `7.4.0` version resolved by `infra/oemportal-workflow2`; `-Pazure-6` uses the `6.0.0` version in the original report.
...

## Run

Requirements: JDK 17+ and Maven 3.9+.

Run the focused property-merge test against the latest release:

```sh
mvn -Dtest=AzureAppConfigurationCredentialMergeTest test
```

Run the focused test against workflow 2's version:

```sh
mvn -Pazure-74 -Dtest=AzureAppConfigurationCredentialMergeTest test
```

Run the focused test against the original reported version:

```sh
mvn -Pazure-6 -Dtest=AzureAppConfigurationCredentialMergeTest test
```

Run the integrated Spring Boot context test against workflow 2's version:

```sh
mvn -Pazure-74 -Dtest=AzureAppConfigurationBootstrapContextTest test
```
...

No Azure account, real credentials, Azure network access, or local listening socket is needed. The tests use synthetic values and the Boot context test disables the store after configuring the `azureAppConfiguration` ConfigData import. Maven may need network access to download dependencies on the first run.

## Focused property-merge test

`AzureAppConfigurationCredentialMergeTest` uses Spring Boot's `Binder` to bind service-specific client ID, secret, and tenant ID, then calls the same `AzureGlobalPropertiesUtils.loadProperties` merge used by the SDK bootstrap. With the affected implementation, null global values replace all three service-specific fields, so the preservation assertions fail.

## Integrated Spring Boot context test

`AzureAppConfigurationBootstrapContextTest` starts a real Spring Boot `ApplicationContext` and loads `spring.config.import=azureAppConfiguration` with synthetic service-specific credentials. The store is disabled, so ConfigData initialization registers the SDK bootstrap factories but does not issue requests. The test asks the registered SDK factory to build its client (without sending an HTTP request) and inspects the `TokenCredential` configured on `ConfigurationClientBuilder` before any customizer can replace it.

With the affected merge, the builder has a `DefaultAzureCredential`; the test expects a `ClientSecretCredential` because the service-specific client ID, secret, and tenant ID are present in `application.yml`. The context starts successfully, and the test fails at that assertion. This demonstrates that the SDK discarded the configured service credentials. The environment still exposes the original YAML values; it is the merged SDK properties used for client creation that lose them.

## Expected behavior

Unset global properties should not erase explicitly configured App Configuration credentials or tenant ID. With the synthetic service-specific client ID, secret, and tenant ID configured, the SDK bootstrap should configure a `ClientSecretCredential` on the App Configuration client builder.
