/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Copyright (c) 2021
 */

package net.nprod.wikidataLotusExporter.modes.export

import com.univocity.parsers.tsv.TsvWriter
import com.univocity.parsers.tsv.TsvWriterSettings
import net.nprod.wikidataLotusExporter.lotus.models.Compound
import net.nprod.wikidataLotusExporter.lotus.models.CompoundReferenceTaxon
import net.nprod.wikidataLotusExporter.lotus.models.Reference
import net.nprod.wikidataLotusExporter.lotus.models.Taxon
import net.nprod.wikidataLotusExporter.rdf.RDFRepository
import net.nprod.wikidataLotusExporter.rdf.vocabulary.Wikidata
import net.nprod.wikidataLotusExporter.rdf.vocabulary.WikidataBibliography
import net.nprod.wikidataLotusExporter.rdf.vocabulary.WikidataChemistry
import net.nprod.wikidataLotusExporter.rdf.vocabulary.WikidataTaxonomy
import net.nprod.wikidataLotusExporter.sparql.LOTUSQueries
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Resource
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.repository.RepositoryConnection
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository
import org.slf4j.LoggerFactory
import java.io.File

val queryForRefsAndTaxon = """
  ${LOTUSQueries.prefixes}
  SELECT ?taxon_id ?reference_id
  WHERE {
    <<<COMPOUND>>>   p:P703 ?pp703.
    ?pp703           ps:P703 ?taxon_id;
                     prov:wasDerivedFrom/pr:P248 ?reference_id.
  }
  """.trimIndent()

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

fun export(repositoryLocation: File, outputDirectory: File, direct: Boolean) {
    val logger = LoggerFactory.getLogger("export")
    val rdfRepository = RDFRepository(repositoryLocation)

    val connection = if (direct) {
        SPARQLRepository("https://query.wikidata.org/sparql").connection
    } else {
        rdfRepository.repository.connection
    }

    logger.info("Exporting from the repository: $repositoryLocation")

    val compoundReferenceTaxonList = mutableListOf<CompoundReferenceTaxon>()
    val compounds = mutableListOf<Compound>()
    val references = mutableListOf<Reference>()
    val taxa = mutableListOf<Taxon>()


    connection.use { conn: RepositoryConnection ->
        val allCompoundsSubjects =
            (
                conn.getStatements(null, Wikidata.Properties.instanceOf, WikidataChemistry.chemicalCompound) +
                    conn.getStatements(null, Wikidata.Properties.instanceOf, WikidataChemistry.chemicalEntity) +
                    conn.getStatements(null, Wikidata.Properties.instanceOf, WikidataChemistry.groupOfStereoIsomers)
                ).toSet()
                .map { it.subject }

        val allTaxaSubjects = mutableSetOf<Resource>()

        val allReferencesSubjects = mutableSetOf<Resource>()

        allCompoundsSubjects.forEach { compound ->
            conn.prepareTupleQuery(queryForRefsAndTaxon.replace("<<COMPOUND>>", compound.toString())).evaluate().map {
                val taxonId = it.getValue("taxon_id")
                if (taxonId is Resource) allTaxaSubjects.add(taxonId)

                val referenceId = it.getValue("reference_id")
                if (referenceId is Resource) allReferencesSubjects.add(referenceId)

                compoundReferenceTaxonList.add(
                    CompoundReferenceTaxon(
                        compound.toString(),
                        taxonId.toString(),
                        referenceId.toString()
                    )
                )
            }
            try {
                compounds.add(
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
                        ).toString()
                    )
                )
            } catch (e: CardinalityException) {
                logger.error(e.message)
            }
        }

        allTaxaSubjects.forEach { taxon ->
            try {
                taxa.add(Taxon(
                    wikidataId = taxon.toString(),
                    names = conn.getStatements(taxon, WikidataTaxonomy.Properties.taxonName, null).mapNotNull {
                        it.`object`?.toString()
                    }
                ))
            } catch (e: CardinalityException) {
                logger.error(e.message)
            }
        }

        allReferencesSubjects.forEach { reference ->
            try {
                references.add(
                    Reference(
                        wikidataId = reference.toString(),
                        doi = conn.getSingleObjectOrFail(reference, WikidataBibliography.Properties.doi).toString()
                    )
                )
            } catch (e: CardinalityException) {
                logger.error(e.message)
            }
        }

        logger.info("Exporting")
        compoundListToTSV(compounds, File(outputDirectory, "compounds.tsv"))
        referenceListToTSV(references, File(outputDirectory, "references.tsv"))
        taxonListToTSV(taxa, File(outputDirectory,"taxa.tsv"))
        compoundReferenceTaxonListToTSV(compoundReferenceTaxonList, File(outputDirectory, "compound_reference_taxon.tsv"))
        logger.info("Done")
    }
}

fun compoundListToTSV(compoundList: List<Compound>, file: File) {
    val writer = TsvWriter(file.bufferedWriter(), TsvWriterSettings())
    writer.writeHeaders("wikidataId", "canonicalSmiles", "isomericSmiles", "inchi", "inchiKey")
    compoundList.forEach {
        writer.writeRow(it.wikidataId, it.canonicalSmiles, it.isomericSmiles, it.inchi, it.inchiKey)
    }
    writer.close()
}

fun taxonListToTSV(taxonList: List<Taxon>, file: File) {
    val writer = TsvWriter(file.bufferedWriter(), TsvWriterSettings())
    writer.writeHeaders("wikidataId", "names_pipe_separated")
    taxonList.forEach {
        writer.writeRow(it.wikidataId, it.names.joinToString("|"))
    }
    writer.close()
}

fun referenceListToTSV(referenceList: List<Reference>, file: File) {
    val writer = TsvWriter(file.bufferedWriter(), TsvWriterSettings())
    writer.writeHeaders("wikidataId", "doi")
    referenceList.forEach {
        writer.writeRow(it.wikidataId, it.doi)
    }
    writer.close()
}

fun compoundReferenceTaxonListToTSV(compoundReferenceTaxonList: List<CompoundReferenceTaxon>, file: File) {
    val writer = TsvWriter(file.bufferedWriter(), TsvWriterSettings())
    writer.writeHeaders("compound", "reference", "taxon")
    compoundReferenceTaxonList.forEach {
        writer.writeRow(it.compound, it.reference, it.taxon)
    }
    writer.close()
}
