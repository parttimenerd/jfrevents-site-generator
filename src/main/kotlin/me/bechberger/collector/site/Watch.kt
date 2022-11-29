package me.bechberger.collector.generator

import java.nio.file.Path

fun create(target: Path) {
    try {
        Main(target, Path.of("src/main/resources/")).createPage(19)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun main(args: Array<String>) {
    val templateFolder = Path.of("src/main/resources/template")
    val target = Path.of(args[0])
    var map = templateFolder.toFile().walk().filter { it.isFile }.map { it.toPath() to it.lastModified() }.toMap()
    create(target)
    while (true) {
        val newMap =
            templateFolder.toFile().walk().filter { it.isFile }.map { it.toPath() to it.lastModified() }.toMap()
        if (newMap != map) {
            println("Change detected")
            create(target)
            map = newMap
        }
        Thread.sleep(100)
    }
}
