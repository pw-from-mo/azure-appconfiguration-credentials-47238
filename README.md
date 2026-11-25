# Spring Cloud Azure App Configuration credential merge repro

Minimal, network-free reproducer for [Azure SDK for Java issue #47238](https://github.com/Azure/azure-sdk-for-java/issues/47238). It targets Spring Cloud Azure `7.5.0`, the latest released version as of 2026-11-25. The `azure-74` profile tests the `7.4.0` version used by `infra/oemportal-workflow2`; the `azure-6` profile tests the `6.0.0` version in the original report.
...

## Run

Requirements: JDK 17+ and Maven 3.9+.

```sh
mvn test
```

To check the workflow 2 dependency:

```sh
mvn -Pazure-74 test
```

To check the original reported SDK version:

```sh
mvn -Pazure-6 test
```
...

The test uses synthetic credential strings and never authenticates. No Azure account or store is needed. Maven may need network access to download dependencies on the first run; the test itself makes no Azure network calls.

## What the test does

The test uses Spring Boot's `Binder` to bind `spring.cloud.azure.appconfiguration.credential` and `spring.cloud.azure.appconfiguration.profile.tenant-id`, just as the SDK bootstrap registrar binds the global and service-specific properties. It then invokes the SDK's `AzureGlobalPropertiesUtils.loadProperties(global, appConfigurationProperties)` merge.

The global properties are left unset. The test asserts that merging them preserves the locally bound client ID, secret, and tenant ID. With the affected implementation, `AzurePropertiesUtils.copyAzureCommonProperties` calls `BeanUtils.copyProperties` for global credential/profile fields, copying null global values over those local values. `mvn test` therefore fails on the preservation assertions; that failure is the expected reproduction. The same test can be run against SDK versions `7.4.0` (`-Pazure-74`) and `6.0.0` (`-Pazure-6`).

## Expected behavior

Unset global properties should not erase explicitly configured App Configuration credentials or tenant ID. The `loadProperties` result should retain `repro-client-id`, `repro-client-secret`, and `repro-tenant-id`.
