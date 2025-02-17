package com.malinskiy.marathon.config.serialization

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.malinskiy.marathon.config.Configuration
import com.malinskiy.marathon.config.environment.EnvironmentReader
import com.malinskiy.marathon.config.environment.SystemEnvironmentReader
import com.malinskiy.marathon.config.exceptions.ConfigurationException
import com.malinskiy.marathon.config.serialization.time.InstantTimeProviderImpl
import com.malinskiy.marathon.config.serialization.yaml.SerializeModule
import com.malinskiy.marathon.config.vendor.VendorConfiguration
import com.malinskiy.marathon.config.vendor.apple.AppleTestBundleConfiguration
import com.malinskiy.marathon.config.vendor.apple.SshAuthentication
import com.malinskiy.marathon.config.vendor.apple.ios.PullingPolicy
import org.apache.commons.text.StringSubstitutor
import org.apache.commons.text.lookup.StringLookupFactory
import java.io.File

class ConfigurationFactory(
    private val marathonfileDir: File,
    private val environmentReader: EnvironmentReader = SystemEnvironmentReader(),
    private val mapper: ObjectMapper = ObjectMapper(
        YAMLFactory()
            .disable(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID)
    ).apply {
        setSerializationInclusion(JsonInclude.Include.NON_NULL)
        registerModule(SerializeModule(InstantTimeProviderImpl(), marathonfileDir))
        registerModule(
            KotlinModule.Builder()
                .withReflectionCacheSize(512)
                .configure(KotlinFeature.NullToEmptyCollection, false)
                .configure(KotlinFeature.NullToEmptyMap, false)
                .configure(KotlinFeature.NullIsSameAsDefault, false)
                .configure(KotlinFeature.SingletonSupport, true)
                .configure(KotlinFeature.StrictNullChecks, false)
                .build()
        )
        registerModule(JavaTimeModule())
        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        configure(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false)
    },
    private val environmentVariableSubstitutor: StringSubstitutor = StringSubstitutor(StringLookupFactory.INSTANCE.environmentVariableStringLookup()),
    private val analyticsTracking: Boolean? = null,
    private val bugsnagReporting: Boolean? = null,
) {
    fun parse(marathonfile: File): Configuration {
        val configWithEnvironmentVariablesReplaced = environmentVariableSubstitutor.replace(marathonfile.readText())
        try {
            val configuration = mapper.readValue(configWithEnvironmentVariablesReplaced, Configuration::class.java)
            val vendorConfiguration = when (configuration.vendorConfiguration) {
                is VendorConfiguration.AndroidConfiguration -> {
                    if (configuration.vendorConfiguration.androidSdk == null) {
                        val androidSdk =
                            environmentReader.read().androidSdk ?: throw ConfigurationException("No android SDK path specified")
                        configuration.vendorConfiguration.copy(
                            androidSdk = androidSdk
                        )
                    } else {
                        configuration.vendorConfiguration
                    }
                }

                is VendorConfiguration.IOSConfiguration -> {
                    // Any relative path specified in Marathonfile should be resolved against the directory Marathonfile is in
                    val iosConfiguration: VendorConfiguration.IOSConfiguration = configuration.vendorConfiguration
                    val resolvedBundle = iosConfiguration.bundle?.let {
                        val resolvedDerivedDataDir = it.derivedDataDir?.let { ddd -> marathonfileDir.resolve(ddd) }
                        val resolvedApplication = it.application?.let { ddd -> marathonfileDir.resolve(ddd) }
                        val resolvedTestApplication = it.testApplication?.let { ddd -> marathonfileDir.resolve(ddd) }
                        val resolvedExtraApplications = it.extraApplications?.map { ddd -> marathonfileDir.resolve(ddd) }

                        AppleTestBundleConfiguration(
                            resolvedApplication,
                            resolvedTestApplication,
                            resolvedExtraApplications,
                            resolvedDerivedDataDir,
                            it.testType
                        ).apply { validate() }
                    }

                    val resolvedMediaFiles = iosConfiguration.mediaFiles?.map { ddd -> marathonfileDir.resolve(ddd) }

                    val optionalDevices = configuration.vendorConfiguration.devicesFile?.resolveAgainst(marathonfileDir)
                        ?: marathonfileDir.resolve("Marathondevices")

                    val optionalknownHostsPath = iosConfiguration.ssh.knownHostsPath?.resolveAgainst(marathonfileDir)
                    val optionalSshAuthentication = when (iosConfiguration.ssh.authentication) {
                        is SshAuthentication.PasswordAuthentication -> iosConfiguration.ssh.authentication
                        is SshAuthentication.PublicKeyAuthentication -> iosConfiguration.ssh.authentication.copy(
                            username = iosConfiguration.ssh.authentication.username,
                            key = iosConfiguration.ssh.authentication.key.resolveAgainst(marathonfileDir)
                        )

                        null -> null
                    }
                    val optionalSshConfiguration = iosConfiguration.ssh.copy(
                        authentication = optionalSshAuthentication,
                        knownHostsPath = optionalknownHostsPath,
                    )

                    val xcresultConfiguration = when (iosConfiguration.xcresult.pull) {
                        true -> iosConfiguration.xcresult.copy(
                            pullingPolicy = PullingPolicy.ALWAYS,
                            remoteClean = iosConfiguration.xcresult.remoteClean
                        )

                        false -> iosConfiguration.xcresult.copy(
                            pullingPolicy = PullingPolicy.NEVER,
                            remoteClean = iosConfiguration.xcresult.remoteClean
                        )

                        null -> iosConfiguration.xcresult
                    }

                    configuration.vendorConfiguration.copy(
                        bundle = resolvedBundle,
                        mediaFiles = resolvedMediaFiles,
                        devicesFile = optionalDevices,
                        ssh = optionalSshConfiguration,
                        xcresult = xcresultConfiguration,
                    )
                }

                is VendorConfiguration.MacosConfiguration -> {
                    // Any relative path specified in Marathonfile should be resolved against the directory Marathonfile is in
                    val macosConfiguration: VendorConfiguration.MacosConfiguration = configuration.vendorConfiguration
                    val resolvedBundle = macosConfiguration.bundle?.let {
                        val resolvedDerivedDataDir = it.derivedDataDir?.let { ddd -> marathonfileDir.resolve(ddd) }
                        val resolvedApplication = it.application?.let { ddd -> marathonfileDir.resolve(ddd) }
                        val resolvedTestApplication = it.testApplication?.let { ddd -> marathonfileDir.resolve(ddd) }
                        val resolvedExtraApplications = it.extraApplications?.map { ddd -> marathonfileDir.resolve(ddd) }

                        AppleTestBundleConfiguration(
                            resolvedApplication,
                            resolvedTestApplication,
                            resolvedExtraApplications,
                            resolvedDerivedDataDir,
                            it.testType
                        ).apply { validate() }
                    }
                    val optionalDevices = configuration.vendorConfiguration.devicesFile?.resolveAgainst(marathonfileDir)
                        ?: marathonfileDir.resolve("Marathondevices")

                    val optionalknownHostsPath = macosConfiguration.ssh.knownHostsPath?.resolveAgainst(marathonfileDir)
                    val optionalSshAuthentication = when (macosConfiguration.ssh.authentication) {
                        is SshAuthentication.PasswordAuthentication -> macosConfiguration.ssh.authentication
                        is SshAuthentication.PublicKeyAuthentication -> macosConfiguration.ssh.authentication.copy(
                            username = macosConfiguration.ssh.authentication.username,
                            key = macosConfiguration.ssh.authentication.key.resolveAgainst(marathonfileDir)
                        )

                        null -> null
                    }
                    val optionalSshConfiguration = macosConfiguration.ssh.copy(
                        authentication = optionalSshAuthentication,
                        knownHostsPath = optionalknownHostsPath,
                    )

                    val xcresultConfiguration = when (macosConfiguration.xcresult.pull) {
                        true -> macosConfiguration.xcresult.copy(
                            pullingPolicy = PullingPolicy.ALWAYS,
                            remoteClean = macosConfiguration.xcresult.remoteClean
                        )

                        false -> macosConfiguration.xcresult.copy(
                            pullingPolicy = PullingPolicy.NEVER,
                            remoteClean = macosConfiguration.xcresult.remoteClean
                        )

                        null -> macosConfiguration.xcresult
                    }

                    configuration.vendorConfiguration.copy(
                        bundle = resolvedBundle,
                        devicesFile = optionalDevices,
                        ssh = optionalSshConfiguration,
                        xcresult = xcresultConfiguration,
                    )
                }

                VendorConfiguration.StubVendorConfiguration -> configuration.vendorConfiguration
                is VendorConfiguration.EmptyVendorConfiguration -> throw ConfigurationException("No vendor configuration specified")
            }
            return configuration.copy(
                vendorConfiguration = vendorConfiguration,
                // Default value for analyticsTracking / bugsnagReporting in both CLI and marathon / gradle config is true.
                // If analyticsTracking / bugsnagReporting is set to false in either CLI or marathon / gradle config, it will be disabled.
                // Note that it's not possible to set the CLI value when running marathon via gradle.
                analyticsTracking = if (analyticsTracking == false || configuration.analyticsTracking == false) false else true,
                bugsnagReporting = if (bugsnagReporting == false || configuration.bugsnagReporting == false) false else true
            )
        } catch (e: JsonProcessingException) {
            throw ConfigurationException("Error parsing config file ${marathonfile.absolutePath}", e)
        }
    }

    fun serialize(configuration: Configuration): String {
        return mapper.writeValueAsString(configuration)
    }
}

// inverted [resolve] call allows to avoid too many if expressions
private fun File.resolveAgainst(file: File): File = file.resolve(this).canonicalFile
