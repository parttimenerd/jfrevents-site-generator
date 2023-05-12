package me.bechberger.collector.site

import com.github.mustachejava.util.DecoratedCollection
import me.bechberger.collector.xml.AbstractType
import me.bechberger.collector.xml.Event
import me.bechberger.collector.xml.Example
import me.bechberger.collector.xml.FieldType
import me.bechberger.collector.xml.Loader
import me.bechberger.collector.xml.Type
import me.bechberger.collector.xml.XmlContentType
import me.bechberger.collector.xml.XmlType
import org.eclipse.jdt.internal.compiler.parser.Parser.name
import java.net.URL
import java.nio.file.Path
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.TreeMap
import kotlin.io.path.exists

/**
 * @param fileNamePrefix the prefix for all but the index.html, the latter is named prefix.html
 */
class Main(
    val target: Path,
    val resourceFolder: Path? = null,
    val fileNamePrefix: String = "",
) {

    val versions = Loader.getVersions()
    val versionToFileName = versions.associateWithTo(TreeMap<Int, String>()) {
        if (it != versions.last() - 1) {
            "$fileNamePrefix$it.html"
        } else {
            if (fileNamePrefix != "") "$fileNamePrefix.html" else "index.html"
        }
    }

    /** LTS, last and current, the only versions that anyone would work with */
    val relevantVersions = versions.filter { it in setOf(11, 17, 21, 25, versions.last(), versions.last() - 1) }
    val templating: Templating = Templating(resourceFolder)

    init {
        target.toFile().mkdirs()
        downloadBootstrapIfNeeded()
        templating.copyFromResources(target.resolve("css/style.css"), "template/style.css")
        templating.copyFromResources(target.resolve("img/sapmachine.svg"), "template/sapmachine.svg")
        downloadDependendenciesIfNeeded()
    }

    private fun downloadDependendenciesIfNeeded() {
        FILES_TO_DOWNLOAD.forEach { (url, targetFile) ->
            val path = target.resolve(targetFile).toFile()
            path.parentFile.mkdirs()
            URL(url).openStream().use { stream ->
                path.outputStream().use {
                    it.write(stream.readBytes())
                }
            }
        }
    }

    private fun downloadBootstrapIfNeeded() {
        if (!target.resolve("bootstrap").exists()) {
            URL(
                "https://github.com/twbs/bootstrap/releases/download/v$BOOTSTRAP_VERSION/" + "bootstrap-$BOOTSTRAP_VERSION-dist.zip",
            ).openStream().use { stream ->
                target.resolve("bootstrap.zip").toFile().outputStream().use {
                    it.write(stream.readBytes())
                }
            }
            Runtime.getRuntime().exec(
                arrayOf(
                    "unzip",
                    target.resolve("bootstrap.zip").toString(),
                    "-d",
                    target.toString(),
                ),
            ).waitFor()
            target.resolve("bootstrap-$BOOTSTRAP_VERSION-dist").toFile().renameTo(target.resolve("bootstrap").toFile())
            target.resolve("bootstrap.zip").toFile().delete()
        }
    }

    inner class SupportedRelevantJDKScope(val version: Int, val file: String) {
        constructor(version: Int) : this(version, versionToFileName[version]!!)
    }

    data class SupportedRelevantJDKsScope(
        val versions: List<SupportedRelevantJDKScope>,
        val removedIn: SupportedRelevantJDKScope?,
        /** X is in all relevant JDKs <since>+ */
        val since: SupportedRelevantJDKScope?,
    )

    fun List<Int>.isSubList(other: List<Int>) = other.isNotEmpty() && subList(indexOf(other.first()), size) == other

    fun List<Int>.toSupportedRelevantJDKScopes(shorten: Boolean): SupportedRelevantJDKsScope {
        val relVersions = filter { it in relevantVersions }
        val relevantSinceVersion = if (relevantVersions.isSubList(relVersions)) relVersions.first() else null
        val sinceVersion = if (versions.isSubList(this)) first() else relevantSinceVersion
        val supportedRelevantJDKsScope = SupportedRelevantJDKsScope(
            (
                (if (sinceVersion != null && sinceVersion != relVersions.first()) listOf(sinceVersion) else listOf()) +
                    relVersions
                ).map {
                SupportedRelevantJDKScope(
                    it,
                )
            },
            if (last() != versions.last()) {
                SupportedRelevantJDKScope(
                    last() + 1,
                    versionToFileName[last() + 1]!!,
                )
            } else {
                null
            },
            if (shorten && sinceVersion != null) {
                SupportedRelevantJDKScope(sinceVersion)
            } else {
                null
            },
        )
        return supportedRelevantJDKsScope
    }

    fun formatJDKBadges(jdks: List<Int>, shorten: Boolean) =
        if (!shorten || !relevantVersions.all { it in jdks }) {
            templating.template(
                "jdk_badges.html",
                jdks.toSupportedRelevantJDKScopes(shorten),
            )
        } else {
            ""
        }

    class InfoScope(
        val version: Int,
        val isCurrent: Boolean,
        val year: Int,
        val versions: Array<VersionToFile>,
        val tag: String,
        val date: String,
    )

    data class MainScope(
        val info: InfoScope,
        val inner: List<String>,
    )

    data class VersionToFile(
        val version: Int,
        val fileName: String,
        val isCurrent: Boolean,
        val isBeta: Boolean,
        val isRelevant: Boolean,
    )

    fun createPage(version: Int) {
        val metadata = Loader.loadVersion(version)
        println("${metadata.events.size} events (${metadata.events.count { it.examples.isNotEmpty() }} have examples)")
        val infoScope = InfoScope(
            version,
            version == versions.last(),
            LocalDate.now().year,
            versionToFileName.map {
                VersionToFile(
                    it.key,
                    it.value,
                    it.key == version,
                    it.key == versions.last() - 1,
                    relevantVersions.contains(it.key),
                )
            }.toTypedArray(),
            Loader.getSpecificVersion(version),
            LocalDate.ofInstant(Loader.getCreationDate(), ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("dd-MMMM-yyyy")),
        )
        val html = templating.template(
            "main.html",
            MainScope(
                infoScope,
                body(metadata, infoScope),
            ),
        )
        target.resolve(versionToFileName[version]!!).toFile().writeText(html)
    }

    fun create() {
        versions.forEach { createPage(it) }
    }

    data class SectionEntryScope(val name: String, val inner: String)

    data class SectionScope(val title: String, val entries: DecoratedCollection<SectionEntryScope>)

    data class Flags(
        val isEnabled: Boolean,
        val isExperimental: Boolean,
        val isInternal: Boolean,
        val throttle: Boolean,
        val cutoff: Boolean,
        val enabledInConfigs: List<String>,
        val hasStartTime: Boolean,
        val hasDuration: Boolean,
        val hasStackTrace: Boolean,
        val hasThread: Boolean,
        val period: String?,
    )

    data class TypeDescriptorScope(val name: String, val description: String? = null, val link: String? = null)

    data class FieldScope(
        val name: String,
        val type: TypeDescriptorScope,
        val contentType: TypeDescriptorScope?,
        val struct: Boolean,
        val array: Boolean,
        val experimental: Boolean,
        val transition: String?,
        val jdkBadges: String,
        val label: String,
        val description: String?,
        val additionalDescription: String?,
        val descriptionMissing: Boolean,
    )

    fun AbstractType<*>?.toLink(): String? {
        return this?.name?.let { name ->
            val link = name.replace(" ", "_").lowercase()
            if (this is Type) {
                "#$link"
            } else {
                "#$link"
            }
        }
    }

    /** for XMLType and Type */
    fun createTypeDescriptorScope(metadata: me.bechberger.collector.xml.Metadata, type: String): TypeDescriptorScope {
        return (
            metadata.getSpecificType(type, metadata.types) ?: metadata.getSpecificType(
                type,
                metadata.xmlTypes,
            )
            )?.let {
            TypeDescriptorScope(
                it.name,
                null,
                it.toLink(),
            )
        } ?: TypeDescriptorScope(type)
    }

    fun createContentTypeDescriptorScope(
        metadata: me.bechberger.collector.xml.Metadata,
        type: String,
    ): TypeDescriptorScope? {
        return metadata.getSpecificType(type, metadata.xmlContentTypes)?.let {
            TypeDescriptorScope(
                it.name,
                null,
                it.toLink(),
            )
        }
    }

    fun createFieldScope(
        metadata: me.bechberger.collector.xml.Metadata,
        field: me.bechberger.collector.xml.Field,
        parent: Type<*>,
    ): FieldScope {
        val descriptionAndLabelLength =
            field.label.length + (field.description?.length ?: 0) + (field.additionalDescription?.length ?: 0)
        return FieldScope(
            field.name,
            createTypeDescriptorScope(metadata, field.type),
            field.contentType?.let { createContentTypeDescriptorScope(metadata, it) },
            field.struct,
            field.array,
            field.experimental,
            field.transition.toString().lowercase(),
            if (parent.jdks != field.jdks) formatJDKBadges(field.jdks, shorten = true) else "",
            field.label,
            field.description,
            field.additionalDescription,
            descriptionAndLabelLength - 4 < field.name.length && field.type[0].isUpperCase(),
        )
    }

    /** the different types for XMLTypes */
    data class TypesTableScope(
        val parameterType: String = "",
        val fieldType: String = "",
        val javaType: String = "",
        val contentType: String = "",
        val contentTypeLink: String = "",
    )

    fun createTypeTableScope(
        metadata: me.bechberger.collector.xml.Metadata,
        type: XmlType,
    ): TypesTableScope {
        return TypesTableScope(
            type.parameterType,
            type.fieldType,
            type.javaType ?: "",
            type.contentType ?: "",
            type.contentType?.let { contentType ->
                metadata.getSpecificType(contentType, metadata.xmlContentTypes).toLink()
            } ?: "",
        )
    }

    /** AbstractType and Type */
    data class TypeScope(
        val name: String,
        val label: String,
        val additionalDescription: String?,
        val descriptionMissing: Boolean,
        val jdkBadges: String,
        val fields: String?,
        val appearsIn: String,
        val examples: String,
        val typesTable: String?,
        val unsigned: Boolean,
        val annotation: String?,
    )

    fun formatTypesTable(metadata: me.bechberger.collector.xml.Metadata, type: XmlType): String {
        return templating.template(
            "types_table.html",
            createTypeTableScope(metadata, type),
        )
    }

    fun createTypeScope(metadata: me.bechberger.collector.xml.Metadata, type: AbstractType<*>): TypeScope {
        val descriptionAndLabelLength = type.label.length + (type.additionalDescription?.length ?: 0)
        return TypeScope(
            type.name,
            type.label,
            type.additionalDescription,
            descriptionAndLabelLength - 4 < type.name.length && type.name[0].isUpperCase(),
            formatJDKBadges(type.jdks, shorten = true),
            if (type is Type<*>) formatFields(metadata, type) else null,
            formatAppearsIn(metadata, type),
            formatTypeExamples(metadata, type),
            if (type is XmlType) formatTypesTable(metadata, type) else null,
            type is XmlType && (type.unsigned ?: false),
            if (type is XmlContentType) type.annotation else null,
        )
    }

    fun formatType(metadata: me.bechberger.collector.xml.Metadata, type: AbstractType<*>): String {
        return templating.template(
            "type.html",
            createTypeScope(metadata, type),
        )
    }

    data class AppearsInScope(
        val appearsIn: DecoratedCollection<String>,
        val missesIn: DecoratedCollection<String>,
        val show: Boolean,
    )

    fun createAppearsInScope(metadata: me.bechberger.collector.xml.Metadata, type: AbstractType<*>): AppearsInScope {
        val appearsIn = type.appearedIn.map { file -> metadata.getExampleName(file) }.toSet()
        val missesIn = metadata.exampleFiles.map { it.label }.toSet() - appearsIn

        return AppearsInScope(
            DecoratedCollection(
                appearsIn.sorted(),
            ),
            DecoratedCollection(
                missesIn.sorted(),
            ),
            show = missesIn.isNotEmpty() && appearsIn.isNotEmpty(),
        )
    }

    fun formatAppearsIn(metadata: me.bechberger.collector.xml.Metadata, type: AbstractType<*>): String {
        val appearsInScope = createAppearsInScope(metadata, type)
        return templating.template(
            "appears_in.html",
            appearsInScope,
        )
    }

    data class ExampleScope(val name: String, val content: String = "")

    data class ExamplesScope(val id: String, val examples: DecoratedCollection<ExampleScope>)

    data class TypeExamplesScope(
        val examples: String,
        val hasExamples: Boolean,
        val exampleSize: Int,
    )

    data class SimpleTypeLinkScope(val name: String, val link: String? = null)

    data class ObjectExampleEntryScope(
        val key: String,
        val value: String,
        val type: SimpleTypeLinkScope? = null,
        val contentType: SimpleTypeLinkScope? = null,
    )

    data class ExampleEntryScope(
        val depth: Int,
        val firstComplex: Boolean,
        val isTruncated: Boolean,
        var isNull: Boolean = false,
        var stringValue: String? = null,
        var arrayValue: DecoratedCollection<String>? = null,
        var objectValue: DecoratedCollection<ObjectExampleEntryScope>? = null,
    )

    fun formatExample(
        metadata: me.bechberger.collector.xml.Metadata,
        example: Example,
        depth: Int = 0,
        firstComplex: Boolean = true,
    ): String {
        return templating.template(
            "example_entry.html",
            when (example.type) {
                FieldType.STRING -> ExampleEntryScope(depth, false, example.isTruncated).also {
                    it.stringValue = example.stringValue
                }
                FieldType.ARRAY -> ExampleEntryScope(depth, firstComplex, example.isTruncated).also {
                    example.arrayValue?.let { array ->
                        it.arrayValue = DecoratedCollection(
                            array.map { elem ->
                                formatExample(
                                    metadata,
                                    elem,
                                    depth + 1,
                                    false,
                                )
                            },
                        )
                    } ?: run {
                        it.isNull = true
                    }
                }
                FieldType.OBJECT -> ExampleEntryScope(depth, firstComplex, example.isTruncated).also {
                    example.objectValue?.let { obj ->
                        it.objectValue =
                            DecoratedCollection(
                                obj.entries.sortedBy { e -> e.key }.map { (k, v) ->
                                    val type =
                                        v.typeName?.let { t -> SimpleTypeLinkScope(t, metadata.getType(t).toLink()) }
                                    val contentType = v.contentTypeName?.let { c ->
                                        SimpleTypeLinkScope(
                                            c,
                                            metadata.getSpecificType(c, metadata.xmlContentTypes).toLink(),
                                        )
                                    }
                                    ObjectExampleEntryScope(
                                        k,
                                        formatExample(metadata, v, depth + 1, false),
                                        type,
                                        contentType,
                                    )
                                },
                            )
                    } ?: run {
                        it.isNull = true
                    }
                }
                FieldType.NULL -> ExampleEntryScope(depth, false, example.isTruncated).also { it.isNull = true }
            },
        )
    }

    fun createExampleScope(
        metadata: me.bechberger.collector.xml.Metadata,
        example: Example,
    ): ExampleScope {
        return ExampleScope(metadata.getExampleName(example.exampleFile), formatExample(metadata, example))
    }

    data class EventScope(
        val name: String,
        val categories: DecoratedCollection<String>,
        val description: String?,
        val additionalDescription: String?,
        val flags: Flags,
        val source: String?,
        val configurations: ConfigurationScope?,
        val jdkBadges: String,
        val fields: String,
        val descriptionMissing: Boolean,
        val appearsIn: String,
        val examples: String,
    )

    data class ConfigurationScope(
        val headers: List<String>,
        val lastHeader: String,
        val rows: List<ConfigurationRowScope>,
    )

    data class ConfigurationRowScope(val name: String, val cells: List<String>)

    fun Event.topLevelCategory(): String {
        val cats = categories()
        if (cats.first() == "Java Virtual Machine") {
            if (cats.size > 1) {
                if (cats[1] == "GC" && cats.size > 2) {
                    return "JVM: GC: ${cats[2]}"
                }
                return "JVM: ${cats[1]}"
            }
            return "JVM"
        }
        if (cats.first() == "Java Application" && cats.last() == "Statistics") {
            return "Java Application Statistics"
        }
        return cats.first()
    }

    fun groupEventsByTopLevelCategory(metadata: me.bechberger.collector.xml.Metadata): List<Pair<String, List<Event>>> {
        val sections = metadata.events.groupBy { it.topLevelCategory() }
        return sections.map { (section, events) ->
            section to events
        }.sortedBy { it.first }
    }

    fun me.bechberger.collector.xml.Metadata.getConfigurationName(id: Int): String {
        return when (configurations[id].label) {
            "Profiling" -> "profiling"
            "Continuous" -> "default"
            else -> configurations[id].label
        }
    }

    fun me.bechberger.collector.xml.Metadata.getExampleName(id: Int): String {
        return exampleFiles[id].label
    }

    fun createConfigurationScope(
        metadata: me.bechberger.collector.xml.Metadata,
        event: Event,
    ): ConfigurationScope? {
        val configs = event.configurations
        if (configs.isEmpty()) {
            return null
        }
        val configEntryNames = configs.flatMap { it.settings.map { it.name } }.distinct().sorted()
        return ConfigurationScope(
            configEntryNames.dropLast(1),
            configEntryNames.last(),
            configs.map { config ->
                ConfigurationRowScope(
                    metadata.getConfigurationName(config.id) + " " + (
                        if (config.jdks != event.jdks) {
                            formatJDKBadges(
                                config.jdks,
                                shorten = true,
                            )
                        } else {
                            ""
                        }
                        ),
                    configEntryNames.map { name ->
                        config.settings.find { it.name == name }
                            ?.let {
                                it.value + " " + (
                                    if (it.jdks != config.jdks) {
                                        formatJDKBadges(
                                            it.jdks,
                                            shorten = true,
                                        )
                                    } else {
                                        ""
                                    }
                                    )
                            } ?: ""
                    },
                )
            },
        )
    }

    fun formatFields(metadata: me.bechberger.collector.xml.Metadata, type: Type<*>): String {
        if (type.fields.isEmpty()) {
            return ""
        }
        return templating.template(
            "fields.html",
            mutableMapOf("fields" to type.fields.map { createFieldScope(metadata, it, type) }),
        )
    }

    fun formatExamples(metadata: me.bechberger.collector.xml.Metadata, type: AbstractType<*>): String {
        if (type.examples.isEmpty()) {
            return ""
        }
        return templating.template(
            "examples.html",
            ExamplesScope(
                type.name + type.javaClass.name.length,
                DecoratedCollection(type.examples.map { createExampleScope(metadata, it) }),
            ),
        )
    }

    fun formatTypeExamples(metadata: me.bechberger.collector.xml.Metadata, type: AbstractType<*>): String {
        if (type.examples.isEmpty()) {
            return ""
        }
        return templating.template(
            "type_examples.html",
            TypeExamplesScope(
                examples = formatExamples(metadata, type),
                exampleSize = type.examples.size,
                hasExamples = type.examples.size > 0,
            ),
        )
    }

    fun createEventScope(metadata: me.bechberger.collector.xml.Metadata, event: Event): EventScope {
        return EventScope(
            name = event.name,
            categories = DecoratedCollection(event.categories()),
            description = event.description,
            additionalDescription = event.additionalDescription,
            flags = Flags(
                isEnabled = event.enabled,
                isExperimental = event.experimental,
                isInternal = event.internal,
                throttle = event.throttle,
                cutoff = event.cutoff,
                enabledInConfigs = event.configurations.filter { conf ->
                    conf.settings.find { it.name == "enabled" }?.let { it.value != "false" } ?: true
                }.map { metadata.getConfigurationName(it.id) },
                hasStartTime = event.startTime,
                hasDuration = event.duration,
                hasThread = event.thread,
                hasStackTrace = event.stackTrace,
                period = event.period?.name?.replace("_", " ")?.lowercase(),
            ),
            source = event.source,
            configurations = createConfigurationScope(metadata, event),
            appearsIn = formatAppearsIn(metadata, event),
            jdkBadges = formatJDKBadges(event.jdks, shorten = false),
            fields = formatFields(metadata, event),
            descriptionMissing = event.description.isNullOrBlank() && event.additionalDescription.isNullOrBlank(),
            examples = formatTypeExamples(metadata, event),
        )
    }

    fun formatEvent(metadata: me.bechberger.collector.xml.Metadata, event: Event): String {
        val eventScope = createEventScope(metadata, event)
        return templating.template("event.html", eventScope)
    }

    fun createEventSection(
        metadata: me.bechberger.collector.xml.Metadata,
        title: String,
        events: List<Event>,
    ): SectionScope {
        return SectionScope(
            title,
            DecoratedCollection(
                events.map {
                    SectionEntryScope(it.name, formatEvent(metadata, it))
                },
            ),
        )
    }

    fun createTypeSection(
        metadata: me.bechberger.collector.xml.Metadata,
        title: String,
        types: List<AbstractType<*>>,
    ): SectionScope {
        return SectionScope(
            title,
            DecoratedCollection(
                types.sortedBy { it.name }.map {
                    SectionEntryScope(it.name, formatType(metadata, it))
                },
            ),
        )
    }

    fun body(metadata: me.bechberger.collector.xml.Metadata, infoScope: InfoScope): List<String> {
        val sections: MutableList<String> = mutableListOf(templating.template("intro.html", infoScope))
        sections.addAll(
            groupEventsByTopLevelCategory(metadata).map { (title, events) ->
                templating.template(
                    "section.html",
                    createEventSection(metadata, title, events),
                )
            },
        )
        sections.add(
            templating.template(
                "section.html",
                createTypeSection(metadata, "Types", metadata.types),
            ),
        )
        sections.add(
            templating.template(
                "section.html",
                createTypeSection(metadata, "XML Content Types", metadata.xmlContentTypes),
            ),
        )
        sections.add(
            templating.template(
                "section.html",
                createTypeSection(metadata, "XML Types", metadata.xmlTypes),
            ),
        )
        return sections
    }

    companion object {
        const val BOOTSTRAP_VERSION = "5.2.3"
        val FILES_TO_DOWNLOAD = mapOf(
            "https://raw.githubusercontent.com/afeld/bootstrap-toc/gh-pages/dist/bootstrap-toc.js" to "js/bootstrap-toc.js",
            "https://raw.githubusercontent.com/afeld/bootstrap-toc/gh-pages/dist/bootstrap-toc.css" to "css/bootstrap-toc.css",
            "https://cdn.jsdelivr.net/npm/jquery@3.6.3/dist/jquery.min.js" to "js/jquery.min.js",
            "https://cdn.jsdelivr.net/npm/anchor-js/anchor.min.js" to "js/anchor.min.js",
        )
    }
}

fun main(args: Array<String>) {
    if (args.isEmpty() || args.size > 2) {
        println("Usage: generator <target> [<filename prefix>]")
        return
    }
    if (args.size == 1) {
        Main(Path.of(args[0])).create()
    } else {
        Main(Path.of(args[0]), fileNamePrefix = args[1]).create()
    }
}
