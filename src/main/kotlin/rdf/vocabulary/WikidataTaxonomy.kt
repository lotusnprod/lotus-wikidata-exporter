/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Copyright (c) 2021
 */

package net.nprod.wikidataLotusExporter.rdf.vocabulary

object WikidataTaxonomy {
    object Properties {
        val taxonName = Wikidata.wdt("P225")
        val taxonRank = Wikidata.wdt("P105")
        val parentTaxon = Wikidata.wdt("P171")
        val parentTaxonChain = "<" + Wikidata.p("P171") + ">/<" + Wikidata.ps("P171") + ">"
    }
    val taxon = Wikidata.wd("Q16521")
}
