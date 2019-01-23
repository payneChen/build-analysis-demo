package org.gradle.buildeng.analysis.transform

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import org.gradle.buildeng.analysis.common.NullAvoidingStringSerializer
import org.gradle.buildeng.analysis.model.BuildEvent
import org.gradle.buildeng.analysis.model.ExceptionData
import org.gradle.buildeng.analysis.model.StackFrame
import org.gradle.buildeng.analysis.model.StackTrace

/**
 * Transforms input of the following format to JSON that is BigQuery-compatible. See https://cloud.google.com/bigquery/docs/loading-data-cloud-storage-json#limitations
 */
class ExceptionDataEventsJsonTransformer {

    private val objectMapper = ObjectMapper()
    private val objectReader = objectMapper.reader()
    private val objectWriter = objectMapper.writer()

    init {
        objectMapper.registerModule(object : SimpleModule() {
            init {
                addSerializer(NullAvoidingStringSerializer())
            }
        })
    }

    fun transform(input: String): List<String> {
        val buildEvent = BuildEvent.fromJson(objectReader.readTree(input))!!
        val stackTracesNode = buildEvent.data.get("stackTraces")
        val stackFramesNode = buildEvent.data.get("stackFrames")

        return buildEvent.data.get("exceptions").fields().asSequence().map { exceptionKVs ->
            val stackTraceNode = stackTracesNode.get(exceptionKVs.value.get("stackTrace").asText())
            val stackFrameIds = stackTraceNode.get("stackFrames")
                    .map { stackFrameNode ->
                        val stackFrameId = stackFrameNode.asText()
                        val jsonNode = stackFramesNode.get(stackFrameNode.asText())
                        StackFrame(
                                stackFrameId,
                                jsonNode.path("declaringClass").asText(),
                                jsonNode.path("methodName").asText(),
                                jsonNode.path("fileName").asText(),
                                jsonNode.path("lineNumber").asInt(),
                                jsonNode.path("fileRef").asText())
                    }

            val stackTrace = StackTrace(exceptionKVs.value.path("stackTrace").asText(), stackFrameIds)

            ExceptionData(
                    exceptionKVs.key,
                    exceptionKVs.value.path("className").asText(),
                    exceptionKVs.value.path("message").asText(),
                    stackTrace,
                    exceptionKVs.value.path("causes").map { it.asText() },
                    objectWriter.writeValueAsString(exceptionKVs.value.path("metadata")),
                    exceptionKVs.value.path("classLevelAnnotations").map { it.asText() }
            )
        }.toList().map {
            objectWriter.writeValueAsString(objectMapper.convertValue(it, JsonNode::class.java))
        }
    }
}
