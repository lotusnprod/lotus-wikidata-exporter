/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Copyright (c) 2021
 */

package net.nprod.wikidataLotusExporter.lotus.models

data class Taxon(
    val wikidataId: String,
    val names: List<String>,
    val rank: String?,
    val parents: List<String>
)
