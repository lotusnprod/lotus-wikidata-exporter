/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Copyright (c) 2021
 */

package net.nprod.wikidataLotusExporter.modes.export.types

import net.nprod.wikidataLotusExporter.lotus.models.Taxon
import net.nprod.wikidataLotusExporter.rdf.vocabulary.WikidataTaxonomy
import org.eclipse.rdf4j.IsolationLevels
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.repository.RepositoryConnection

fun doWithEachTaxon(repository: Repository, f: (Taxon) -> Unit) {
    repository.connection.use { conn: RepositoryConnection ->
        conn.begin(IsolationLevels.NONE) // We are not writing anything
        conn.prepareTupleQuery(
            """
            SELECT ?taxon_id ?parent_id ?taxon_name {
              ?taxon_id <${WikidataTaxonomy.Properties.taxonName}> ?taxon_name.
              OPTIONAL { ?taxon_id <${WikidataTaxonomy.Properties.parentTaxon}> ?parent_id. }
            }
            """.trimIndent()
        ).evaluate().groupBy { it.getValue("taxon_id").stringValue() }.forEach { (key, value) ->
            f(
                Taxon(
                    wikidataId = key,
                    names = value.mapNotNull { it.getValue("taxon_name")?.stringValue() },
                    parents = value.mapNotNull { it.getValue("parent_id")?.stringValue() }
                )
            )
        }
        conn.commit()
    }
}
