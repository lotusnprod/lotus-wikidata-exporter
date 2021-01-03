/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Copyright (c) 2021
 */

package net.nprod.wikidataLotusExporter.modes.export.types

import net.nprod.wikidataLotusExporter.lotus.models.Reference
import net.nprod.wikidataLotusExporter.rdf.vocabulary.Wikidata
import net.nprod.wikidataLotusExporter.rdf.vocabulary.WikidataBibliography
import org.eclipse.rdf4j.IsolationLevels
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.repository.RepositoryConnection

fun doWithEachReference(
    repository: Repository,
    f: (Reference) -> Unit
) {
    repository.connection.use { conn: RepositoryConnection ->
        conn.begin(IsolationLevels.NONE) // We are not writing anything
        conn.prepareTupleQuery(
            """
            SELECT ?article_id ?doi {
                ?article_id <${Wikidata.Properties.instanceOf}> ?type;
                            <${WikidataBibliography.Properties.doi}> ?doi.
            }
            """.trimIndent()
        ).evaluate().map { bindingSet ->
            bindingSet.getValue("article_id").stringValue() to bindingSet.getValue("doi").stringValue()
        }.groupBy { it.first }.forEach { (key, value) ->
            f(
                Reference(
                    wikidataId = key,
                    dois = value.map { it.second }
                )
            )
        }
        conn.commit()
    }
}
