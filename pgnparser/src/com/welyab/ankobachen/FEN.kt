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

class FenException(message: String, cause: Throwable? = null) : ChessException(message, cause)

class CastlingFlags(
    val leftWhiteRook: Position? = null,
    val rightWhiteRook: Position? = null,
    val leftBlackRook: Position? = null,
    val rightBlackRook: Position? = null
)

data class FenInfo(
    val piecesDisposition: List<LocalizedPiece>,
    val sideToMove: Color,
    val castlingFlags: CastlingFlags,
    val epTarget: Position?,
    val halfMoveClock: Int,
    val fullMoveCounter: Int
)

class FenString(val fen: String, val pieceSetup: String = STANDARRD_CHESS_START_POSITION) {

    private var fenInfo: FenInfo? = null

    fun getFenInfo(): FenInfo {
        if (fenInfo != null) return fenInfo!!

        val fenParts = getFenParts()

        fenInfo = FenInfo(
            piecesDisposition = parsePiecesDisposition(fenParts.getOrNull(0)),
            sideToMove = parseSideToMove(fenParts.getOrNull(1)),
            castlingFlags = parseCastlingFlags(fenParts.getOrNull(2)),
            epTarget = parseEpTarget(fenParts.getOrNull(3)),
            halfMoveClock = parseHalfMoveClock(fenParts.getOrNull(4)),
            fullMoveCounter = parseFullMoveCounter(fenParts.getOrNull(5))
        )

        return fenInfo!!
    }

    private fun getFenParts(): List<String> {
        val parts = arrayListOf<String>()
        var startIndex = 0
        var whiteSpace = fen[0].isWhitespace()
        fen.forEachIndexed { index, _ ->
            if (fen[index].isWhitespace() != whiteSpace) {
                parts += fen.substring(startIndex, index)
                startIndex = index
                whiteSpace = !whiteSpace
            }
        }
        parts += fen.substring(startIndex, fen.length)

        if (parts.size % 2 == 0) throw FenException("Invalid fen string: $fen")

        parts.forEachIndexed { index, _ ->
            if (index % 2 == 0 && parts[index].isBlank()
                || index % 2 == 1 && (parts[index].isNotBlank() || parts[index].length != 1)
            ) throw FenException("Invalid fen string: $fen")
        }

        return parts.filter { it.isNotBlank() }
    }

    private fun parsePiecesDisposition(pieceDispositionPart: String?): List<LocalizedPiece> {
        val pieces = arrayListOf<LocalizedPiece>()
        var row = 0
        var column = 0
        pieceDispositionPart!!.forEach { c ->
            when (c) {
                '/' -> {
                    if (row >= 8) throw FenException("Invalid FEN: $fen")
                    row++
                    column = 0
                }
                in '1'..'8' -> {
                    if (column >= 8) throw FenException("Invalid FEN: $fen")
                    column += c - '0'
                }
                else -> {
                    val piece = try {
                        Piece.from(c)
                    } catch (e: PieceException) {
                        throw FenException("Invalid FEN $fen", e)
                    }
                    if (row >= 8 || column >= 8) throw FenException("Invalid FEN: $fen")
                    pieces += LocalizedPiece(piece, Position.from(row, column))
                    column++
                }
            }
        }
        return pieces
    }

    private fun parseSideToMove(sideToMovePart: String?) =
        if (sideToMovePart != null && sideToMovePart.length == 1) try {
            Color.from(sideToMovePart[0])
        } catch (e: ColorException) {
            throw FenException("Invalid fen \"$fen\". Invalid side to move: $sideToMovePart", e)
        }
        else throw FenException("Invalid fen \"$fen\". Invalid side to move: $sideToMovePart")

    private fun parseCastlingFlags(castlingFlagsPart: String?): CastlingFlags {
        if (castlingFlagsPart == null || castlingFlagsPart == "-") return CastlingFlags()

        var index = 0
        var invalidFagsForSetup = !CHESS_960_START_POSITIONS.contains(pieceSetup)
        var leftWhiteRook: Position? = null
        var rightWhiteRook: Position? = null
        var leftBlackRook: Position? = null
        var rightBlackRook: Position? = null

        if (
            !invalidFagsForSetup
            && castlingFlagsPart[index] == FEN_WHITE_KING_SIDE_CASTLING_FLAG
        ) {
            val columnIndex = pieceSetup.lastIndexOf(WHITE_ROOK_LETTER)
            index++

            if (columnIndex >= 0) {
                rightWhiteRook = Position.from(7, columnIndex)
            } else {
                invalidFagsForSetup = true
            }
        }
        if (
            !invalidFagsForSetup
            && index < castlingFlagsPart.length && castlingFlagsPart[index] == FEN_WHITE_QUEEN_SIDE_CASTLING_FLAG
        ) {
            val columnIndex = pieceSetup.indexOf(WHITE_ROOK_LETTER)
            index++

            if (columnIndex >= 0) {
                leftWhiteRook = Position.from(7, columnIndex)
            } else {
                invalidFagsForSetup = true
            }
        }
        if (
            !invalidFagsForSetup
            && index < castlingFlagsPart.length && castlingFlagsPart[index] == FEN_BLACK_KING_SIDE_CASTLING_FLAG
        ) {
            val columnIndex = pieceSetup.lastIndexOf(WHITE_ROOK_LETTER)
            index++

            if (columnIndex >= 0) {
                rightBlackRook = Position.from(0, columnIndex)
            } else {
                invalidFagsForSetup = true
            }
        }
        if (
            !invalidFagsForSetup
            && index < castlingFlagsPart.length && castlingFlagsPart[index] == FEN_BLACK_QUEEN_SIDE_CASTLING_FLAG
        ) {
            val columnIndex = pieceSetup.indexOf(WHITE_ROOK_LETTER)
            index++

            if (columnIndex >= 0) {
                leftBlackRook = Position.from(0, columnIndex)
            } else {
                invalidFagsForSetup = true
            }
        }

        if (invalidFagsForSetup) throw FenException(
            "Invalid fen \"$fen\". Invalid castling flags '$castlingFlagsPart' for piece setup '$pieceSetup'"
        )

        if (index != castlingFlagsPart.length)
            throw FenException("Invalid fen \"$fen\". Invalid castling flags: $castlingFlagsPart")

        return CastlingFlags(
            leftWhiteRook,
            rightWhiteRook,
            leftBlackRook,
            rightBlackRook
        )
    }

    private fun parseEpTarget(epTargetPart: String?): Position? =
        if (epTargetPart == null || epTargetPart == "-") null
        else try {
            Position.from(epTargetPart)
        } catch (e: PositionException) {
            throw FenException("Invalid fen \"$fen\". Invalid en passant target square: $epTargetPart", e)
        }

    private fun parseHalfMoveClock(halfMoveClockPart: String?): Int =
        if (halfMoveClockPart == null || halfMoveClockPart == "-") FEN_DEFAULT_HALF_MOVE_CLOCK
        else try {
            halfMoveClockPart.toInt().apply {
                if (this < 0)
                    throw FenException("Invalid fen \"$fen\". Invalid half move clock: $halfMoveClockPart")
            }
        } catch (e: NumberFormatException) {
            throw FenException("Invalid fen \"$fen\". Invalid half move clock: $halfMoveClockPart", e)
        }

    private fun parseFullMoveCounter(fullMoveCounterPart: String?): Int =
        if (fullMoveCounterPart == null || fullMoveCounterPart == "-") FEN_DEFAULT_FULL_MOVE_COUNTER
        else try {
            fullMoveCounterPart.toInt().apply {
                if (this < 1)
                    throw FenException("Invalid fen \"$fen\". Invalid full move counter: $fullMoveCounterPart")
            }
        } catch (e: NumberFormatException) {
            throw FenException("Invalid fen \"$fen\". Invalid full move counter: $fullMoveCounterPart", e)
        }
}
