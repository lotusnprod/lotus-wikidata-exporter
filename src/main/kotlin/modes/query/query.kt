package net.nprod.wikidataLotusExporter.modes.query

import com.univocity.parsers.tsv.TsvWriter
import com.univocity.parsers.tsv.TsvWriterSettings
import net.nprod.wikidataLotusExporter.rdf.RDFRepository
import net.nprod.wikidataLotusExporter.sparql.LOTUSQueries
import org.eclipse.rdf4j.query.MalformedQueryException
import org.slf4j.LoggerFactory
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter

fun query(repositoryLocation: File, queryFile: File, outFile: File?) {
    val logger = LoggerFactory.getLogger("query")
    val rdfRepository = RDFRepository(repositoryLocation)

    val fileWriter = outFile?.bufferedWriter() ?: BufferedWriter(OutputStreamWriter(System.out))
    fileWriter.use {
        val writer = TsvWriter(fileWriter, TsvWriterSettings())

        rdfRepository.repository.connection.use { connection ->

            val query = queryFile.readText().replace("#!WDDEFAULTIMPORTS", LOTUSQueries.prefixes)

            try {
                connection.prepareTupleQuery(query).evaluate().let { results ->
                    val bindingNames = results.bindingNames
                    writer.writeHeaders(bindingNames)
                    results.map { bindingSet ->
                        writer.writeRow(bindingNames.map { bindingSet.getBinding(it).value })
                    }
                }
            } catch (e: MalformedQueryException) {
                logger.error("SPARQL error: ${e.cause}")
            }
        }
    }
}
