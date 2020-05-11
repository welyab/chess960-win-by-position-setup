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

import kotlin.math.abs
import kotlin.random.Random

class Board private constructor(
    private val grid: Grid,
    private val boardState: BoardStateImpl,
    private val movementHistory: MovementHistory
) : Copyable<Board>, Iterable<Square> {

    constructor() : this(FenString(FEN_INITIAL))

    constructor(fen: String) : this(FenString(fen))

    constructor(fenString: FenString) : this(
        fenString.toGrid(),
        fenString.toBoardState(),
        MovementHistory()
    )

    fun getBoardState(): BoardState = boardState

    fun moveRandom() {
        move(getMovementRandom())
    }

    fun move(origin: Position, destination: Position, pawnPromotion: PieceType = PieceType.QUEEN) {
        val movement = getMovements(origin)
            .asSequenceOfDestinations()
            .filter { it.getDestination().getTarget().position == destination }
            .filter { !it.getFlags().isPromotion() || it.getDestination().getTarget().piece.type == pawnPromotion }
            .firstOrNull()
            ?: throw BoardException("No valid movement from $origin to $destination")
        move(movement)
    }

    fun move(pieceMovement: PieceMovement) {
        try {
            move(
                pieceMovement.getOrigin(),
                pieceMovement.getDestination().getTarget(),
                pieceMovement.getFlags().isCapture(),
                pieceMovement.getFlags().isEnPassant(),
                pieceMovement.getFlags().isCastling()
            )
        } catch (e: Exception) {
            throw e
        }
    }

    fun move(
        origin: LocalizedPiece,
        target: LocalizedPiece,
        isCapture: Boolean,
        isEnPassant: Boolean,
        isCastling: Boolean
    ) {
        val color = boardState.getSideToMove()
        val originPos = origin.position
        val targetPos = target.position

        val capturedPiece = when {
            isEnPassant -> grid[originPos.row][targetPos.column].getPiece()
            isCapture -> grid[targetPos].getPiece()
            else -> null
        }

        movementHistory.add(
            MovementAction(
                boardState = boardState.copy(),
                origin = origin,
                target = target,
                isCapture = isCapture,
                isEnPassant = isEnPassant,
                isCastling = isCastling,
                capturedPiece = capturedPiece
            )
        )

        if (origin.piece.isKing && !isCastling) {
            boardState.setKingPosition(targetPos, color)
        }

        if (isCastling) {
            val (rookTargetPos, realKingTargetPos) = when (color) {
                Color.WHITE -> if (
                    boardState.hasLeftCastlingFor(color, targetPos)
                ) Pair(leftWhiteRookFinalCastlingPosition, leftWhiteKingFinalCastlingPosition)
                else Pair(rightWhiteRookFinalCastlingPosition, rightWhiteKingFinalCastlingPosition)
                Color.BLACK -> if (
                    boardState.hasLeftCastlingFor(color, targetPos)
                ) Pair(leftBlackRookFinalCastlingPosition, leftBlackKingFinalCastlingPosition)
                else Pair(rightBlackRookFinalCastlingPosition, rightBlackKingFinalCastlingPosition)
            }
            grid[originPos].setContent(EmptySquareContent)
            grid[rookTargetPos].setContent(grid[targetPos].getContent())
            grid[targetPos].setContent(EmptySquareContent)
            grid[realKingTargetPos].setContent(target.piece)

            boardState.setKingPosition(realKingTargetPos, color)
        } else if (isEnPassant) {
            grid[originPos].setContent(EmptySquareContent)
            grid[targetPos].setContent(target.piece)
            grid[originPos.row][targetPos.column].setContent(EmptySquareContent)
        } else {
            grid[originPos].setContent(EmptySquareContent)
            grid[targetPos].setContent(target.piece)
        }

        if (target.piece.isKing) {
            boardState.clearLeftRookPosition(color)
            boardState.clearRightRookPosition(color)
        } else if (origin.piece.isRook && boardState.hasLeftCastlingFor(color, originPos)) {
            boardState.clearLeftRookPosition(color)
        } else if (origin.piece.isRook && boardState.hasRightCastlingFor(color, originPos)) {
            boardState.clearRightRookPosition(color)
        }
        if (capturedPiece?.isRook == true && boardState.hasLeftCastlingFor(capturedPiece.color, targetPos)) {
            boardState.clearLeftRookPosition(capturedPiece.color)
        } else if (capturedPiece?.isRook == true && boardState.hasRightCastlingFor(capturedPiece.color, targetPos)) {
            boardState.clearRightRookPosition(capturedPiece.color)
        }

        if (target.piece.isBlack) {
            boardState.incrementFullMoveCounter()
        }

        val epTarget: Position? = if (!target.piece.isPawn || abs(originPos.row - targetPos.row) != 2) null
        else arrayOf(-1, 1)
            .asSequence()
            .filter { targetPos.column + it in 0..7 }
            .map { targetPos.column + it }
            .filter { grid[targetPos.row][it].getContent().isPawn }
            .filter { grid[targetPos.row][it].getContent().isColorOf(color.opposite) }
            .map { Position.from((originPos.row + targetPos.row) / 2, originPos.column) }
            .firstOrNull()
        if (epTarget != null) boardState.setEpTarget(epTarget)
        else boardState.clearEpTarget()

        if (target.piece.isPawn || isCapture) {
            boardState.resetHalfMoveClock()
        } else {
            boardState.incrementHalfMoveClock()
        }

        boardState.incrementPlyCounter()
    }

    fun hasPreviousMove() = movementHistory.hasPreviousMove()

    fun undo() {
        if (!movementHistory.hasPreviousMove()) throw BoardException("No previous movement to undo")

        val movementAction = movementHistory.pollLast()

        val savedState = movementAction.boardState
        val isCapture = movementAction.isCapture
        val isEnPassant = movementAction.isEnPassant
        val isCastling = movementAction.isCastling
        val origin = movementAction.origin
        val target = movementAction.target

        if (isCastling) {
            val rook = Piece.from(PieceType.ROOK, origin.piece.color)

            val (rookTargetPos, realKingTargetPos) = when (origin.piece.color) {
                Color.WHITE -> if (
                    savedState.hasLeftCastlingFor(origin.piece.color, target.position)
                ) Pair(leftWhiteRookFinalCastlingPosition, leftWhiteKingFinalCastlingPosition)
                else Pair(rightWhiteRookFinalCastlingPosition, rightWhiteKingFinalCastlingPosition)
                Color.BLACK -> if (
                    savedState.hasLeftCastlingFor(origin.piece.color, target.position)
                ) Pair(leftBlackRookFinalCastlingPosition, leftBlackKingFinalCastlingPosition)
                else Pair(rightBlackRookFinalCastlingPosition, rightBlackKingFinalCastlingPosition)
            }

            grid[rookTargetPos].setContent(EmptySquareContent)
            grid[realKingTargetPos].setContent(EmptySquareContent)

            grid[origin.position].setContent(origin.piece)
            grid[target.position].setContent(rook)
        } else if (isEnPassant) {
            grid[origin.position].setContent(origin.piece)
            grid[target.position].setContent(EmptySquareContent)
            grid[origin.position.row][target.position.column].setContent(movementAction.capturedPiece!!)
        } else {
            grid[origin.position].setContent(origin.piece)
            if (isCapture) grid[target.position].setContent(movementAction.capturedPiece!!)
            else grid[target.position].setContent(EmptySquareContent)
        }

        boardState.set(movementAction.boardState)
    }

    fun getMovements(color: Color = boardState.getSideToMove()) = privateGetMovements(
        color = color,
        stopOnFirstMovement = false,
        extractAllFlags = true
    )

    private fun privateGetMovements(
        color: Color,
        stopOnFirstMovement: Boolean,
        extractAllFlags: Boolean
    ): Movements {
        val movements = ArrayList<PieceMovements>()
        for (square in grid.asSequence()) {
            if (square.isEmpty()) continue
            if (square.getPiece().color != color) continue
            privateGetMovements(
                square.getPosition(),
                stopOnFirstMovement,
                extractAllFlags
            ).apply {
                if (isNotEmpty()) movements += this
            }
            if (movements.isNotEmpty() && stopOnFirstMovement) break
        }
        return movements.toMovements()
    }

    fun getMovementRandom(): PieceMovement =
        getMovements()
            .toListDestinations()
            .let {
                if (it.isEmpty())
                    throw BoardException("No movement available. Side to move: ${boardState.getSideToMove()}")

                it[Random.nextInt(it.size)]
            }

    fun getMovementsWhiteBlack() = getMovements(Color.WHITE) + getMovements(Color.BLACK)

    fun getMovements(position: Position): Movements =
        if (isEmpty(position)) throw EmptySquareException(position)
        else try {
            privateGetMovements(
                position = position,
                stopOnFirstMovement = false,
                extractAllFlags = true
            ).toMovements()
        } catch (e: Exception) {
            throw if (e is BoardException) e
            else BoardException("Fail to get movements of piece located in $position", e)
        }

    private fun privateGetMovements(
        position: Position,
        stopOnFirstMovement: Boolean,
        extractAllFlags: Boolean
    ): PieceMovements =
        when (grid[position].getPiece().type) {
            PieceType.KING -> getMovementsFromKing(position, stopOnFirstMovement, extractAllFlags)
            PieceType.PAWN -> getMovementsFromPawn(position, stopOnFirstMovement, extractAllFlags)
            else -> getMovementsFromQueenRookBishopKnight(position, stopOnFirstMovement, extractAllFlags)
        }

    private fun getMovementsFromKing(
        position: Position,
        stopOnFirstMovement: Boolean,
        extractAllFlags: Boolean
    ): PieceMovements {
        val kingPiece = grid[position].getPiece()
        val destinations = arrayListOf<MovementDestination>()
        val origin = LocalizedPiece(kingPiece, position)
        squareWalker(
            position,
            1,
            KING_MOVEMENT_TEMPLATE
        ) { square ->
            var feedBack = SquareWalkingFeedback.CONTINUE
            if (square.getContent().isColorOf(kingPiece.color)) {
                feedBack = SquareWalkingFeedback.STOP_DIRECTION
            } else {
                grid[position].setContent(EmptySquareContent)
                val isAttacked = isAttacked(
                    square.getPosition(),
                    kingPiece.color.opposite
                )
                grid[position].setContent(kingPiece)
                if (isAttacked) {
                    feedBack = SquareWalkingFeedback.STOP_DIRECTION
                } else {
                    val target = LocalizedPiece(kingPiece, square.getPosition())
                    destinations += MovementDestination(
                        target,
                        flags = extractMovementFlags(origin, target, extractAllFlags)
                    )
                    if (stopOnFirstMovement) feedBack = SquareWalkingFeedback.STOP_WALKING
                }
            }
            feedBack
        }

        if (destinations.isEmpty() || !stopOnFirstMovement) {
            destinations += getCastlingDestinations(position, stopOnFirstMovement, extractAllFlags)
        }

        return PieceMovements(
            origin,
            destinations,
            destinations.summarizeFlags()
        )
    }

    private fun getCastlingDestinations(
        kingPosition: Position,
        stopOnFirstMovement: Boolean,
        extractAllFlags: Boolean
    ): List<MovementDestination> {
        data class CastlingInfo(
            val rookStartPosition: Position,
            val rookEndPosition: Position,
            val kingEndPosition: Position
        )

        val kingPiece = grid[kingPosition].getPiece()
        val origin = LocalizedPiece(kingPiece, kingPosition)
        val destinations = arrayListOf<MovementDestination>()
        val availableCastlings = arrayListOf<CastlingInfo>()

        if (boardState.hasLeftCastling(kingPiece.color)) {
            availableCastlings += CastlingInfo(
                rookStartPosition = boardState.getLeftRookPosition(kingPiece.color),
                rookEndPosition = when (kingPiece.color) {
                    Color.WHITE -> leftWhiteRookFinalCastlingPosition
                    Color.BLACK -> leftBlackRookFinalCastlingPosition
                },
                kingEndPosition = when (kingPiece.color) {
                    Color.WHITE -> leftWhiteKingFinalCastlingPosition
                    Color.BLACK -> leftBlackKingFinalCastlingPosition
                }
            )
        }
        if (boardState.hasRightCastling(kingPiece.color)) {
            availableCastlings += CastlingInfo(
                rookStartPosition = boardState.getRightRookPosition(kingPiece.color),
                rookEndPosition = when (kingPiece.color) {
                    Color.WHITE -> rightWhiteRookFinalCastlingPosition
                    Color.BLACK -> rightBlackRookFinalCastlingPosition
                },
                kingEndPosition = when (kingPiece.color) {
                    Color.WHITE -> rightWhiteKingFinalCastlingPosition
                    Color.BLACK -> rightBlackKingFinalCastlingPosition
                }
            )
        }

        for (castlingInfo in availableCastlings) {
            var invalidCastling = false
            var direction =
                if (castlingInfo.rookStartPosition.column < castlingInfo.rookEndPosition.column) +1 else -1
            var distance = 1
            do {
                val currentRookPosition = Position.from(
                    castlingInfo.rookStartPosition.row,
                    castlingInfo.rookStartPosition.column + direction * distance
                )
                distance++
                if (
                    isNotEmpty(currentRookPosition)
                    && grid[currentRookPosition].getContent().isNotPieceof(kingPiece)
                ) {
                    invalidCastling = true
                    break
                }
            } while (currentRookPosition != castlingInfo.rookEndPosition)

            if (!invalidCastling) {
                val rookPiece = grid[castlingInfo.rookStartPosition].getPiece()
                direction = if (kingPosition.column < castlingInfo.kingEndPosition.column) +1 else -1
                distance = 0
                do {
                    val currentKingPosition = Position.from(
                        kingPosition.row, kingPosition.column + direction * distance
                    )
                    distance++
                    if (isAttacked(currentKingPosition, kingPiece.color.opposite)) {
                        invalidCastling = true
                        break
                    }
                    if (
                        currentKingPosition != kingPosition
                        && currentKingPosition != castlingInfo.rookStartPosition
                        && isNotEmpty(currentKingPosition)
                        && grid[currentKingPosition].getContent().isNotPieceof(rookPiece)
                    ) {
                        invalidCastling = true
                        break
                    }
                } while (currentKingPosition != castlingInfo.kingEndPosition)

                if (!invalidCastling) {
                    val target = LocalizedPiece(kingPiece, castlingInfo.rookStartPosition)
                    destinations += MovementDestination(
                        target,
                        flags = extractMovementFlags(origin, target, extractAllFlags)
                    )
                    if (stopOnFirstMovement) break
                }
            }
        }

        return destinations
    }

    private fun getMovementsFromPawn(
        position: Position,
        stopOnFirstMovement: Boolean,
        extractAllFlags: Boolean
    ): PieceMovements {
        val pawnPiece = grid[position].getPiece()
        val origin = LocalizedPiece(pawnPiece, position)
        val destinations = arrayListOf<MovementDestination>()

        squareWalker(
            position,
            1,
            when (pawnPiece.color) {
                Color.WHITE -> whitePawnSingleSquareMovementTemplate
                Color.BLACK -> blackPawnSingleSquareMovementTemplate
            }
        ) { square ->
            var feedback = SquareWalkingFeedback.STOP_DIRECTION
            if (isEmpty(square.getPosition()) && !isKingInCheckWithMovement(position, square.getPosition())) {
                val target = LocalizedPiece(pawnPiece, square.getPosition())
                destinations += getPawnMovementDestinations(origin, target, stopOnFirstMovement, extractAllFlags)
                if (stopOnFirstMovement) feedback = SquareWalkingFeedback.STOP_WALKING
            }
            feedback
        }

        if (
            (destinations.isEmpty() || !stopOnFirstMovement)
            && pawnPiece.color.isWhite && position.row == 6 || pawnPiece.color.isBlack && position.row == 1
        ) {
            squareWalker(
                position,
                1,
                when (pawnPiece.color) {
                    Color.WHITE -> whitePawnDoubleSquareMovementTemplate
                    Color.BLACK -> blackPawnDoubleSquareMovementTemplate
                }
            ) { square ->
                val midRow = (position.row + square.getPosition().row) / 2
                val midPosition = Position.from(midRow, position.column)
                var feedback = SquareWalkingFeedback.STOP_DIRECTION
                if (
                    isEmpty(square.getPosition())
                    && isEmpty(midPosition)
                    && !isKingInCheckWithMovement(position, square.getPosition())
                ) {
                    val target = LocalizedPiece(pawnPiece, square.getPosition())
                    destinations += MovementDestination(
                        target,
                        extractMovementFlags(origin, target, extractAllFlags)
                    )
                    if (stopOnFirstMovement) feedback = SquareWalkingFeedback.STOP_WALKING
                }
                feedback
            }
        }

        if (destinations.isEmpty() || !stopOnFirstMovement) {
            squareWalker(
                position,
                1,
                when (pawnPiece.color) {
                    Color.WHITE -> whitePawnCaptureMovementTemplate
                    Color.BLACK -> blackPawnCaptureMovementTemplate
                }
            ) { square ->
                var feedback = SquareWalkingFeedback.STOP_DIRECTION
                if (
                    (square.isEmpty() && boardState.hasEpTargetFor(square.getPosition())
                            || square.getContent().isColorOf(pawnPiece.color.opposite))
                    && !isKingInCheckWithMovement(position, square.getPosition())
                ) {
                    val target = LocalizedPiece(pawnPiece, square.getPosition())
                    destinations += getPawnMovementDestinations(origin, target, stopOnFirstMovement, extractAllFlags)
                    if (stopOnFirstMovement) feedback = SquareWalkingFeedback.STOP_WALKING
                }
                feedback
            }
        }

        return PieceMovements(
            origin,
            destinations,
            destinations.summarizeFlags()
        )
    }

    private fun getPawnMovementDestinations(
        origin: LocalizedPiece,
        destination: LocalizedPiece,
        stopOnFirstMovement: Boolean,
        extractAllFlags: Boolean
    ): List<MovementDestination> {
        val pawnTargets = if (destination.position.isPawnPromotionRow()) {
            if (stopOnFirstMovement) listOf(pawnPromotionReplacements[0])
            else pawnPromotionReplacements
        } else {
            pawnList
        }
        val color = origin.piece.color
        return pawnTargets
            .asSequence()
            .map { Piece.from(it, color) }
            .map {
                val target = LocalizedPiece(it, destination.position)
                MovementDestination(
                    target,
                    extractMovementFlags(origin, target, extractAllFlags)
                )
            }
            .toList()
    }

    private fun getMovementsFromQueenRookBishopKnight(
        position: Position,
        stopOnFirstMovement: Boolean,
        extractAllFlags: Boolean
    ): PieceMovements {
        val piece = grid[position].getPiece()
        val origin = LocalizedPiece(piece, position)
        val (movementTemplate, walkMaxLength) = when (piece.type) {
            PieceType.QUEEN -> Pair(queenMovementTemplate, BOARD_SIZE - 1)
            PieceType.ROOK -> Pair(rookMovementTemplate, BOARD_SIZE - 1)
            PieceType.BISHOP -> Pair(bishopMovementTemplate, BOARD_SIZE - 1)
            PieceType.KNIGHT -> Pair(knightMovementTemplate, 1)
            else -> throw Error("Invalid piece type: ${piece.type}")
        }
        val destinations = arrayListOf<MovementDestination>()
        squareWalker(
            startPosition = position,
            walkMaxLength = walkMaxLength,
            movementTemplate = movementTemplate
        ) { square ->
            when {
                square.getContent().isColorOf(piece.color) -> SquareWalkingFeedback.STOP_DIRECTION
                isKingInCheckWithMovement(position, square.getPosition()) ->
                    if (square.isEmpty()) SquareWalkingFeedback.CONTINUE
                    else SquareWalkingFeedback.STOP_DIRECTION
                else -> {
                    val target = LocalizedPiece(piece, square.getPosition())
                    destinations += MovementDestination(
                        target = target,
                        flags = extractMovementFlags(origin, target, extractAllFlags)
                    )
                    if (square.isEmpty()) SquareWalkingFeedback.CONTINUE
                    else SquareWalkingFeedback.STOP_DIRECTION
                }
            }
        }
        return PieceMovements(
            origin,
            destinations,
            destinations.summarizeFlags()
        )
    }

    private fun isKingInCheckWithMovement(origin: Position, destination: Position): Boolean {
        val piece = grid[origin].getPiece()
        if (boardState.isNotKingPresent(piece.color)) return false

        val isEnPassant = piece.isPawn && origin.column != destination.column && grid[destination].isEmpty()
        val backupTarget = if (isEnPassant) grid[origin.row][boardState.getEpTarget().column].getContent()
        else grid[destination].getContent()
        grid[destination].setContent(grid[origin].getContent())
        if (isEnPassant) grid[origin.row][boardState.getEpTarget().column].setContent(EmptySquareContent)
        grid[origin].setContent(EmptySquareContent)

        val isKingAttacked = isAttacked(boardState.getKingPosition(piece.color), piece.color.opposite)

        grid[origin].setContent(grid[destination].getContent())
        grid[destination].setContent(EmptySquareContent)
        if (isEnPassant) grid[origin.row][boardState.getEpTarget().column].setContent(backupTarget)
        else grid[destination].setContent(backupTarget)

        return isKingAttacked
    }

    private fun extractMovementFlags(
        origin: LocalizedPiece,
        destination: LocalizedPiece,
        extractAllFlags: Boolean
    ): MovementFlags {
        val isEnPassant = origin.piece.isPawn
                && origin.position.column != destination.position.column
                && isEmpty(destination.position)
        val isCapture = !grid[destination.position].getContent().isColorOf(grid[origin.position].getPiece().color)
                && (isEnPassant || isNotEmpty(destination.position))
        val isCastling = grid[origin.position].getContent().isKing
                && grid[destination.position].getContent().isRook
                && grid[origin.position].getContent().isColorOf(grid[destination.position].getPiece().color)
        val isPromotion = grid[origin.position].getContent().isPawn
                && (destination.position.row == 0 || destination.position.row == 7)

        var isCheck = false
        var isDiscovery = false
        var isDouble = false
        var isCheckmate = false
        var isStalemate = false

        if (extractAllFlags && boardState.isKingPresent(origin.piece.color.opposite)) {
            move(
                origin = origin,
                target = destination,
                isCapture = isCapture,
                isEnPassant = isEnPassant,
                isCastling = isCastling
            )
            val kingPosition = boardState.getKingPosition(boardState.getSideToMove())
            val color = boardState.getSideToMove()
            val attackers = getAttackers(
                kingPosition,
                color.opposite
            )
            isCheck = attackers.isNotEmpty()
            isDouble = attackers.size == 2
            if (!isDouble) {
                isDiscovery = attackers
                    .asSequence()
                    .filter { it.piece.isQueen || it.piece.isRook || it.piece.isBishop }
                    .filter { it.position.isSameLine(origin.position, kingPosition) }
                    .filter { !it.position.isSameLine(destination.position, kingPosition) }
                    .any()
            }
            val hasValidMovements =
                if (isDouble) privateGetMovements(
                    position = kingPosition,
                    stopOnFirstMovement = true,
                    extractAllFlags = false
                ).isNotEmpty()
                else privateGetMovements(
                    color = color,
                    stopOnFirstMovement = true,
                    extractAllFlags = false
                ).isNotEmpty()
            if (isCheck && !hasValidMovements) isCheckmate = true
            else if (!isCheck && !hasValidMovements) isStalemate = true
            undo()
        }

        return MovementFlags(
            isCapture = isCapture,
            isEnPassant = isEnPassant,
            isCastling = isCastling,
            isPromotion = isPromotion,
            isCheck = isCheck,
            isDiscovery = isDiscovery,
            isDouble = isDouble,
            isCheckmate = isCheckmate,
            isStalemate = isStalemate
        )
    }

    fun isAttacked(attackedPosition: Position, attackerColor: Color) =
        privateGetAttackers(attackedPosition, attackerColor, true).isNotEmpty()

    fun getAttackers(attackedPosition: Position, attackerColor: Color) =
        privateGetAttackers(attackedPosition, attackerColor, false)

    fun getPiecesCanReach(position: Position, color: Color): List<LocalizedPiece> {
        val pieces = getAttackers(position, color)
            .asSequence()
            .filter {
                getMovements(it.position)
                    .asSequenceOfDestinations()
                    .any { p -> p.getDestination().getTarget().position == position }
            }
            .toList()
        val direction = when (color) {
            Color.WHITE -> +1
            Color.BLACK -> -1
        }
        var pawnPosition: Position? = null
        for (distance in 1..2) {
            val targetRow = position.row + distance * direction
            if (!isInsideBoard(targetRow, position.column)) continue
            if (grid[targetRow][position.column].isEmpty()) continue
            if (grid[targetRow][position.column].getContent().isPawnOfColor(color)) {
                pawnPosition = Position.from(targetRow, position.column)
                break
            }
        }
        if (pawnPosition != null) {
            val x = getMovements(pawnPosition)
                .asSequenceOfDestinations()
                .filter { it.getDestination().getTarget().position == position }
                .map { it.getOrigin() }
                .firstOrNull()
            if (x != null)
                return pieces + listOf(x)
        }

        return pieces
    }

    private fun privateGetAttackers(
        attackedPosition: Position,
        attackerColor: Color,
        stopOnFirstAttacker: Boolean = false
    ): List<LocalizedPiece> {
        val attackers = arrayListOf<LocalizedPiece>()
        PieceType.values().forEach { pieceType ->
            val (movementTemplate, walkMaxLength) = when (pieceType) {
                PieceType.KING -> Pair(KING_MOVEMENT_TEMPLATE, 1)
                PieceType.QUEEN -> Pair(queenMovementTemplate, BOARD_SIZE - 1)
                PieceType.ROOK -> Pair(rookMovementTemplate, BOARD_SIZE - 1)
                PieceType.BISHOP -> Pair(bishopMovementTemplate, BOARD_SIZE - 1)
                PieceType.KNIGHT -> Pair(knightMovementTemplate, 1)
                PieceType.PAWN -> Pair(
                    if (attackerColor.isWhite) blackPawnCaptureMovementTemplate
                    else whitePawnCaptureMovementTemplate,
                    1
                )
            }
            val attackerPiece = Piece.from(pieceType, attackerColor)
            squareWalker(
                attackedPosition,
                walkMaxLength,
                movementTemplate
            ) { square ->
                when {
                    square.contains(attackerPiece) -> {
                        attackers += LocalizedPiece(square.getPiece(), square.getPosition())
                        if (stopOnFirstAttacker) SquareWalkingFeedback.STOP_WALKING
                        else SquareWalkingFeedback.STOP_DIRECTION
                    }
                    square.isNotEmpty() -> SquareWalkingFeedback.STOP_DIRECTION
                    else -> SquareWalkingFeedback.CONTINUE
                }
            }
        }
        return attackers
    }

    private fun squareWalker(
        startPosition: Position,
        walkMaxLength: Int,
        movementTemplate: MovementTemplate,
        squareVisitorFeedback: (SquareImpl) -> SquareWalkingFeedback
    ) {
        for (direction in movementTemplate) {
            for (walkLength in 1..walkMaxLength) {
                val targetRow = startPosition.row + direction.rowDirection * walkLength
                val targetColumn = startPosition.column + direction.columnDirection * walkLength
                if (isNotInsideBoard(targetRow, targetColumn)) break
                val position = Position.from(targetRow, targetColumn)
                val squareWalking = squareVisitorFeedback.invoke(grid[position])
                if (squareWalking == SquareWalkingFeedback.STOP_DIRECTION) break
                if (squareWalking == SquareWalkingFeedback.STOP_WALKING) return
            }
        }
    }

    fun getSideToMove() =
        boardState.getSideToMove()

    fun isEmpty(position: Position) =
        grid[position].getContent().isEmpty

    fun isNotEmpty(position: Position) =
        !isEmpty(position)

    private fun isInsideBoard(row: Int, column: Int) =
        row in 0..7 && column in 0..7

    private fun isNotInsideBoard(row: Int, column: Int) =
        !isInsideBoard(row, column)

    fun getFen(): String {
        val pieceDisposition = grid
            .asSequenceOfRows()
            .map { row ->
                val builder = StringBuilder()
                var spaces = 0
                for (square in row) {
                    if (square.isNotEmpty()) {
                        if (spaces > 0) builder.append(spaces)
                        builder.append(square.getContent().letter)
                        spaces = 0
                    } else {
                        spaces++
                    }
                }
                if (spaces > 0) builder.append(spaces)
                builder.toString()
            }
            .reduce { r1, r2 -> "$r1/$r2" }

        val builder = StringBuilder()
        builder.append(pieceDisposition)
        builder.append(' ')
        builder.append(boardState.getSideToMove().letter)
        builder.append(' ')

        var hasCastling = false
        if (boardState.hasRightCastling(Color.WHITE)) {
            builder.append(FEN_WHITE_KING_SIDE_CASTLING_FLAG)
            hasCastling = true
        }
        if (boardState.hasLeftCastling(Color.WHITE)) {
            builder.append(FEN_WHITE_QUEEN_SIDE_CASTLING_FLAG)
            hasCastling = true
        }
        if (boardState.hasRightCastling(Color.BLACK)) {
            builder.append(FEN_BLACK_KING_SIDE_CASTLING_FLAG)
            hasCastling = true
        }
        if (boardState.hasLeftCastling(Color.BLACK)) {
            builder.append(FEN_BLACK_QUEEN_SIDE_CASTLING_FLAG)
            hasCastling = true
        }
        if (!hasCastling) {
            builder.append(' ')
            builder.append('-')
        }

        builder.append(' ')
        if (boardState.hasEnPassant()) {
            builder.append(boardState.getEpTarget().getSanNotation())
        } else {
            builder.append('-')
        }

        builder.append(' ')
        builder.append(boardState.getHalfMoveClock())

        builder.append(' ')
        builder.append(boardState.getFullMoveCounter())

        return builder.toString()
    }

    override fun copy() = Board(
        grid.copy(),
        boardState.copy(),
        movementHistory.copy()
    )

    private fun Exception.toBoardException(message: String) = BoardException(
        "$message\n\nBoard:\n${this@Board.toString()}\n",
        this
    )

    override fun iterator() =
        grid.asSequence().map { it.copy() as Square }.iterator()

    override fun toString() =
        grid.asSequenceOfRows()
            .map { line -> line.map { it.getContent().letter }.toTypedArray() }
            .map { line -> "│ %c │ %c │ %c │ %c │ %c │ %c │ %c │ %c │".format(*line) }
            .reduce { l1, l2 -> "$l1$NEWLINE├───┼───┼───┼───┼───┼───┼───┼───┤$NEWLINE$l2" }
            .let {
                "┌───┬───┬───┬───┬───┬───┬───┬───┐$NEWLINE$it$NEWLINE└───┴───┴───┴───┴───┴───┴───┴───┘$NEWLINE"
            }
}

fun main() {
//    e8 -> d7=k, no flags
//    e8 -> e7=k, no flags
//    e8 -> d8=k, no flags
//    e8 -> a8=k, castling
    val board = Board("r3kbnr/1p3ppp/p1n1p3/2p5/P1B2PP1/2NP2P1/1PPB4/2KR3R b kq - 2 14")
    val movements = board.getMovements(Position.E8)
    movements.forEachDestination {
        println(it)
    }
}

internal typealias MovementTemplate = ArrayList<Direction>

internal val leftWhiteKingFinalCastlingPosition = Position.C1
internal val rightWhiteKingFinalCastlingPosition = Position.G1
internal val leftBlackKingFinalCastlingPosition = Position.C8
internal val rightBlackKingFinalCastlingPosition = Position.G8

internal val leftWhiteRookFinalCastlingPosition = Position.D1
internal val rightWhiteRookFinalCastlingPosition = Position.F1
internal val leftBlackRookFinalCastlingPosition = Position.D8
internal val rightBlackRookFinalCastlingPosition = Position.F8

internal val pawnList = arrayListOf(PieceType.PAWN)

internal val pawnPromotionReplacements = arrayListOf(
    PieceType.QUEEN,
    PieceType.ROOK,
    PieceType.BISHOP,
    PieceType.KNIGHT
)

internal enum class SquareWalkingFeedback {
    CONTINUE,
    STOP_DIRECTION,
    STOP_WALKING
}

class BoardException(message: String, cause: Throwable? = null) : ChessException(message, cause)
class EmptySquareException(val position: Position) : ChessException("Square $position is empty")

internal class Grid(
    private val matrix: Array<Array<SquareImpl>> = createEmptyGrid()
) : Copyable<Grid> {

    fun asSequence() =
        asSequenceOfRows().flatMap { row -> row.asSequence() }

    fun setContent(position: Position, content: SquareContent<*>) {
        this[position].setContent(content)
    }

    fun asSequenceOfRows() =
        matrix.asSequence()

    operator fun get(row: Int) =
        matrix[row]

    operator fun get(position: Position) =
        matrix[position.row][position.column]

    companion object {
        fun createEmptyGrid() = Array(BOARD_SIZE) { row ->
            Array(BOARD_SIZE) { column ->
                SquareImpl(Position.from(row, column))
            }
        }
    }

    override fun copy() = Grid().apply {
        this@Grid.matrix.forEach { row ->
            row.forEach { square ->
                this[square.getPosition()].setContent(square.getContent())
            }
        }
    }
}

internal fun List<PieceMovements>.toMovements() = Movements(this, summarizeMetadata())
internal fun PieceMovements.toMovements() = Movements(listOf(this), getMetadata())

internal fun FenString.toGrid() = Grid().apply {
    getFenInfo().piecesDisposition.forEach {
        setContent(it.position, it.piece)
    }
}

internal fun FenInfo.getKingPosition(color: Color) =
    piecesDisposition
        .asSequence()
        .filter { it.piece.type == PieceType.KING }
        .filter { it.piece.color == color }
        .map { it.position }
        .firstOrNull()

internal fun FenString.toBoardState() = BoardStateImpl(
    sideToMove = this.getFenInfo().sideToMove,
    halfMoveClock = this.getFenInfo().halfMoveClock,
    fullMoveCounter = this.getFenInfo().fullMoveCounter,
    leftWhiteRook = this.getFenInfo().castlingFlags.leftWhiteRook,
    rightWhiteRook = this.getFenInfo().castlingFlags.rightWhiteRook,
    leftBlackRook = this.getFenInfo().castlingFlags.leftBlackRook,
    rightBlackRook = this.getFenInfo().castlingFlags.rightBlackRook,
    whiteKingPosition = this.getFenInfo().getKingPosition(Color.WHITE),
    blackKingPosition = this.getFenInfo().getKingPosition(Color.BLACK),
    epTarget = this.getFenInfo().epTarget
)

internal data class MovementAction(
    val boardState: BoardStateImpl,
    val origin: LocalizedPiece,
    val target: LocalizedPiece,
    val isCapture: Boolean,
    val isEnPassant: Boolean,
    val isCastling: Boolean,
    val capturedPiece: Piece?
)

internal class MovementHistory private constructor(
    private val moves: ArrayList<MovementAction>
) : Copyable<MovementHistory> {

    constructor() : this(ArrayList())

    fun hasPreviousMove() = moves.isNotEmpty()

    fun pollLast(): MovementAction {
        if (!hasPreviousMove()) throw BoardException("No movement to remove")
        return moves.removeAt(moves.lastIndex)
    }

    fun add(movementAction: MovementAction) {
        moves += movementAction
    }

    fun forEachMove(action: (movementAction: MovementAction) -> Unit) {
        moves.forEach { action.invoke(it) }
    }

    override fun copy() = moves
        .asSequence()
        .map { it.copy() }
        .toCollection(ArrayList())
        .let { MovementHistory(it) }

}

internal class BoardStateImpl private constructor(
    private var plyCounter: Int = 0,
    private var sideToMoveAdjuster: Int,
    private var halfMoveClock: Int = 0,
    private var fullMoveCounter: Int = 1,
    private var leftWhiteRook: Position? = null,
    private var rightWhiteRook: Position? = null,
    private var leftBlackRook: Position? = null,
    private var rightBlackRook: Position? = null,
    private var whiteKingPosition: Position? = null,
    private var blackKingPosition: Position? = null,
    private var epTarget: Position? = null
) : BoardState, Copyable<BoardStateImpl> {

    constructor(
        sideToMove: Color = Color.WHITE,
        halfMoveClock: Int = 0,
        fullMoveCounter: Int = 1,
        leftWhiteRook: Position? = null,
        rightWhiteRook: Position? = null,
        leftBlackRook: Position? = null,
        rightBlackRook: Position? = null,
        whiteKingPosition: Position? = null,
        blackKingPosition: Position? = null,
        epTarget: Position? = null
    ) : this(
        plyCounter = 0,
        sideToMoveAdjuster = when (sideToMove) {
            Color.WHITE -> 0
            Color.BLACK -> 1
        },
        halfMoveClock = halfMoveClock,
        fullMoveCounter = fullMoveCounter,
        leftWhiteRook = leftWhiteRook,
        rightWhiteRook = rightWhiteRook,
        leftBlackRook = leftBlackRook,
        rightBlackRook = rightBlackRook,
        whiteKingPosition = whiteKingPosition,
        blackKingPosition = blackKingPosition,
        epTarget = epTarget
    )

    override fun getPlyCounter() = plyCounter
    override fun getSideToMove() = if ((plyCounter + sideToMoveAdjuster) % 2 == 0) Color.WHITE else Color.BLACK
    override fun getHalfMoveClock() = halfMoveClock
    override fun getFullMoveCounter() = fullMoveCounter

    fun incrementPlyCounter() {
        plyCounter++
    }

    fun resetHalfMoveClock() {
        halfMoveClock = 0
    }

    fun incrementHalfMoveClock() {
        halfMoveClock++
    }

    fun incrementFullMoveCounter() {
        fullMoveCounter++
    }

    override fun isKingPresent(color: Color) = when (color) {
        Color.WHITE -> whiteKingPosition != null
        Color.BLACK -> blackKingPosition != null
    }

    fun setKingPosition(position: Position, color: Color): Unit = when (color) {
        Color.WHITE -> whiteKingPosition = position
        Color.BLACK -> blackKingPosition = position
    }

    override fun getKingPosition(color: Color) = when (color) {
        Color.WHITE -> if (whiteKingPosition != null) whiteKingPosition!! else throwMissingKingException(color)
        Color.BLACK -> if (blackKingPosition != null) blackKingPosition!! else throwMissingKingException(color)
    }

    private fun throwMissingKingException(color: Color): Nothing =
        throw BoardException("King of color $color is not set in the board")

    fun hasEpTargetFor(position: Position) = epTarget == position
    fun getEpTarget(): Position = epTarget ?: throw BoardException("No en passant target available")
    fun hasEnPassant() = epTarget != null
    fun clearEpTarget() {
        epTarget = null
    }

    fun setEpTarget(position: Position) {
        epTarget = position
    }

    fun hasLeftCastlingFor(color: Color, position: Position) = when (color) {
        Color.WHITE -> leftWhiteRook == position
        Color.BLACK -> leftBlackRook == position
    }

    fun hasRightCastlingFor(color: Color, position: Position) = when (color) {
        Color.WHITE -> rightWhiteRook == position
        Color.BLACK -> rightBlackRook == position
    }

    override fun hasLeftCastling(color: Color) = when (color) {
        Color.WHITE -> leftWhiteRook != null
        Color.BLACK -> leftBlackRook != null
    }

    override fun hasRightCastling(color: Color) = when (color) {
        Color.WHITE -> rightWhiteRook != null
        Color.BLACK -> rightBlackRook != null
    }

    fun clearLeftRookPosition(color: Color): Unit = when (color) {
        Color.WHITE -> leftWhiteRook = null
        Color.BLACK -> leftBlackRook = null
    }

    fun clearRightRookPosition(color: Color): Unit = when (color) {
        Color.WHITE -> rightWhiteRook = null
        Color.BLACK -> rightBlackRook = null
    }

    override fun getLeftRookPosition(color: Color) = when (color) {
        Color.WHITE -> leftWhiteRook!!
        Color.BLACK -> leftBlackRook!!
    }

    override fun getRightRookPosition(color: Color) = when (color) {
        Color.WHITE -> rightWhiteRook!!
        Color.BLACK -> rightBlackRook!!
    }

    fun set(boardState: BoardStateImpl) {
        plyCounter = boardState.plyCounter
        sideToMoveAdjuster = boardState.sideToMoveAdjuster
        halfMoveClock = boardState.halfMoveClock
        fullMoveCounter = boardState.fullMoveCounter
        leftWhiteRook = boardState.leftWhiteRook
        rightWhiteRook = boardState.rightWhiteRook
        leftBlackRook = boardState.leftBlackRook
        rightBlackRook = boardState.rightBlackRook
        whiteKingPosition = boardState.whiteKingPosition
        blackKingPosition = boardState.blackKingPosition
        epTarget = boardState.epTarget
    }

    override fun copy() = BoardStateImpl(
        plyCounter,
        sideToMoveAdjuster,
        halfMoveClock,
        fullMoveCounter,
        leftWhiteRook,
        rightWhiteRook,
        leftBlackRook,
        rightBlackRook,
        whiteKingPosition,
        blackKingPosition,
        epTarget
    )
}

internal class SquareImpl(
    private val position: Position,
    private var content: SquareContent<*> = EmptySquareContent
) : Square, Copyable<SquareImpl> {

    override fun getPosition() =
        position

    override fun getContent() =
        content

    fun setContent(squareContent: SquareContent<*>) {
        content = squareContent
    }

    override fun getPiece() =
        if (isNotEmpty()) content.asPiece()
        else throw BoardException("Empty square: $position")

    override fun isEmpty() =
        content.isEmpty

    override fun isNotEmpty() =
        content.isNotEmpty

    override fun contains(piece: Piece) =
        content.isPieceOf(piece)

    override fun copy() =
        SquareImpl(position, content)
}

internal fun Position.isPawnPromotionRow() = row == 0 || row == 7

internal fun Position.isSameLine(p2: Position, p3: Position): Boolean {
    val p1 = this
    return p1.column * (p2.row - p3.row) + p2.column * (p3.row - p1.row) + p3.column * (p1.row - p2.row) == 0
}

// =====================================================================================================================
// Movement templates
// =====================================================================================================================

internal data class Direction(val rowDirection: Int, val columnDirection: Int)

private val KING_MOVEMENT_TEMPLATE =
    arrayListOf(
        Direction(-1, -1),
        Direction(-1, +0),
        Direction(-1, +1),
        Direction(+1, -1),
        Direction(+1, +0),
        Direction(+1, +1),
        Direction(+0, -1),
        Direction(+0, +1)
    )

private val queenMovementTemplate =
    arrayListOf(
        Direction(-1, -1),
        Direction(-1, +0),
        Direction(-1, +1),
        Direction(+1, -1),
        Direction(+1, +0),
        Direction(+1, +1),
        Direction(+0, -1),
        Direction(+0, +1)
    )

private val rookMovementTemplate =
    arrayListOf(
        Direction(-1, +0),
        Direction(+1, +0),
        Direction(+0, -1),
        Direction(+0, +1)
    )

private val bishopMovementTemplate =
    arrayListOf(
        Direction(-1, -1),
        Direction(-1, +1),
        Direction(+1, -1),
        Direction(+1, +1)
    )

private val knightMovementTemplate =
    arrayListOf(
        Direction(-2, -1),
        Direction(-2, +1),
        Direction(+2, -1),
        Direction(+2, +1),
        Direction(-1, -2),
        Direction(-1, +2),
        Direction(+1, -2),
        Direction(+1, +2)
    )

private val whitePawnCaptureMovementTemplate =
    arrayListOf(
        Direction(-1, -1),
        Direction(-1, +1)
    )

private val blackPawnCaptureMovementTemplate =
    arrayListOf(
        Direction(+1, -1),
        Direction(+1, +1)
    )

private val whitePawnSingleSquareMovementTemplate =
    arrayListOf(
        Direction(-1, +0)
    )

private val blackPawnSingleSquareMovementTemplate =
    arrayListOf(
        Direction(+1, +0)
    )

private val whitePawnDoubleSquareMovementTemplate =
    arrayListOf(
        Direction(-2, +0)
    )

private val blackPawnDoubleSquareMovementTemplate =
    arrayListOf(
        Direction(+2, +0)
    )
