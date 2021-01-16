/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Copyright (c) 2021
 */

package net.nprod.wikidataLotusExporter.rdf.vocabulary

import org.eclipse.rdf4j.model.impl.SimpleValueFactory

object Lotus {
    private val simpleValueFactory = SimpleValueFactory.getInstance()
    fun p(localName: String) = simpleValueFactory.createIRI(Lotus.lotusPropertiesPrefix, localName)

    const val lotusPropertiesPrefix = "http://lotus.nprod.net/p/"

    object Properties {
        val otolID = p("P1")
    }
}
