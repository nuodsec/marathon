package com.malinskiy.marathon.config.strategy

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.Instant

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = BatchingStrategyConfiguration.FixedSizeBatchingStrategyConfiguration::class, name = "fixed-size"),
    JsonSubTypes.Type(value = BatchingStrategyConfiguration.IsolateBatchingStrategyConfiguration::class, name = "isolate"),
    JsonSubTypes.Type(value = BatchingStrategyConfiguration.ClassNameBatchingStrategyConfiguration::class, name = "class-name"),
    JsonSubTypes.Type(value = BatchingStrategyConfiguration.MixedBatchingStrategyConfiguration::class, name = "mixed"),
)
sealed class BatchingStrategyConfiguration {
    data class FixedSizeBatchingStrategyConfiguration(
        val size: Int,
        @JsonInclude(JsonInclude.Include.NON_NULL) val durationMillis: Long? = null,
        @JsonInclude(JsonInclude.Include.NON_NULL) val percentile: Double? = null,
        @JsonInclude(JsonInclude.Include.NON_NULL) val timeLimit: Instant? = null,
        val lastMileLength: Int = 0
    ) : BatchingStrategyConfiguration()

    data class MixedBatchingStrategyConfiguration(
        val isolateAnnotationName: Regex?,
        val unionAnnotationName: Regex?,
    ) : BatchingStrategyConfiguration()

    object ClassNameBatchingStrategyConfiguration : BatchingStrategyConfiguration()

    object IsolateBatchingStrategyConfiguration : BatchingStrategyConfiguration()
}
