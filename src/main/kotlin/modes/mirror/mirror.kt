/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Copyright (c) 2020 Jonathan Bisson
 */

package net.nprod.wikidataLotusExporter.modes.mirror

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.nprod.wikidataLotusExporter.rdf.RDFRepository
import net.nprod.wikidataLotusExporter.sparql.LOTUSQueries
import org.eclipse.rdf4j.IsolationLevels
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.query.TupleQueryResult
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository
import org.eclipse.rdf4j.repository.util.Repositories
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

fun IRI.getIDFromIRI(): String = this.stringValue().split("/").last()

/**
 * Size of the blocks of values for each SPARQL query
 */
const val CHUNK_SIZE = 4000
const val LARGE_CHUNK_SIZE = CHUNK_SIZE * 10

fun Repository.addEntriesFromConstruct(query: String = LOTUSQueries.queryCompoundTaxonRef): List<Statement> {
    val list = mutableListOf<Statement>()
    Repositories.graphQuery(this, query) { result ->
        list.addAll(result)
    }
    return list
}

typealias IRISET = Set<IRI>
typealias TAXAIRISET = Set<IRI>

fun Repository.getIRIsAndTaxaIRIs(logger: Logger? = null): Pair<IRISET, TAXAIRISET> {
    val irisToMirror = mutableSetOf<IRI>()
    val taxasToParentMirror = mutableSetOf<IRI>()
    // We add all the ids to a set so we can mirror them
    var count = 0
    Repositories.tupleQuery(this, LOTUSQueries.queryIdsLocal) { result: TupleQueryResult ->
        irisToMirror.addAll(
            result.flatMap { bindingSet ->
                val compoundID: IRI = bindingSet.getBinding("compound_id").value as IRI
                val taxonID: IRI = bindingSet.getBinding("taxon_id").value as IRI
                val referenceID: IRI = bindingSet.getBinding("reference_id").value as IRI
                taxasToParentMirror.add(taxonID)
                count++
                listOf(compoundID, referenceID)
            }
        )
    }
    logger?.info(" We found $count entries mapped to a LOTUS-like triplet")
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
                result.mapNotNull { bindingSet ->
                    when (val value = bindingSet.getBinding("parenttaxon_id").value) {
                        is IRI -> value
                        else -> { // In two cases we have parenttaxon_id being a blank node, we just ignore those two
                            println("Incorrect value $value")
                            null
                        }
                    }
                }
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
 * Get all the compounds that have a found in taxon
 */
fun Repository.getAllCompoundsID(query: String = LOTUSQueries.queryCompoundsOfInterest): Set<IRI> =
    mutableSetOf<IRI>().also { set ->
        Repositories.tupleQuery(this, query) { result ->
            set.addAll(result.map { it.getBinding("compound_id").value as IRI })
        }
    }

/**
 * Get all the information about the given IRIs
 *
 * @param iris Collection of IRIs
 * @param f Function that will be executed every chunk (useful for logging)
 */
suspend fun Repository.getEverythingAbout(
    iris: Collection<IRI>,
    taxoMode: Boolean = false,
    channel: Channel<List<Statement>>,
    f: (Int) -> Unit = { }
) {
    val query = if (taxoMode) LOTUSQueries.mirrorQueryForTaxo else LOTUSQueries.mirrorQuery
    var count = 0
    iris.chunked(CHUNK_SIZE).map {
        val listOfCompounds = it.map { "wd:${it.getIDFromIRI()}" }.joinToString(" ")
        val compoundQuery = query.replace("%%IDS%%", listOfCompounds)
        var statementList: List<Statement> = listOf()
        Repositories.graphQuery(this, compoundQuery) { result ->
            count += it.size
            statementList = result.map { it }
            f(count)
        }
        channel.send(statementList)
    }
}

/**
 * Get the taxa and refs for the given compound IRIs
 *
 * @param iris Collection of compounds IRIs
 * @param f Function that will be executed every chunk (useful for logging)
 */
suspend fun Repository.getTaxaAndRefsAboutGivenCompounds(
    iris: Collection<IRI>,
    chunkSize: Int = CHUNK_SIZE,
    channel: Channel<List<Statement>>,
    f: (Int) -> Unit = {}
): List<Statement> {
    val list = mutableListOf<Statement>()
    val query = LOTUSQueries.queryCompoundTaxonRefModularForCompoundIds
    var count = 0
    iris.chunked(chunkSize).map {
        val listOfCompounds = it.map { "wd:${it.getIDFromIRI()}" }.joinToString(" ")
        val compoundQuery = query.replace("%%IDS%%", listOfCompounds)
        var statementList: List<Statement> = listOf()
        Repositories.graphQuery(this, compoundQuery) { result -> statementList = result.map { it } }
        count += it.size
        channel.send(statementList)
        f(count)
    }
    return list
}

@Suppress("MagicNumber")
fun mirror(repositoryLocation: File) = runBlocking<Unit> {
    val logger = LoggerFactory.getLogger("mirror")
    val sparqlRepository = SPARQLRepository("https://query.wikidata.org/sparql")
    val rdfRepository = RDFRepository(repositoryLocation)

    logger.info("Starting in mirroring mode into the repository: $repositoryLocation")

    val channelStatements = Channel<List<Statement>>(CHUNK_SIZE * 2)

    // This is the coroutine that loads the data into the triple store
    val repositoryWriter = launch(Dispatchers.IO) {
        logger.info("Started the writer coroutine")
        rdfRepository.repository.connection.use {
            it.isolationLevel = IsolationLevels.READ_UNCOMMITTED
            for (statement in channelStatements) {
                it.add(statement)
            }
        }
    }

    launch(Dispatchers.IO) {
        logger.info("Querying Wikidata for the compounds having a found in taxon")
        val compoundsIRIList = sparqlRepository.getAllCompoundsID()

        logger.info("We found ${compoundsIRIList.size} compounds")

        logger.info("Querying Wikidata for all the triplets taxon-compound-reference")
        sparqlRepository.getTaxaAndRefsAboutGivenCompounds(
            compoundsIRIList,
            channel = channelStatements,
            chunkSize = LARGE_CHUNK_SIZE
        ) {
            logger.info(" ${100 * it / compoundsIRIList.size}%")
        }

        logger.info("Querying the local data for all the ids we need")
        val (irisToMirror, taxaToMirror) = rdfRepository.repository.getIRIsAndTaxaIRIs(logger)

        logger.info("We have ${irisToMirror.size} entries and ${taxaToMirror.size} taxa")

        logger.info("Getting the taxa relations remotely")

        val newTaxaToMirrorIRIs = sparqlRepository.getTaxaParentIRIs(taxaToMirror)

        logger.info(
            "${irisToMirror.size} entries to mirror including ${taxaToMirror.size} for taxa + " +
                " ${newTaxaToMirrorIRIs.size} for their parents"
        )

        logger.info("Getting the taxonomic ranks info")
        channelStatements.send(sparqlRepository.getAllTaxRanks())


        logger.info("Gathering full data about all the taxo")
        val allIRIsTaxo = newTaxaToMirrorIRIs.toSet() + taxaToMirror.toSet()
        sparqlRepository.getEverythingAbout(
            allIRIsTaxo,
            channel = channelStatements,
            taxoMode = true
        ) { count ->
            logger.info(" $count/${allIRIsTaxo.size} done")
        }

        logger.info("Gathering full data about all the compounds and references")
        val allIRIs = irisToMirror.toSet()
        sparqlRepository.getEverythingAbout(allIRIs, channel = channelStatements) { count ->
            logger.info(" $count/${allIRIs.size} done")
        }

        channelStatements.close()
    }

    // We wait for the writer to be done
    repositoryWriter.join()
    logger.info("We have ${rdfRepository.repository.connection.use { it.size() }} entries in the local repository")

    logger.info("Loading OTOL data (if existing)")
}
