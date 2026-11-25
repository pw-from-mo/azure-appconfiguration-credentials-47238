# [BUG] Spring Cloud Azure App Configuration credentials are overwritten by unset global properties

## Description

Spring Cloud Azure App Configuration loses service-specific credentials and profile values when the corresponding global `spring.cloud.azure` values are unset.

This is the same property-merge behavior described in [#47238](https://github.com/Azure/azure-sdk-for-java/issues/47238), which was closed after a request for a minimal reproducer. This report includes a network-free reproducer. It reproduces with Spring Cloud Azure `7.5.0` and `7.4.0` (the version resolved by our application) and with the originally reported `6.0.0`.
...

## Reproducer

Repository: **[add the published reproducer repository URL here]**

Run from the repository root:

```sh
mvn test
```

Run against the version resolved by our application:

```sh
mvn -Pazure-74 test
```

Run against the latest release:

```sh
mvn test
```

Run against the original version from #47238:

```sh
mvn -Pazure-6 test
```
...

No Azure account, service, or real credentials are needed. Maven may need network access to resolve dependencies the first time, but the test itself does not connect to Azure. It binds local App Configuration properties with Spring Boot's `Binder`, then calls the same `AzureGlobalPropertiesUtils.loadProperties(globalProperties, appConfigurationProperties)` method used by `AzureAppConfigBootstrapRegistrar`.
...

## Setup

- Java 21
- Spring Boot 4.1.0 with Spring Cloud Azure `7.4.0` (workflow's dependency; `-Pazure-74`)
- Spring Boot 4.1.2 with Spring Cloud Azure `7.5.0` (latest release; default command)
- Spring Boot 3.5.5 with Spring Cloud Azure `6.0.0` (version from original report; `-Pazure-6`)

## Steps to reproduce

1. Bind these App Configuration properties while leaving global Azure credentials/profile unset:

   ```properties
   spring.cloud.azure.appconfiguration.credential.client-id=repro-client-id
   spring.cloud.azure.appconfiguration.credential.client-secret=repro-client-secret
   spring.cloud.azure.appconfiguration.profile.tenant-id=repro-tenant-id
   ```

2. Invoke `AzureGlobalPropertiesUtils.loadProperties(globalProperties, appConfigurationProperties)`, as the App Configuration bootstrap registrar does.
3. Run the test command above.

## Actual behavior

The test runs 1 test and fails 3 preservation assertions: client ID, client secret, and tenant ID are all `null`. This result was observed with Spring Cloud Azure `7.5.0`, `7.4.0`, and `6.0.0` using the respective commands above.

`AzureGlobalPropertiesUtils.loadProperties` calls `AzurePropertiesUtils.copyAzureCommonProperties`. That method uses `BeanUtils.copyProperties(source.getCredential(), target.getCredential())` and copies profile properties the same way. The global credential fields and profile tenant ID are null when not set, so those nulls overwrite the explicitly bound App Configuration values.

## Expected behavior

Unset global properties should not erase explicitly configured service-specific properties. The resulting App Configuration properties should retain the local client ID, client secret, and tenant ID. Explicitly set global values may continue to override service-specific values if that is the intended precedence.

## Notes

The previous issue was closed because a minimal project was requested but not provided. This report includes a deterministic failing test that exercises the SDK merge directly, without depending on Azure service availability or invalid credentials.
