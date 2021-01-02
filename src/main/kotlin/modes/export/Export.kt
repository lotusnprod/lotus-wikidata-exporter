/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Copyright (c) 2021
 */

package net.nprod.wikidataLotusExporter.modes.export

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import net.nprod.wikidataLotusExporter.DEFAULT_REPOSITORY
import java.io.File

class Export : CliktCommand(help = "Export LOTUS to… something") {
    private val store by option("-s", "--store", help = "Where the data is going to be stored")
        .default(DEFAULT_REPOSITORY)
    private val outputFilename by option("-o", "--output", help = "Output file")
    private val direct by option(
        "-d",
        "--direct",
        help = "Connect directly to WikiData, do not use the local instance"
    ).flag("-l", "--local", default = false, defaultForHelp = "Use the local instance")

    override fun run() {
        val storeFile = File(store)
        val outputFile = outputFilename?.let { File(it) }
        export(storeFile, outputFile, direct)
    }
}
