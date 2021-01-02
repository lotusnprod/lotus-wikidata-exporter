/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Copyright (c) 2021
 */

package net.nprod.wikidataLotusExporter.lotus.models

data class Compound(
    val wikidataId: String,
    val canonicalSmiles: String?,
    val isomericSmiles: String?,
    val inchi: String?,
    val inchiKey: String?,
    val foundInTaxon: List<String>
)
