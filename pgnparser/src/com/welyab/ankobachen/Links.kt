package com.welyab.ankobachen

import java.nio.file.Files
import java.nio.file.Paths

fun main() {
    Files.lines(Paths.get("C:\\Users\\welyab\\Desktop\\chess\\links.txt"))
        .filter { it.contains("chess960") && it.contains("bz2") }
        .map { it.substring(it.indexOf("\"") + 1, it.lastIndexOf("\"")) }
        .map { "https://database.lichess.org/$it" }
        .forEach { println(it) }
}