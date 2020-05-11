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

class PieceException(message: String, cause: Throwable? = null) : ChessException(message, cause)

enum class PieceType(val letter: Char) {
    KING(KING_LETTER),
    QUEEN(QUEEN_LETTER),
    ROOK(ROOK_LETTER),
    BISHOP(BISHOP_LETTER),
    KNIGHT(KNIGHT_LETTER),
    PAWN(PAWN_LETTER);

    val isKing get() = this == KING
    val isQueen get() = this == QUEEN
    val isRook get() = this == ROOK
    val isBishop get() = this == BISHOP
    val isKnight get() = this == KNIGHT
    val isPawn get() = this == PAWN

    override fun toString() =
        name.toLowerCase().capitalize()

    companion object {
        fun from(letter: Char) =
            when (letter) {
                KING_LETTER -> KING
                QUEEN_LETTER -> QUEEN
                ROOK_LETTER -> ROOK
                BISHOP_LETTER -> BISHOP
                KNIGHT_LETTER -> KNIGHT
                PAWN_LETTER -> PAWN
                else -> throw PieceException("Invalid piece type letter: $letter")
            }

        fun isPieceTypeLetter(letter: Char) =
            values().asSequence().any { it.letter == letter }
    }
}

enum class Piece(
    val type: PieceType,
    val color: Color,
    override val letter: Char
) : SquareContent<Piece> {

    WHITE_KING(PieceType.KING, Color.WHITE, WHITE_KING_LETTER),
    WHITE_QUEEN(PieceType.QUEEN, Color.WHITE, WHITE_QUEEN_LETTER),
    WHITE_ROOK(PieceType.ROOK, Color.WHITE, WHITE_ROOK_LETTER),
    WHITE_BISHOP(PieceType.BISHOP, Color.WHITE, WHITE_BISHOP_LETTER),
    WHITE_KNIGHT(PieceType.KNIGHT, Color.WHITE, WHITE_KNIGHT_LETTER),
    WHITE_PAWN(PieceType.PAWN, Color.WHITE, WHITE_PAWN_LETTER),

    BLACK_KING(PieceType.KING, Color.BLACK, BLACK_KING_LETTER),
    BLACK_QUEEN(PieceType.QUEEN, Color.BLACK, BLACK_QUEEN_LETTER),
    BLACK_ROOK(PieceType.ROOK, Color.BLACK, BLACK_ROOK_LETTER),
    BLACK_BISHOP(PieceType.BISHOP, Color.BLACK, BLACK_BISHOP_LETTER),
    BLACK_KNIGHT(PieceType.KNIGHT, Color.BLACK, BLACK_KNIGHT_LETTER),
    BLACK_PAWN(PieceType.PAWN, Color.BLACK, BLACK_PAWN_LETTER);

    override val isKing = type.isKing
    override val isQueen = type.isQueen
    override val isRook = type.isRook
    override val isBishop = type.isBishop
    override val isKnight = type.isKnight
    override val isPawn = type.isPawn

    override val isWhiteKing get() = this == WHITE_KING
    override val isWhiteQueen get() = this == WHITE_QUEEN
    override val isWhiteRook get() = this == WHITE_ROOK
    override val isWhiteBishop get() = this == WHITE_BISHOP
    override val isWhiteKnight get() = this == WHITE_KNIGHT
    override val isWhitePawn get() = this == WHITE_PAWN

    override val isBlackKing get() = this == BLACK_KING
    override val isBlackQueen get() = this == BLACK_QUEEN
    override val isBlackRook get() = this == BLACK_ROOK
    override val isBlackBishop get() = this == BLACK_BISHOP
    override val isBlackKnight get() = this == BLACK_KNIGHT
    override val isBlackPawn get() = this == BLACK_PAWN

    override val isWhite = color.isWhite
    override val isBlack = color.isBlack

    override val isEmpty = false
    override val isNotEmpty = true

    override fun isColorOf(color: Color) = this.color == color

    override fun isPieceOf(piece: Piece) = this == piece

    override fun copy() = this

    override fun asPiece() = this

    override fun toString() = "$color $type"

    companion object {

        fun from(letter: Char) = when (letter) {
            WHITE_KING_LETTER -> WHITE_KING
            WHITE_QUEEN_LETTER -> WHITE_QUEEN
            WHITE_ROOK_LETTER -> WHITE_ROOK
            WHITE_BISHOP_LETTER -> WHITE_BISHOP
            WHITE_KNIGHT_LETTER -> WHITE_KNIGHT
            WHITE_PAWN_LETTER -> WHITE_PAWN
            BLACK_KING_LETTER -> BLACK_KING
            BLACK_QUEEN_LETTER -> BLACK_QUEEN
            BLACK_ROOK_LETTER -> BLACK_ROOK
            BLACK_BISHOP_LETTER -> BLACK_BISHOP
            BLACK_KNIGHT_LETTER -> BLACK_KNIGHT
            BLACK_PAWN_LETTER -> BLACK_PAWN
            else -> throw PieceException("Invalid piece letter: $letter")
        }

        fun from(type: PieceType, color: Color) = when (color) {
            Color.WHITE -> when (type) {
                PieceType.KING -> WHITE_KING
                PieceType.QUEEN -> WHITE_QUEEN
                PieceType.ROOK -> WHITE_ROOK
                PieceType.BISHOP -> WHITE_BISHOP
                PieceType.KNIGHT -> WHITE_KNIGHT
                PieceType.PAWN -> WHITE_PAWN
            }
            Color.BLACK -> when (type) {
                PieceType.KING -> BLACK_KING
                PieceType.QUEEN -> BLACK_QUEEN
                PieceType.ROOK -> BLACK_ROOK
                PieceType.BISHOP -> BLACK_BISHOP
                PieceType.KNIGHT -> BLACK_KNIGHT
                PieceType.PAWN -> BLACK_PAWN
            }
        }
    }
}
