/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Copyright (c) 2021
 */

package net.nprod.wikidataLotusExporter.modes.export

import net.nprod.wikidataLotusExporter.lotus.models.Compound
import net.nprod.wikidataLotusExporter.lotus.models.Taxon
import net.nprod.wikidataLotusExporter.rdf.RDFRepository
import net.nprod.wikidataLotusExporter.rdf.vocabulary.Wikidata
import net.nprod.wikidataLotusExporter.rdf.vocabulary.WikidataChemistry
import net.nprod.wikidataLotusExporter.rdf.vocabulary.WikidataTaxonomy
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Resource
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.repository.RepositoryConnection
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Thrown when an object that should exist in a specific number or range is out of specification
 */
class CardinalityException(override val message: String?) : RuntimeException()

fun RepositoryConnection.getSingleObjectOrFail(subject: Resource, predicate: IRI): Value? =
    this.getStatements(subject, predicate, null).let {
        val results = it.map { it.`object` }
        if (results.size > 1)
            throw CardinalityException(
                "This subject: $subject has more than one $predicate: it has ${results.size} total!"
            )
        results.firstOrNull()
    }

/**
 * This will select the InchiKey, if multiple are present, it will take the one that has the stereochemistry
 *
 * We have to do that as many compounds on WikiData have two
 */
fun RepositoryConnection.getBestInchiKey(subject: Resource): String? =
    this.getStatements(subject, WikidataChemistry.Properties.inchiKey, null).let {
        val results = it.map { it.`object` }
        if (results.size > 2)
            throw CardinalityException(
                "This subject: $subject has more than one InChiKeys: it has ${results.size} total!"
            )
        val resultsEventuallyFiltered = if (results.size == 2)
            results.filterNot { it.toString().endsWith("UHFFFAOYSA-N") }
        else
            results
        resultsEventuallyFiltered.firstOrNull()?.toString()
    }

fun export(repositoryLocation: File, outFile: File?, direct: Boolean) {
    val logger = LoggerFactory.getLogger("export")
    val rdfRepository = RDFRepository(repositoryLocation)
    val connection = if (direct) {
        SPARQLRepository("https://query.wikidata.org/sparql").connection
    } else {
        rdfRepository.repository.connection
    }


    connection.use { conn: RepositoryConnection ->
        val allCompoundsSubjects =
            (conn.getStatements(null, Wikidata.Properties.instanceOf, WikidataChemistry.chemicalCompound) +
                conn.getStatements(null, Wikidata.Properties.instanceOf, WikidataChemistry.chemicalEntity) +
                conn.getStatements(null, Wikidata.Properties.instanceOf, WikidataChemistry.groupOfStereoIsomers))
                .map { it.subject }

        val allTaxaSubjects = mutableSetOf<Resource>()

        val finalCompounds = allCompoundsSubjects.mapNotNull { compound ->
            try {
                val taxa =
                    conn.getStatements(compound, WikidataTaxonomy.Properties.foundInTaxon, null)
                        .mapNotNull { if (it.`object` is Resource) it.`object` as Resource else null }
                allTaxaSubjects.addAll(taxa)
                Compound(
                    wikidataId = compound.toString(),
                    inchiKey = conn.getBestInchiKey(compound),
                    inchi = conn.getSingleObjectOrFail(compound, WikidataChemistry.Properties.inchi)
                        .toString(),
                    canonicalSmiles = conn.getSingleObjectOrFail(
                        compound,
                        WikidataChemistry.Properties.canonicalSmiles
                    ).toString(),
                    isomericSmiles = conn.getSingleObjectOrFail(
                        compound,
                        WikidataChemistry.Properties.isomericSmiles
                    ).toString(),
                    foundInTaxon = taxa.map { it.toString() }
                )
            } catch (e: CardinalityException) {
                logger.error(e.message)
                null
            }
        }

        val finalTaxa = allTaxaSubjects.mapNotNull { taxon ->
            try {
                Taxon(
                    wikiDataId = taxon.toString(),
                    name = conn.getSingleObjectOrFail(taxon, WikidataTaxonomy.Properties.taxonName)?.toString()
                )
            } catch (e: CardinalityException) {
                logger.error(e.message)
                null
            }
        }.also { it.take(10).forEach { println(it)}}

        logger.info("We have ${finalCompounds.size} compounds usable.")
        logger.info("We have ${finalTaxa.size} taxa usable.")
    }
}
