/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Copyright (c) 2020 Jonathan Bisson
 */

package net.nprod.wikidataLotusExporter.modes.mirror

import net.nprod.wikidataLotusExporter.rdf.RDFRepository
import net.nprod.wikidataLotusExporter.sparql.LOTUSQueries
import org.eclipse.rdf4j.IsolationLevels
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.query.TupleQueryResult
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository
import org.eclipse.rdf4j.repository.util.Repositories
import org.slf4j.LoggerFactory
import java.io.File

fun IRI.getIDFromIRI(): String = this.stringValue().split("/").last()

/**
 * Size of the blocks of values for each SPARQL query
 */
const val CHUNK_SIZE = 4000

fun Repository.addEntriesFromConstruct(query: String = LOTUSQueries.queryCompoundTaxonRef): List<Statement> {
    val list = mutableListOf<Statement>()
    Repositories.graphQuery(this, query) { result ->
        list.addAll(result)
    }
    return list
}

typealias IRISET = Set<IRI>
typealias TAXAIRISET = Set<IRI>

fun Repository.getIRIsAndTaxaIRIs(): Pair<IRISET, TAXAIRISET> {
    val irisToMirror = mutableSetOf<IRI>()
    val taxasToParentMirror = mutableSetOf<IRI>()
    // We add all the ids to a set so we can mirror them
    Repositories.tupleQuery(this, LOTUSQueries.queryIdsLocal) { result: TupleQueryResult ->
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
    return Pair(irisToMirror.toSet(), taxasToParentMirror.toSet())
}

/**
 * Complete the given set of Taxa IRIs with all the parents IRIs
 *
 * @param taxasToParentMirror All the taxa you want parents off
 * @return A set of IRIs of all the parents
 */
fun Repository.getTaxaParentIRIs(taxasToParentMirror: TAXAIRISET): Set<IRI> {
    val newIRIsToMirror = mutableSetOf<IRI>()
    taxasToParentMirror.chunked(CHUNK_SIZE).forEach {
        val listOfTaxa = it.map { "wd:${it.getIDFromIRI()}" }.joinToString(" ")
        val modifiedQuery = LOTUSQueries.queryTaxonParents.replace("%%IDS%%", listOfTaxa)
        Repositories.tupleQuery(this, modifiedQuery) { result: TupleQueryResult ->
            newIRIsToMirror.addAll(
                result.map { bindingSet -> bindingSet.getBinding("parenttaxon_id").value as IRI }
            )
        }
    }
    return newIRIsToMirror
}

fun Repository.getAllTaxRanks(query: String = LOTUSQueries.queryTaxoRanksInfo): List<Statement> {
    val list = mutableListOf<Statement>()
    Repositories.graphQuery(this, query) { result ->
        list.addAll(result)
    }
    return list
}

/**
 * Get all the information about the given IRIs
 *
 * @param iris Collection of IRIs
 * @param f Function that will be executed every chunk (useful for logging)
 */
fun Repository.getEverythingAbout(
    iris: Collection<IRI>,
    taxoMode: Boolean = false,
    f: (Int) -> Unit = {}
): List<Statement> {
    val list = mutableListOf<Statement>()
    val query = if (taxoMode) LOTUSQueries.mirrorQueryForTaxo else LOTUSQueries.mirrorQuery
    var count = 0
    iris.chunked(CHUNK_SIZE).map {
        val listOfCompounds = it.map { "wd:${it.getIDFromIRI()}" }.joinToString(" ")
        val compoundQuery = query.replace("%%IDS%%", listOfCompounds)
        Repositories.graphQuery(this, compoundQuery) { result -> list.addAll(result) }
        count += it.size
        f(count)
    }
    return list
}

fun mirror(repositoryLocation: File) {
    val logger = LoggerFactory.getLogger("mirror")
    val sparqlRepository = SPARQLRepository("https://query.wikidata.org/sparql")
    val rdfRepository = RDFRepository(repositoryLocation)

    logger.info("Starting in mirroring mode into the repository: $repositoryLocation")

    logger.info("Querying Wikidata for all the triplets taxon-compound-reference")
    val fullEntries = sparqlRepository.addEntriesFromConstruct()

    logger.info("Adding the data to our local repository")
    rdfRepository.repository.connection.use {
        it.isolationLevel = IsolationLevels.NONE
        it.add(fullEntries)
    }

    logger.info("Querying the local data for all the ids we need")
    val (irisToMirror, taxaToMirror) = rdfRepository.repository.getIRIsAndTaxaIRIs()

    logger.info("We have ${irisToMirror.size} and ${taxaToMirror.size} taxa")

    logger.info("Getting the taxa relations remotely")

    val newTaxaToMirrorIRIs = sparqlRepository.getTaxaParentIRIs(taxaToMirror)

    logger.info(
        "${irisToMirror.size} entries to mirror + ${taxaToMirror.size} for taxa + " +
            " ${newTaxaToMirrorIRIs.size} for their parents"
    )

    logger.info("Getting the taxonomic ranks info")
    rdfRepository.repository.connection.use {
        it.isolationLevel = IsolationLevels.NONE
        it.add(
            sparqlRepository.getAllTaxRanks()
        )
    }

    logger.info("Gathering full data about all the taxo")
    val allIRIsTaxo = newTaxaToMirrorIRIs.toSet() + taxaToMirror.toSet()
    val fullDataTaxo = sparqlRepository.getEverythingAbout(allIRIsTaxo, taxoMode = true) { count ->
        logger.info(" $count/${allIRIsTaxo.size} done")
    }

    logger.info("Gathering full data about all the compounds and references")
    val allIRIs = irisToMirror.toSet()
    val fullData = sparqlRepository.getEverythingAbout(allIRIs) { count ->
        logger.info(" $count/${allIRIs.size} done")
    }

    logger.info("Adding the queried info on all the compounds, taxa and references to the local repository")
    rdfRepository.repository.connection.use {
        it.isolationLevel = IsolationLevels.NONE
        it.add(fullData)
        it.add(fullDataTaxo)
    }

    logger.info("We have ${rdfRepository.repository.connection.use { it.size() }} in the local RDF repository now")
}
