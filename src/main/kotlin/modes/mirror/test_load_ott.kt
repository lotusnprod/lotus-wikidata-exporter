/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Copyright (c) 2021
 */

package net.nprod.wikidataLotusExporter.modes.mirror

import net.nprod.konnector.otol.OfficialOtolAPI
import net.nprod.konnector.otol.OtolConnector
import net.nprod.wikidataLotusExporter.rdf.RDFRepository
import net.nprod.wikidataLotusExporter.rdf.vocabulary.Lotus
import net.nprod.wikidataLotusExporter.rdf.vocabulary.WikidataChemistry
import net.nprod.wikidataLotusExporter.rdf.vocabulary.WikidataTaxonomy
import java.io.File
import kotlin.time.ExperimentalTime

@ExperimentalTime
@Suppress("LongMethod", "NestedBlockDepth")
fun main() {
    val rdfRepository = RDFRepository(File("data/local_rdf"))
    val otolConnector = OtolConnector(OfficialOtolAPI())
    val wikidataTaxa: Set<TaxonData>
    // Get all the ones without an otolId
    rdfRepository.repository.connection.use {
        val result = it.prepareTupleQuery(
            """
            SELECT DISTINCT ?taxon_id ?taxon_name ?irmng_id ?gbif_id ?ncbi_id ?worms_id {
              ?something <${WikidataChemistry.Properties.foundInTaxon}> ?taxon_id.
              ?taxon_id <${WikidataTaxonomy.Properties.taxonName}> ?taxon_name.
              OPTIONAL { ?taxon_id <${WikidataTaxonomy.Properties.irmngId}> ?irmng_id. }
              OPTIONAL { ?taxon_id <${WikidataTaxonomy.Properties.gbifId}> ?gbif_id. }
              OPTIONAL { ?taxon_id <${WikidataTaxonomy.Properties.ncbiId}> ?ncbi_id. }
              OPTIONAL { ?taxon_id <${WikidataTaxonomy.Properties.wormsId}> ?worms_id. }
              FILTER NOT EXISTS {
                ?taxon_id <${Lotus.Properties.otolID}> ?otolId.
              }
            }
            """.trimIndent()
        ).evaluate()
        wikidataTaxa = result.map {
            val name = it.getValue("taxon_name").stringValue()
            TaxonData(
                name,
                it.getValue("taxon_id").stringValue(),
                it.getValue("irmng_id")?.stringValue(),
                it.getValue("gbif_id")?.stringValue(),
                it.getValue("ncbi_id")?.stringValue(),
                it.getValue("worms_id")?.stringValue(),
            )
        }.toSet()
    }
    println("We have ${wikidataTaxa.size} entries without otolIDs")
    rdfRepository.repository.connection.use { connection ->
        val withIRMNG = wikidataTaxa.filter { it.irmngId != null }.toSet()
        println("We have ${withIRMNG.size} with an IRMNG")

       /* withIRMNG.chunked(1000).forEach { block ->
            otolConnector.tnrs.matchNames(block.map {
                "irmng:" + it.IRMNGId
            }).results.forEach {
                val matched = it.matches.filter {
                    (setOf(
                        "extinct",
                        "merged",
                        "sibling_higher",
                        "incertae_sedis"
                    ).intersect(it.taxon.flags)).isEmpty()
                }
                if (matched.size > 1) {
                    println("${it.name}")
                    matched.forEach {
                        println(" ${it.taxon.ott_id}")
                        println(" ${it.taxon.rank}")
                        println(it.taxon)
                    }
                }
            }
        }*/

        val withGBIF = (wikidataTaxa - withIRMNG).filter { it.gbifId != null }.toSet()
        println("We have ${withGBIF.size} with a GBIF")

        val withNCBI = (wikidataTaxa - withIRMNG - withGBIF).filter { it.ncbiId != null }.toSet()
        println("We have ${withNCBI.size} with a NCBI")

        val withWORMS = (wikidataTaxa - withIRMNG - withGBIF - withNCBI).filter { it.wormsId != null }.toSet()
        println("We have ${withWORMS.size} with a WORMS")

        val withoutId = (wikidataTaxa - withIRMNG - withGBIF - withNCBI - withWORMS)
        println("We have ${withoutId.size} without IDs")
        println(withoutId)
        otolConnector.tnrs.matchNames(withoutId.map { it.name }).results.forEach {
            val matched = it.matches.filter {
                (
                    setOf(
                        "extinct",
                        "merged",
                        "sibling_higher",
                        "incertae_sedis"
                    ).intersect(it.taxon.flags)
                    ).isEmpty()
            }
            if (matched.size > 1) {
                println("${it.name}")
                matched.forEach {
                    println(" ${it.taxon.ott_id}")
                    println(" ${it.taxon.rank}")
                    println(it.taxon)
                }
            }
        }
    }
}
