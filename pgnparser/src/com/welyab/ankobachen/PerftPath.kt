/*
 * Copyright (C) 2020 Welyab da Silva Paula
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.welyab.ankobachen

import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

enum class PerftValue {
    NODES,
    CAPTURES,
    EN_PASSANTS,
    CASTLINGS,
    PROMOTIONS,
    CHECKS,
    DISCOVERIES,
    DOUBLES,
    CHECKMATES,
    STALEMATES
}

class PerftResult private constructor(val fen: String, private val results: Map<Int, MovementMetadata>) {

    fun getMaxDepth() = results.size

    fun getPerftValues(depth: Int) = results[depth]?.let {
        mapOf(
            PerftValue.NODES to it.movementsCount(),
            PerftValue.CAPTURES to it.capturesCount(),
            PerftValue.EN_PASSANTS to it.enPassantsCount(),
            PerftValue.CASTLINGS to it.castlingsCount(),
            PerftValue.PROMOTIONS to it.promotionsCount(),
            PerftValue.CHECKS to it.checksCount(),
            PerftValue.DISCOVERIES to it.discoveriesCount(),
            PerftValue.DOUBLES to it.doublesCount(),
            PerftValue.CHECKMATES to it.checkmatesCount(),
            PerftValue.STALEMATES to it.stalematesCount()
        )
    }!!

    fun getPeftValue(depth: Int, perftValue: PerftValue) = results[depth]?.let {
        when (perftValue) {
            PerftValue.NODES -> it.movementsCount()
            PerftValue.CAPTURES -> it.capturesCount()
            PerftValue.EN_PASSANTS -> it.enPassantsCount()
            PerftValue.CASTLINGS -> it.castlingsCount()
            PerftValue.PROMOTIONS -> it.promotionsCount()
            PerftValue.CHECKS -> it.checksCount()
            PerftValue.DISCOVERIES -> it.discoveriesCount()
            PerftValue.DOUBLES -> it.doublesCount()
            PerftValue.CHECKMATES -> it.checkmatesCount()
            PerftValue.STALEMATES -> it.stalematesCount()
        }
    }!!

    class Builder constructor(
        private val fen: String,
        private val map: HashMap<Int, MovementMetadata.Builder> = HashMap()
    ) {
        fun add(depth: Int, movementMetadata: MovementMetadata): Builder {
            map.computeIfAbsent(depth) { _ ->
                    MovementMetadata.builder()
                }
                .add(movementMetadata)
            return this
        }

        fun builder() = PerftResult(
            fen,
            map.mapValues { it.value.build() }
        )
    }

    override fun toString() = buildString {
        val headers = ArrayList<String>()
        headers += " DEPTH "
        PerftValue.values().forEach { headers += " ${it.name} " }
        val values = ArrayList<ArrayList<String>>()
        for (depth in 1..getMaxDepth()) {
            val list = ArrayList<String>()
            values += list
            list += " $depth "
            for (perftValue in PerftValue.values()) {
                val value = getPeftValue(depth, perftValue).toString()
                list += " $value "
            }
        }
        val rows: ArrayList<ArrayList<String>> = ArrayList<ArrayList<String>>()
        rows += headers
        fun ArrayList<ArrayList<String>>.columnLength(columnIndex: Int) =
            asSequence().map { it[columnIndex].length }.max()!!
        values.forEach { rows += it }

        for (i in 0 until headers.size) {
            if (i == 0) append('┌') else append('┬')
            append("".padStart(rows.columnLength(i), '─'))
        }
        append("┐").append('\n')

        for (rowIndex in 0 until rows.size) {
            rows[rowIndex].forEachIndexed { column, value ->
                append('│').append(rows[rowIndex][column].padStart(rows.columnLength(column), ' '))
            }
            append('│').append('\n')

            if (rowIndex != rows.lastIndex) {
                for (i in 0 until headers.size) {
                    if (i == 0) append('├') else append('┼')
                    append("".padStart(rows.columnLength(i), '─'))
                }
                append("┤").append('\n')
            } else {
                for (i in 0 until headers.size) {
                    if (i == 0) append('└') else append('┴')
                    append("".padStart(rows.columnLength(i), '─'))
                }
                append("┘").append('\n')
            }
        }
    }

    companion object {
        fun builder(fen: String) = Builder(fen)
    }
}

class PerftCalculator(
    val fen: String = FEN_INITIAL,
    val deepth: Int = 2
) {
    private val board = Board(fen)
    private var perftResult: PerftResult? = null

    fun getPerftResult(): PerftResult {
        if (perftResult == null) execute()
        return perftResult!!
    }

    fun execute() {
        if (perftResult != null) return
        val board = Board(fen)
        val builder = PerftResult.builder(fen)
        walker(board, 1, builder)
        perftResult = builder.builder()
    }

    private fun walker(board: Board, currentDepth: Int, builder: PerftResult.Builder) {
        val movements = board.getMovements()
        builder.add(currentDepth, movements.getMetadata())
        for (movement in movements) {
            board.move(movement)
            if (currentDepth + 1 <= deepth) {
                walker(board, currentDepth + 1, builder)
            }
            board.undo()
        }
    }
}

class PathEnumerator(
    val fen: String,
    val enumerationDepth: Int,
    val depth: Int
) {
    fun enumerate() {
        val board = Board(fen)
        enumerate(board, 1, ArrayList())
    }

    private fun enumerate(board: Board, currentDepth: Int, path: ArrayList<PieceMovement>) {
        if (currentDepth > enumerationDepth) {
            path.asSequence()
                .map { "${it.getOrigin().position}${it.getDestination().getTarget().position}" }
                .reduce { s1, s2 -> "$s1 $s2" }
                .apply { print(this) }
            if (currentDepth <= depth) {
                totalizeMovements(board, currentDepth).apply {
                    print(" $this")
                }
            }
            println()
        } else {
            board.getMovements().forEachDestination { pieceMovement ->
                board.move(pieceMovement)
                path += pieceMovement

                if (currentDepth + 1 > enumerationDepth && pieceMovement.getFlags().isDiscovery()) {
                    println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>")
                }

                enumerate(board, currentDepth + 1, path)
                path.removeAt(path.lastIndex)
                board.undo()
            }
        }
    }

    private fun totalizeMovements(board: Board, currentDepth: Int): Long {
        var sum = 0L
        board.getMovements()
            .apply {
                sum += getMetadata().movementsCount()
            }
            .forEachDestination {
                if (currentDepth + 1 <= depth) {
                    board.move(it)
                    sum += totalizeMovements(board, currentDepth + 1)
                    board.undo()
                }
            }
        return sum
    }
}
