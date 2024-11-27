package com.malinskiy.marathon.execution.strategy.impl.batching

import com.malinskiy.marathon.analytics.external.Analytics
import com.malinskiy.marathon.config.strategy.BatchingStrategyConfiguration
import com.malinskiy.marathon.execution.bundle.TestBundleIdentifier
import com.malinskiy.marathon.execution.strategy.BatchingStrategy
import com.malinskiy.marathon.test.MetaProperty
import com.malinskiy.marathon.test.Test
import com.malinskiy.marathon.test.TestBatch
import com.malinskiy.marathon.test.toClassName
import java.util.Queue

class MixedBatchingStrategy(private val cnf: BatchingStrategyConfiguration.MixedBatchingStrategyConfiguration) : BatchingStrategy {

    override fun process(queue: Queue<Test>, analytics: Analytics, testBundleIdentifier: TestBundleIdentifier?): TestBatch {
        queue.find {
            it.metaProperties.all { metaProperty ->
                !checkNeedIsolate(metaProperty) && !checkNeedUnion(metaProperty)
            }
        }?.let { noAnnotationTest ->
            val classNameSelector = noAnnotationTest.toClassName()
            val classNameTestGroup = queue.toList().filter { test ->
                test.toClassName() == classNameSelector
                    && test.metaProperties.stream().allMatch {
                    !checkNeedIsolate(it) && !checkNeedUnion(it)
                }
            }
            queue.removeAll(classNameTestGroup.toSet())
            return TestBatch(classNameTestGroup)
        }

        queue.find {
            it.metaProperties.any(this::checkNeedUnion)
        }?.let { unionAnnotationTest ->
            unionAnnotationTest.metaProperties.find(this::checkNeedUnion)
                ?.let { metaProperty ->
                    val unionKey = metaProperty.values["value"]
                    val unionTestGroup = queue.toList()
                        .filter { test ->
                            test.metaProperties.stream()
                                .anyMatch { property -> checkNeedUnion(property) && property.values["value"] == unionKey }
                        }
                    queue.removeAll(unionTestGroup.toSet())
                    return TestBatch(unionTestGroup)
                }
        }
        return TestBatch(listOf(queue.poll()))

    }

    private fun checkNeedIsolate(mt: MetaProperty): Boolean {
        return cnf.isolateAnnotationName?.matches(mt.name) ?: false
    }

    private fun checkNeedUnion(mt: MetaProperty): Boolean {
        return cnf.unionAnnotationName?.matches(mt.name) ?: false
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MixedBatchingStrategy

        return true
    }

    override fun hashCode(): Int {
        return javaClass.canonicalName.hashCode()
    }

    override fun toString(): String {
        return "MixedBatchingStrategy()"
    }
}
