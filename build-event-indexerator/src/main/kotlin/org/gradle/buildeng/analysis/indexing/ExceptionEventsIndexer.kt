package org.gradle.buildeng.analysis.indexing

import com.google.api.services.bigquery.model.TableFieldSchema
import com.google.api.services.bigquery.model.TableSchema
import org.gradle.buildeng.analysis.transform.ExceptionDataEventsJsonTransformer
import java.util.*


object ExceptionEventsIndexer {
    @JvmStatic
    fun main(args: Array<String>) {

        val fieldSchema = ArrayList<TableFieldSchema>()
        fieldSchema.add(TableFieldSchema().setName("exceptionId").setType("STRING").setMode("REQUIRED"))
        fieldSchema.add(TableFieldSchema().setName("className").setType("STRING").setMode("NULLABLE"))
        fieldSchema.add(TableFieldSchema().setName("message").setType("STRING").setMode("NULLABLE"))
        fieldSchema.add(TableFieldSchema().setName("stacktrace").setType("RECORD").setMode("NULLABLE").setFields(listOf(
                TableFieldSchema().setName("stackTraceId").setType("STRING").setMode("REQUIRED"),
                TableFieldSchema().setName("stackFrames").setType("RECORD").setMode("REPEATED").setFields(listOf(
                        TableFieldSchema().setName("stackFrameId").setType("STRING").setMode("REQUIRED"),
                        TableFieldSchema().setName("declaringClass").setType("STRING").setMode("NULLABLE"),
                        TableFieldSchema().setName("methodName").setType("STRING").setMode("NULLABLE"),
                        TableFieldSchema().setName("fileName").setType("STRING").setMode("NULLABLE"),
                        TableFieldSchema().setName("lineNumber").setType("INTEGER").setMode("NULLABLE"),
                        TableFieldSchema().setName("fileRef").setType("STRING").setMode("NULLABLE")
                ))
        )))
        fieldSchema.add(TableFieldSchema().setName("causes").setType("STRING").setMode("REPEATED"))
        fieldSchema.add(TableFieldSchema().setName("metadata").setType("STRING").setMode("NULLABLE"))
        fieldSchema.add(TableFieldSchema().setName("classLevelAnnotations").setType("STRING").setMode("REPEATED"))

        val tableSchema = TableSchema()
        tableSchema.fields = fieldSchema

        val (pipe, options) = KPipe.from<IndexingDataflowPipelineOptions>(args)

        // NOTE: We are using FileIO here to read whole files and filter lines rather than reading using TextIO because using TextIO encounters an Error:
        //       "Total size of the BoundedSource objects generated by split() operation is larger than the allowable limit."
        //       See https://cloud.google.com/dataflow/docs/guides/troubleshooting-your-pipeline#total_number_of_boundedsource_objects_generated_by_splitintobundles_operation_is_larger_than_the_allowable_limit_or_total_size_of_the_boundedsource_objects_generated_by_splitintobundles_operation_is_larger_than_the_allowable_limit
        pipe.fromFiles(input = options.input)
                .filter { it.value.contains("\"eventType\":\"ExceptionData\"") }
                .flatMap { ExceptionDataEventsJsonTransformer().transform(it.value) }
                .map { convertJsonToTableRow(it)!! }
                .toTable(tableId = options.output, tableSchema = tableSchema)

        pipe.run().waitUntilFinish()
    }
}
