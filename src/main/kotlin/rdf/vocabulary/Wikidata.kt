package net.nprod.wikidataLotusExporter.rdf.vocabulary

import org.eclipse.rdf4j.model.impl.SimpleValueFactory

object Wikidata {
    private val simpleValueFactory = SimpleValueFactory.getInstance()
    fun wdt(localName: String) = simpleValueFactory.createIRI(wdtPrefix, localName)
    fun wd(localName: String) = simpleValueFactory.createIRI(wdPrefix, localName)

    /**
     * PREFIX wd: <>
    PREFIX wdt: <>
    PREFIX wikibase: <>
    PREFIX p: <>
    PREFIX prov: <>
    PREFIX ps: <>
    PREFIX pq: <>
    PREFIX pr: <>
     */
    val wdPrefix = "http://www.wikidata.org/entity/"
    val wdtPrefix = "http://www.wikidata.org/prop/direct/"
    val wikibasePrefix = "http://wikiba.se/ontology#"
    val pPrefix = "http://www.wikidata.org/prop/"
    val provPrefix = "http://www.w3.org/ns/prov#"
    val psPrefix = "http://www.wikidata.org/prop/statement/"
    val pqPrefix = "http://www.wikidata.org/prop/qualifier/"
    val prPrefix = "http://www.wikidata.org/prop/reference/"

    object Properties {
        val instanceOf = wdt("P31")
    }
}