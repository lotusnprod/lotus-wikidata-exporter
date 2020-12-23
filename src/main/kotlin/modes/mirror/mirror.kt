/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Copyright (c) 2020 Jonathan Bisson
 */

package net.nprod.wikidataLotusExporter.modes.mirror

import net.nprod.wikidataLotusExporter.getIDfromIRI
import net.nprod.wikidataLotusExporter.rdf.RDFRepository
import net.nprod.wikidataLotusExporter.sparql.LOTUSQueries
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.query.TupleQueryResult
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository
import org.eclipse.rdf4j.repository.util.Repositories
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Size of the blocks of values for each SPARQL query
 */
const val CHUNCK_SIZE = 1000

fun mirror(repositoryLocation: File) {
    val logger = LoggerFactory.getLogger("mirror")
    val sparqlRepository = SPARQLRepository("https://query.wikidata.org/sparql")
    val rdfRepository = RDFRepository(repositoryLocation)

    logger.info("Let's go")

    val fullEntries = mutableListOf<Statement>()
    Repositories.graphQuery(sparqlRepository, LOTUSQueries.queryCompoundTaxonRef) { result ->
        fullEntries.addAll(result)
    }
    logger.info("Done querying the compound-taxo couples")
    logger.info("Adding the compound-taxa")
    rdfRepository.repository.connection.use { it.add(fullEntries) }
    fullEntries.clear()

    val irisToMirror = mutableSetOf<IRI>()
    val taxasToParentMirror = mutableSetOf<IRI>()
    // We add all the ids to a set so we can mirror them
    Repositories.tupleQuery(sparqlRepository, LOTUSQueries.queryIdsLocal) { result: TupleQueryResult ->
        irisToMirror.addAll(
            result.flatMap { bindingSet ->
                val compoundID: IRI = bindingSet.getBinding("compound_id").value as IRI
                val taxonID: IRI = bindingSet.getBinding("taxon_id").value as IRI
                val referenceID: IRI = bindingSet.getBinding("reference_id").value as IRI
                taxasToParentMirror.add(taxonID)
                listOf(compoundID, taxonID, referenceID)
            }
        )
    }

    logger.info("Getting the taxa relations")
    val oldCounter = irisToMirror.size
    taxasToParentMirror.chunked(CHUNCK_SIZE).map {
        val listOfTaxa = it.map { "wd:${it.getIDfromIRI()}" }.joinToString(" ")
        val modifiedQuery = LOTUSQueries.queryTaxonParents.replace("%%IDS%%", listOfTaxa)
        Repositories.tupleQuery(sparqlRepository, modifiedQuery) { result: TupleQueryResult ->
            irisToMirror.addAll(
                result.map { bindingSet -> bindingSet.getBinding("parenttaxon_id").value as IRI }
            )
        }
    }
    logger.info("${irisToMirror.size} entries to mirror (${irisToMirror.size - oldCounter} due to taxon parents)")
    var count = 0

    logger.info("Getting the taxonomic ranks info")
    Repositories.graphQuery(sparqlRepository, LOTUSQueries.queryTaxoRanksInfo) { result -> fullEntries.addAll(result) }

    irisToMirror.chunked(CHUNCK_SIZE).map {
        val listOfCompounds = it.map { "wd:${it.getIDfromIRI()}" }.joinToString(" ")
        val compoundQuery = LOTUSQueries.mirrorQuery.replace("%%IDS%%", listOfCompounds)
        Repositories.graphQuery(sparqlRepository, compoundQuery) { result -> fullEntries.addAll(result) }
        count += it.size
        logger.info(" $count/${irisToMirror.size} done")
    }
    logger.info("Done querying the entries")
    rdfRepository.repository.connection.use { it.add(fullEntries) }
    logger.info("Adding the compounds, taxon and reference info")

    logger.info("We have ${rdfRepository.repository.connection.use { it.size() }} in the local RDF repository now")
}
