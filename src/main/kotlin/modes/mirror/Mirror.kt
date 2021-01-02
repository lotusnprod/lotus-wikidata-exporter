/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Copyright (c) 2021
 */

package net.nprod.wikidataLotusExporter.modes.mirror

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import net.nprod.wikidataLotusExporter.DEFAULT_REPOSITORY
import java.io.File

class Mirror : CliktCommand(help = "Mirror Wikidata entries related to LOTUS locally") {
    private val store by option("-s", "--store", help = "Where the data is going to be stored")
        .default(DEFAULT_REPOSITORY)

    override fun run() {
        val storeFile = File(store)
        mirror(storeFile)
    }
}
