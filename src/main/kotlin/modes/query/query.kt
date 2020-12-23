/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Copyright (c) 2020 Jonathan Bisson
 */

package net.nprod.wikidataLotusExporter.modes.query

import com.univocity.parsers.tsv.TsvWriter
import com.univocity.parsers.tsv.TsvWriterSettings
import net.nprod.wikidataLotusExporter.rdf.RDFRepository
import net.nprod.wikidataLotusExporter.sparql.LOTUSQueries
import org.eclipse.rdf4j.query.MalformedQueryException
import org.eclipse.rdf4j.repository.RepositoryConnection
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository
import org.slf4j.LoggerFactory
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter

/**
 * Write the results from the SPARQL query to a TSV file
 */
fun RepositoryConnection.queryToTSV(tsvWriter: TsvWriter, query: String) {
    this.prepareTupleQuery(query).evaluate().let { results ->
        val bindingNames = results.bindingNames
        tsvWriter.writeHeaders(bindingNames)
        results.map { bindingSet ->
            tsvWriter.writeRow(bindingNames.map { bindingSet.getBinding(it).value })
        }
    }
}

fun query(repositoryLocation: File, queryFile: File, outFile: File?, direct: Boolean) {
    val logger = LoggerFactory.getLogger("query")
    val rdfRepository = RDFRepository(repositoryLocation)
    val connection = if (direct) {
        SPARQLRepository("https://query.wikidata.org/sparql").connection
    } else {
        rdfRepository.repository.connection
    }
    val fileWriter = outFile?.bufferedWriter() ?: BufferedWriter(OutputStreamWriter(System.out))
    fileWriter.use {
        val tsvWriter = TsvWriter(fileWriter, TsvWriterSettings())

        connection.use { connection ->
            val query = queryFile.readText().replace("#!WDDEFAULTIMPORTS", LOTUSQueries.prefixes)

            try {
                connection.queryToTSV(tsvWriter, query)
            } catch (e: MalformedQueryException) {
                logger.error("SPARQL error: ${e.cause}")
            }
        }
    }
}
