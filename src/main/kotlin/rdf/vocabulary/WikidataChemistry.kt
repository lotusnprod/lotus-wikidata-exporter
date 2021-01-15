/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Copyright (c) 2021
 */

package net.nprod.wikidataLotusExporter.rdf.vocabulary

object WikidataChemistry {
    object Properties {
        val foundInTaxon = Wikidata.wdt("P703")
        val isomericSmiles = Wikidata.wdt("P2017")
        val canonicalSmiles = Wikidata.wdt("P233")
        val inchi = Wikidata.wdt("P234")
        val inchiKey = Wikidata.wdt("P235")
    }

    val chemicalCompound = Wikidata.wd("Q11173")
    val chemicalEntity = Wikidata.wd("Q43460564")
    val groupOfStereoIsomers = Wikidata.wd("Q59199015")
}
