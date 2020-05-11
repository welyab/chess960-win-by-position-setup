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

class PositionException(message: String, cause: Throwable? = null) : ChessException(message, cause)

enum class Position(
    val row: Int,
    val column: Int
) {
    A1(7, 0),
    A2(6, 0),
    A3(5, 0),
    A4(4, 0),
    A5(3, 0),
    A6(2, 0),
    A7(1, 0),
    A8(0, 0),

    B1(7, 1),
    B2(6, 1),
    B3(5, 1),
    B4(4, 1),
    B5(3, 1),
    B6(2, 1),
    B7(1, 1),
    B8(0, 1),

    C1(7, 2),
    C2(6, 2),
    C3(5, 2),
    C4(4, 2),
    C5(3, 2),
    C6(2, 2),
    C7(1, 2),
    C8(0, 2),

    D1(7, 3),
    D2(6, 3),
    D3(5, 3),
    D4(4, 3),
    D5(3, 3),
    D6(2, 3),
    D7(1, 3),
    D8(0, 3),

    E1(7, 4),
    E2(6, 4),
    E3(5, 4),
    E4(4, 4),
    E5(3, 4),
    E6(2, 4),
    E7(1, 4),
    E8(0, 4),

    F1(7, 5),
    F2(6, 5),
    F3(5, 5),
    F4(4, 5),
    F5(3, 5),
    F6(2, 5),
    F7(1, 5),
    F8(0, 5),

    G1(7, 6),
    G2(6, 6),
    G3(5, 6),
    G4(4, 6),
    G5(3, 6),
    G6(2, 6),
    G7(1, 6),
    G8(0, 6),

    H1(7, 7),
    H2(6, 7),
    H3(5, 7),
    H4(4, 7),
    H5(3, 7),
    H6(2, 7),
    H7(1, 7),
    H8(0, 7);

    val file get() = columnToFile(column)
    val rank get() = rowToRank(row)

    fun getSanNotation() = "$file$rank"

    override fun toString() = getSanNotation()

    companion object {

        private val positionsCache = arrayOf(
            arrayOf(A8, B8, C8, D8, E8, F8, G8, H8),
            arrayOf(A7, B7, C7, D7, E7, F7, G7, H7),
            arrayOf(A6, B6, C6, D6, E6, F6, G6, H6),
            arrayOf(A5, B5, C5, D5, E5, F5, G5, H5),
            arrayOf(A4, B4, C4, D4, E4, F4, G4, H4),
            arrayOf(A3, B3, C3, D3, E3, F3, G3, H3),
            arrayOf(A2, B2, C2, D2, E2, F2, G2, H2),
            arrayOf(A1, B1, C1, D1, E1, F1, G1, H1)
        )

        fun fileToColumn(file: Char): Int =
            if (file in 'a'..'h') file - 'a'
            else throw PositionException("Invalid file: $file")

        fun columnToFile(column: Int): Char =
            if (column in 0..7) 'a' + column
            else throw PositionException("Invalid column: $column")

        fun rankToRow(rank: Int) =
            if (rank in 1..8) 8 - rank
            else throw PositionException("Invalid rank: $rank")

        fun rowToRank(row: Int) =
            if (row in 0..7) 8 - row
            else throw PositionException("Invalid row: $row")

        fun from(sanPosition: String) =
            if (sanPosition.length == 2) try {
                from(sanPosition[0], sanPosition[1])
            } catch (e: PositionException) {
                throw PositionException("Invalid SAN position: $sanPosition", e)
            }
            else throw PositionException("Invalid SAN position: $sanPosition")

        private fun from(file: Char, rank: Char) = from(file, rank - '0')

        fun from(file: Char, rank: Int) = try {
            positionsCache[rankToRow(rank)][fileToColumn(file)]
        } catch (e: IndexOutOfBoundsException) {
            throw PositionException("Invalid position [file = $file, rank = $rank]", e)
        }

        fun from(row: Int, column: Int) = try {
            positionsCache[row][column]
        } catch (e: IndexOutOfBoundsException) {
            throw PositionException("Invalid position [row = $row, column = $column]", e)
        }
    }
}
