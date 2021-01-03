/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Copyright (c) 2021
 */

package net.nprod.wikidataLotusExporter.modes.export.formats

import net.nprod.wikidataLotusExporter.modes.export.types.doWithEachTaxon
import org.eclipse.rdf4j.repository.Repository
import java.io.File

fun taxonListToFlatTSV(repository: Repository, file: File) {
    writeTSVFileWith(file, "wikidataId", "names_pipe_separated", "rank", "parent_pipe_separated") {
        doWithEachTaxon(repository) { taxon ->
            taxon.parents.forEach { parent ->
                writeRow(
                    taxon.wikidataId,
                    taxon.names.joinToString("|"),
                    taxon.rank ?: "unspecified",
                    parent
                )
            }
        }
    }
}
