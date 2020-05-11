package com.welyab.ankobachen.engine

import com.welyab.ankobachen.Board
import com.welyab.ankobachen.Color
import com.welyab.ankobachen.Piece
import com.welyab.ankobachen.PieceMovement
import com.welyab.ankobachen.PieceType
import com.welyab.ankobachen.Position

internal val pawns =
    arrayOf(
        arrayOf(0, 0, 0, 0, 0, 0, 0, 0),
        arrayOf(50, 50, 50, 50, 50, 50, 50, 50),
        arrayOf(10, 10, 20, 30, 30, 20, 10, 10),
        arrayOf(5, 5, 10, 25, 25, 10, 5, 5),
        arrayOf(0, 0, 0, 20, 20, 0, 0, 0),
        arrayOf(5, -5, -10, 0, 0, -10, -5, 5),
        arrayOf(5, 10, 10, -20, -20, 10, 10, 5),
        arrayOf(0, 0, 0, 0, 0, 0, 0, 0)
    )

internal val knights =
    arrayOf(
        arrayOf(-50, -40, -30, -30, -30, -30, -40, -50),
        arrayOf(-40, -20, 0, 0, 0, 0, -20, -40),
        arrayOf(-30, 0, 10, 15, 15, 10, 0, -30),
        arrayOf(-30, 5, 15, 20, 20, 15, 5, -30),
        arrayOf(-30, 0, 15, 20, 20, 15, 0, -30),
        arrayOf(-30, 5, 10, 15, 15, 10, 5, -30),
        arrayOf(-40, -20, 0, 5, 5, 0, -20, -40),
        arrayOf(-50, -40, -30, -30, -30, -30, -40, -50)
    )

internal val bishops =
    arrayOf(
        arrayOf(-20, -10, -10, -10, -10, -10, -10, -20),
        arrayOf(-10, 0, 0, 0, 0, 0, 0, -10),
        arrayOf(-10, 0, 5, 10, 10, 5, 0, -10),
        arrayOf(-10, 5, 5, 10, 10, 5, 5, -10),
        arrayOf(-10, 0, 10, 10, 10, 10, 0, -10),
        arrayOf(-10, 10, 10, 10, 10, 10, 10, -10),
        arrayOf(-10, 5, 0, 0, 0, 0, 5, -10),
        arrayOf(-20, -10, -10, -10, -10, -10, -10, -20)
    )

internal val rooks =
    arrayOf(
        arrayOf(0, 0, 0, 0, 0, 0, 0, 0),
        arrayOf(5, 10, 10, 10, 10, 10, 10, 5),
        arrayOf(-5, 0, 0, 0, 0, 0, 0, -5),
        arrayOf(-5, 0, 0, 0, 0, 0, 0, -5),
        arrayOf(-5, 0, 0, 0, 0, 0, 0, -5),
        arrayOf(-5, 0, 0, 0, 0, 0, 0, -5),
        arrayOf(-5, 0, 0, 0, 0, 0, 0, -5),
        arrayOf(0, 0, 0, 5, 5, 0, 0, 0)
    )

internal val queens =
    arrayOf(
        arrayOf(-20, -10, -10, -5, -5, -10, -10, -20),
        arrayOf(-10, 0, 0, 0, 0, 0, 0, -10),
        arrayOf(-10, 0, 5, 5, 5, 5, 0, -10),
        arrayOf(-5, 0, 5, 5, 5, 5, 0, -5),
        arrayOf(0, 0, 5, 5, 5, 5, 0, -5),
        arrayOf(-10, 5, 5, 5, 5, 5, 0, -10),
        arrayOf(-10, 0, 5, 0, 0, 0, 0, -10),
        arrayOf(-20, -10, -10, -5, -5, -10, -10, -20)
    )

internal val kings =
    arrayOf(
        arrayOf(-30, -40, -40, -50, -50, -40, -40, -30),
        arrayOf(-30, -40, -40, -50, -50, -40, -40, -30),
        arrayOf(-30, -40, -40, -50, -50, -40, -40, -30),
        arrayOf(-30, -40, -40, -50, -50, -40, -40, -30),
        arrayOf(-20, -30, -30, -40, -40, -30, -30, -20),
        arrayOf(-10, -20, -20, -20, -20, -20, -20, -10),
        arrayOf(20, 20, 0, 0, 0, 0, 20, 20),
        arrayOf(20, 30, 10, 0, 0, 10, 30, 20)
    )

internal val pieceValueMap = mapOf(
    PieceType.KING to kings,
    PieceType.QUEEN to queens,
    PieceType.ROOK to rooks,
    PieceType.BISHOP to bishops,
    PieceType.KNIGHT to knights,
    PieceType.PAWN to pawns
)

internal fun getPieceValue(piece: Piece, position: Position) =
    when (piece.color) {
        Color.WHITE -> pieceValueMap[piece.type]!![position.row][position.column]
        Color.BLACK -> pieceValueMap[piece.type]!![7 - position.row][position.column] * -1
    }

internal fun Board.eval() =
    this.iterator()
        .asSequence()
        .filter { it.isNotEmpty() }
        .map { getPieceValue(it.getPiece(), it.getPosition()) }
        .sum()

internal data class BestMove(val origin: Position, val destination: Position, val score: Int)

internal fun max(board: Board, depth: Int, pieceMovement: PieceMovement?): BestMove {
    if (depth == 1) {
        return BestMove(
            origin = pieceMovement!!.getOrigin().position,
            destination = pieceMovement!!.getDestination().getTarget().position,
            score = board.eval()
        )
    }

    var best = BestMove(Position.A1, Position.H8, Int.MIN_VALUE)
    for (pieceMovement in board.getMovements()) {
        board.move(pieceMovement)
        val move = min(board, depth - 1, pieceMovement)
        board.undo()

        if (move.score > best.score) best = BestMove(
            pieceMovement.getOrigin().position,
            pieceMovement.getDestination().getTarget().position,
            move.score
        )
    }
    return best
}

internal fun min(board: Board, depth: Int, pieceMovement: PieceMovement?): BestMove {
    if (depth == 1) {
        return BestMove(
            origin = pieceMovement!!.getOrigin().position,
            destination = pieceMovement!!.getDestination().getTarget().position,
            score = board.eval()
        )
    }

    var best = BestMove(Position.A1, Position.H8, Int.MAX_VALUE)
    for (pieceMovement in board.getMovements()) {
        board.move(pieceMovement)
        val move = max(board, depth - 1, pieceMovement)
        board.undo()

        if (move.score < best.score) best = BestMove(
            pieceMovement.getOrigin().position,
            pieceMovement.getDestination().getTarget().position,
            move.score
        )
    }
    return best
}
