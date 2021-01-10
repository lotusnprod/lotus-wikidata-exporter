/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Copyright (c) 2021
 */

package net.nprod.wikidataLotusExporter.modes.export.types

import net.nprod.wikidataLotusExporter.lotus.models.Taxon
import net.nprod.wikidataLotusExporter.rdf.vocabulary.WikidataTaxonomy
import net.nprod.wikidataLotusExporter.sparql.LOTUSQueries
import org.eclipse.rdf4j.IsolationLevels
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.repository.RepositoryConnection

fun doWithEachTaxon(repository: Repository, f: (Taxon) -> Unit) {
    repository.connection.use { conn: RepositoryConnection ->
        conn.begin(IsolationLevels.NONE) // We are not writing anything
        val query =
            """
            ${LOTUSQueries.prefixes}
            SELECT DISTINCT ?taxon_id ?parent_id ?taxon_name ?taxon_rank {
              ?taxon_id <${WikidataTaxonomy.Properties.taxonName}> ?taxon_name;
              OPTIONAL { ?taxon_id <${WikidataTaxonomy.Properties.taxonRank}>/rdfs:label ?taxon_rank.
                         FILTER (lang(?taxon_rank) = 'en')
              }
              
              OPTIONAL { ?taxon_id ${WikidataTaxonomy.Properties.parentTaxonChain} ?parent_id. }
              
            }
            """.trimIndent()
        conn.prepareTupleQuery(query).evaluate().groupBy { it.getValue("taxon_id").stringValue() }
            .forEach { (key, value) ->
                f(
                    Taxon(
                        wikidataId = key,
                        names = value.mapNotNull { it.getValue("taxon_name")?.stringValue() }.distinct(),
                        rank = value.mapNotNull { it.getValue("taxon_rank")?.stringValue() }.firstOrNull(),
                        parents = value.mapNotNull { it.getValue("parent_id")?.stringValue() }
                    )
                )
            }
        conn.commit()
    }
}
