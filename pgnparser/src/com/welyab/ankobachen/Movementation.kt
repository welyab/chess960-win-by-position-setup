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

class MovementationException(message: String) : ChessException(message)

class MovementFlags(
    private val isCapture: Boolean = false,
    private val isEnPassant: Boolean = false,
    private val isCastling: Boolean = false,
    private val isPromotion: Boolean = false,
    private val isCheck: Boolean = false,
    private val isDiscovery: Boolean = false,
    private val isDouble: Boolean = false,
    private val isCheckmate: Boolean = false,
    private val isStalemate: Boolean = false
) : Copyable<MovementFlags> {
    fun isCapture() = isCapture
    fun isEnPassant() = isEnPassant
    fun isCastling() = isCastling
    fun isPromotion() = isPromotion
    fun isCheck() = isCheck
    fun isDiscovery() = isDiscovery
    fun isDouble() = isDouble
    fun isCheckmate() = isCheckmate
    fun isStalemate() = isStalemate

    override fun copy() = this

    override fun toString() =
        arrayListOf<String>().apply {
                "capture".takeIf { isCapture }?.let { this.add(it) }
                "en passant".takeIf { isEnPassant }?.let { this.add(it) }
                "castling".takeIf { isCastling }?.let { this.add(it) }
                "promotion".takeIf { isPromotion }?.let { this.add(it) }
                "check".takeIf { isCheck }?.let { this.add(it) }
                "discovery".takeIf { isDiscovery }?.let { this.add(it) }
                "double".takeIf { isDouble }?.let { this.add(it) }
                "checkmate".takeIf { isCheckmate }?.let { this.add(it) }
                "stalemate".takeIf { isStalemate }?.let { this.add(it) }
            }.takeIf { it.isNotEmpty() }
            ?.let { it.reduce { s1, s2 -> "$s1, $s2" } }
            ?: "no flags"
}

class MovementDestination(
    private val target: LocalizedPiece,
    private val flags: MovementFlags
) : Copyable<MovementDestination> {

    fun getTarget() = target
    fun getFlags() = flags

    override fun copy() = this

    override fun toString() =
        target.toString().let { targetStrign ->
            flags.toString().takeIf { it.isNotEmpty() }?.let { flagsString ->
                "$targetStrign, $flagsString"
            } ?: targetStrign
        }
}

fun List<MovementDestination>.summarizeFlags() =
    let { list ->
        MovementMetadata
            .builder()
            .apply { list.forEach { add(it.getFlags()) } }
            .build()
    }

class MovementMetadata internal constructor(
    private val movementsCounter: Long = 0,
    private val capturesCounter: Long = 0,
    private val enPassantsCounter: Long = 0,
    private val castlingsCounter: Long = 0,
    private val promotionsCounter: Long = 0,
    private val checksCounter: Long = 0,
    private val discoveriesCounter: Long = 0,
    private val doublesCounter: Long = 0,
    private val checkmatesCounter: Long = 0,
    private val stalematesCounter: Long = 0
) {

    fun movementsCount() = movementsCounter
    fun capturesCount() = capturesCounter
    fun enPassantsCount() = enPassantsCounter
    fun castlingsCount() = castlingsCounter
    fun promotionsCount() = promotionsCounter
    fun checksCount() = checksCounter
    fun discoveriesCount() = discoveriesCounter
    fun doublesCount() = doublesCounter
    fun checkmatesCount() = checkmatesCounter
    fun stalematesCount() = stalematesCounter

    class Builder internal constructor() {
        private var movementsCounter = 0L
        private var capturesCounter = 0L
        private var enPassantsCounter = 0L
        private var castlingsCounter = 0L
        private var promotionsCounter = 0L
        private var checksCounter = 0L
        private var discoveriesCounter = 0L
        private var doublesCounter = 0L
        private var checkmatesCounter = 0L
        private var stalematesCounter = 0L

        fun add(flags: MovementFlags): Builder {
            addMovementsCount()
            if (flags.isCapture()) addCapturesCount()
            if (flags.isEnPassant()) addEnPassantsCount()
            if (flags.isCastling()) addCastlingsCount()
            if (flags.isPromotion()) addPromotionsCount()
            if (flags.isCheck()) addChecksCount()
            if (flags.isDiscovery()) addDiscoveriesCount()
            if (flags.isDouble()) addDoublesCount()
            if (flags.isCheckmate()) addCheckmatesCount()
            if (flags.isStalemate()) addStalematesCount()
            return this
        }

        fun add(metadata: MovementMetadata): Builder {
            addMovementsCount(metadata.movementsCounter)
            addCapturesCount(metadata.capturesCounter)
            addEnPassantsCount(metadata.enPassantsCounter)
            addCastlingsCount(metadata.castlingsCounter)
            addPromotionsCount(metadata.promotionsCounter)
            addChecksCount(metadata.checksCounter)
            addDiscoveriesCount(metadata.discoveriesCounter)
            addDoublesCount(metadata.doublesCounter)
            addCheckmatesCount(metadata.checkmatesCounter)
            addStalematesCount(metadata.stalematesCounter)
            return this
        }

        fun addMovementsCount(movementsCount: Long = 1): Builder {
            this.movementsCounter += movementsCount
            return this
        }

        fun addCapturesCount(capturesCount: Long = 1): Builder {
            this.capturesCounter += capturesCount
            return this
        }

        fun addEnPassantsCount(enPassantsCount: Long = 1): Builder {
            this.enPassantsCounter += enPassantsCount
            return this
        }

        fun addCastlingsCount(castlingsCount: Long = 1): Builder {
            this.castlingsCounter += castlingsCount
            return this
        }

        fun addPromotionsCount(promotionsCount: Long = 1): Builder {
            this.promotionsCounter += promotionsCount
            return this
        }

        fun addChecksCount(checksCount: Long = 1): Builder {
            this.checksCounter += checksCount
            return this
        }

        fun addDiscoveriesCount(discoveriesCount: Long = 1): Builder {
            this.discoveriesCounter += discoveriesCount
            return this
        }

        fun addDoublesCount(doublesCount: Long = 1): Builder {
            this.doublesCounter += doublesCount
            return this
        }

        fun addCheckmatesCount(checkmatesCount: Long = 1): Builder {
            this.checkmatesCounter += checkmatesCount
            return this
        }

        fun addStalematesCount(stalematesCount: Long = 1): Builder {
            this.stalematesCounter += stalematesCount
            return this
        }

        fun build() = MovementMetadata(
            movementsCounter,
            capturesCounter,
            enPassantsCounter,
            castlingsCounter,
            promotionsCounter,
            checksCounter,
            discoveriesCounter,
            doublesCounter,
            checkmatesCounter,
            stalematesCounter
        )
    }

    override fun toString() = buildString {
        append("movements = ").append(movementsCounter).append(", ")
        append("captures = ").append(capturesCounter).append(", ")
        append("enPassants = ").append(enPassantsCounter).append(", ")
        append("castlings = ").append(castlingsCounter).append(", ")
        append("promotions = ").append(promotionsCounter).append(", ")
        append("checks = ").append(checksCounter).append(", ")
        append("discoveries = ").append(discoveriesCounter).append(", ")
        append("doubles = ").append(doublesCounter).append(", ")
        append("checkmates = ").append(checkmatesCounter).append(", ")
        append("stalemates = ").append(stalematesCounter)
    }

    companion object {
        fun builder() = Builder()
    }
}

class PieceMovement(
    private val origin: LocalizedPiece,
    private val destination: MovementDestination
) : Copyable<PieceMovement> {

    fun getOrigin() =
        origin

    fun getDestination() =
        destination

    fun getFlags() =
        destination.getFlags()

    override fun copy() = this

    override fun toString() = "${origin.position.getSanNotation()} " +
            "-> ${destination.getTarget().position.getSanNotation()}=${destination.getTarget().piece.letter}, ${getFlags()}"
}

class PieceMovements(
    private val origin: LocalizedPiece,
    private val destinations: List<MovementDestination>,
    private val metadata: MovementMetadata
) : Copyable<PieceMovements> {
    fun getOrigin() = origin

    fun getDestinationsCount() =
        destinations.size

    fun getMetadata() =
        metadata

    fun isEmpty() =
        destinations.isEmpty()

    fun isNotEmpty() =
        destinations.isNotEmpty()

    fun getDestination(index: Int) =
        destinations[index]

    fun asSequenceOfDestinations() =
        destinations
            .asSequence()
            .map { PieceMovement(origin, it) }

    override fun copy() =
        this

    fun forEachDestination(action: (origin: LocalizedPiece, destination: MovementDestination) -> Unit): Unit =
        asSequenceOfDestinations().forEach { action(it.getOrigin(), it.getDestination()) }
}

fun List<PieceMovements>.summarizeMetadata() =
    let { list ->
        MovementMetadata
            .builder()
            .apply { list.forEach { add(it.getMetadata()) } }
            .build()
    }

class Movements(
    private val origins: List<PieceMovements>,
    private val metadata: MovementMetadata
) : Iterable<PieceMovement> {

    fun getOriginsCount() =
        origins.size

    fun getMetadata() =
        metadata

    fun isEmpty() =
        origins.isEmpty()

    fun isNotEmpty() =
        origins.isNotEmpty()

    fun getDestinationsCount() =
        asSequenceOfOrigins().map { it.getDestinationsCount() }.sum()

    fun getPieceMovement(index: Int) =
        origins[index]

    fun asSequenceOfOrigins() =
        origins.asSequence()

    fun forEachOrigin(action: (origin: PieceMovements) -> Unit): Unit =
        asSequenceOfOrigins().forEach { pieceMovement ->
            action.invoke(pieceMovement)
        }

    fun toListDestinations() =
        asSequenceOfDestinations().toList()

    fun asSequenceOfDestinations() =
        asSequenceOfOrigins()
            .flatMap { it.asSequenceOfDestinations() }

    fun forEachDestination(action: (pieceMovement: PieceMovement) -> Unit): Unit =
        asSequenceOfDestinations().forEach { action.invoke(it) }

    operator fun plus(movements: Movements) =
        Movements(
            origins + movements.origins,
            MovementMetadata.builder().add(metadata).add(movements.metadata).build()
        )

    override fun iterator() =
        asSequenceOfDestinations().iterator()
}
