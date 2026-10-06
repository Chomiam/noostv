package io.noostv.data.parser

import io.noostv.data.model.EpgProgram
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Analyseur XMLTV EPG haute performance pour le Guide TV de NoosTV.
 * Utilise DocumentBuilderFactory standard compatible JVM et Android pour préserver la portabilité.
 */
class XmlTvParser {

    private val dateFormat = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)

    fun parse(inputStream: InputStream): List<EpgProgram> {
        val programs = mutableListOf<EpgProgram>()
        try {
            val factory = DocumentBuilderFactory.newInstance()
            // Désactiver les entités externes pour sécurité XML (OWASP XXE protection)
            factory.isExpandEntityReferences = false
            try {
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            } catch (e: Exception) {
                // Ignore si la fonctionnalité n'est pas supportée par l'implémentation
            }

            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(inputStream)
            doc.documentElement.normalize()

            val nodeList = doc.getElementsByTagName("programme")
            for (i in 0 until nodeList.length) {
                val node = nodeList.item(i)
                if (node.nodeType == Node.ELEMENT_NODE) {
                    val element = node as Element

                    val channelId = element.getAttribute("channel")
                    val startStr = element.getAttribute("start")
                    val stopStr = element.getAttribute("stop")

                    val titleNode = element.getElementsByTagName("title").item(0)
                    val title = titleNode?.textContent?.trim()

                    val descNode = element.getElementsByTagName("desc").item(0)
                    val desc = descNode?.textContent?.trim()

                    val catNode = element.getElementsByTagName("category").item(0)
                    val category = catNode?.textContent?.trim()

                    val iconNode = element.getElementsByTagName("icon").item(0) as? Element
                    val iconUrl = iconNode?.getAttribute("src")

                    if (!channelId.isNullOrBlank() && !title.isNullOrBlank()) {
                        val startEpoch = parseDateToEpoch(startStr)
                        val stopEpoch = parseDateToEpoch(stopStr)

                        programs.add(
                            EpgProgram(
                                id = UUID.randomUUID().toString(),
                                channelId = channelId,
                                title = title,
                                description = desc,
                                startEpochMs = startEpoch,
                                stopEpochMs = stopEpoch,
                                category = category,
                                iconUrl = iconUrl,
                                hasCatchup = true
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Log or fallback
        }

        return programs
    }

    private fun parseDateToEpoch(rawDate: String?): Long {
        if (rawDate.isNullOrBlank()) return 0L
        return try {
            val cleanDate = rawDate.trim()
            val parsed = if (cleanDate.contains(" ")) {
                dateFormat.parse(cleanDate)
            } else {
                val fallbackFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
                fallbackFormat.parse(cleanDate)
            }
            parsed?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}
