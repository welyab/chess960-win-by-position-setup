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

import com.sun.xml.internal.messaging.saaj.packaging.mime.internet.HeaderTokenizer
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.FileReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringReader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.LinkedList
import kotlin.streams.toList

private const val SUFFIX_GOOD_MOVE = "!"
private const val SUFFIX_POOR_MOVE = "?"
private const val SUFFIX_VERY_GOOD_MOVE = "!!"
private const val SUFFIX_VERY_POOR_MOVE = "??"
private const val SUFFIX_SPECULATIVE_MOVE = "!?"
private const val SUFFIX_QUESTIONABLE_MOVE = "?!"

private const val GAME_RESULT_WHITE_WIN = "1-0"
private const val GAME_RESULT_BLACK_WIN = "0-1"
private const val GAME_RESULT_DRAW = "1/2-1/2"
private const val GAME_RESULT_UNKNOWN = "*"

private const val TAG_NAME_EVENT = "Event"
private const val TAG_NAME_SITE = "Site"
private const val TAG_NAME_DATE = "Date"
private const val TAG_NAME_ROUND = "Round"
private const val TAG_NAME_WHITE = "White"
private const val TAG_NAME_BLACK = "Black"
private const val TAG_NAME_RESULT = "Result"

private val VARIANT_STANDARD = "Standard"
private val VARIANT_FISCHER_RANDOM_CHESS = "Chess960"

class PgnException(message: String, cause: Throwable? = null) : ChessException(message, cause)

enum class SevenTagRoster(val tagName: String) {

    EVENT(TAG_NAME_EVENT),
    SITE(TAG_NAME_SITE),
    DATE(TAG_NAME_DATE),
    ROUND(TAG_NAME_ROUND),
    WHITE(TAG_NAME_WHITE),
    BLACK(TAG_NAME_BLACK),
    RESULT(TAG_NAME_RESULT);
}

enum class SuffixAnnotation(val value: String) {
    GOOD_MOVE("!"),
    POOR_MOVE("?"),
    VERY_GOOD_MOVE("!!"),
    VERY_POOR_MOVE("??"),
    SPECULATIVE_MOVE("!?"),
    QUESTIONABLE_MOVE("?!");

    companion object {
        fun fromValue(value: String) =
            when (value) {
                SUFFIX_GOOD_MOVE -> GOOD_MOVE
                SUFFIX_POOR_MOVE -> POOR_MOVE
                SUFFIX_VERY_GOOD_MOVE -> VERY_GOOD_MOVE
                SUFFIX_VERY_POOR_MOVE -> VERY_POOR_MOVE
                SUFFIX_SPECULATIVE_MOVE -> SPECULATIVE_MOVE
                SUFFIX_QUESTIONABLE_MOVE -> QUESTIONABLE_MOVE
                else -> throw IllegalArgumentException("Invalid sufix: $value")
            }
    }
}

enum class GameResult(val value: String) {

    WHITE_WIN(GAME_RESULT_WHITE_WIN),
    BLACK_WIN(GAME_RESULT_BLACK_WIN),
    DRAW(GAME_RESULT_DRAW),
    UNKNOWN(GAME_RESULT_UNKNOWN);

    override fun toString() = value

    companion object {
        fun from(result: String) = when (result) {
            GAME_RESULT_WHITE_WIN -> WHITE_WIN
            GAME_RESULT_BLACK_WIN -> BLACK_WIN
            GAME_RESULT_DRAW -> DRAW
            GAME_RESULT_UNKNOWN -> UNKNOWN
            else -> throw IllegalArgumentException("Invalid game result: $result")
        }
    }
}

private fun Char.isNewLineChar() = this == '\r' || this == '\n'

private class Context {

    private var gameBuilder: PgnGameBuilder? = null
    private var moveNumber: Int? = null
    private var sideToMove: Color? = null
    private var game: PgnGame? = null
    private var variantLine: Int = 0

    fun getGameBuilder() = gameBuilder!!

    fun resetGameBuilder() {
        gameBuilder = PgnGameBuilder()
    }

    fun getCurrentMoveNumber() = moveNumber!!

    fun setCurrentMoveNumber(moveNumber: Int) {
        this.moveNumber = moveNumber
    }

    fun setCurrentSideToMove(sideToMove: Color) {
        this.sideToMove = sideToMove
    }

    fun getCurrentSideToMove() = sideToMove!!

    fun getCurrentParsedGame() = game!!

    fun setCurrentParsedGame(game: PgnGame) {
        this.game = game
    }

    fun incrementVariantLine() {
        variantLine++
    }

    fun decrementVariantLine() {
        variantLine--
    }

    fun getVariantLine() = variantLine
}

data class Tag(val name: String, val value: String) {
    override fun hashCode() = name.hashCode()
    override fun equals(other: Any?) = name == other
    override fun toString() = "Tag($name = $value)"
}

private class CharStream(private val charSource: BufferedReader) : Iterator<Char> {
    private var prev: Char? = null
    private val queue = LinkedList<Char>()

    override fun hasNext(): Boolean {
        if (queue.isNotEmpty()) return true
        val c = charSource.read()
        if (c < 0) return false
        queue.addLast(c.toChar())
        return true
    }

    override fun next(): Char {
        if (!hasNext()) throw IOException("no more characters")
        val c = queue.removeFirst()!!
        prev = c
        return c
    }

    fun back() {
        if (prev == null) throw IllegalStateException("no previous character to back")
        queue.offerFirst(prev)
        prev = null
    }

    fun nextWhile(predicate: (Char) -> Boolean) = buildString {
        this@CharStream
            .asSequence()
            .takeWhile {
                if (predicate.invoke(it)) {
                    true
                } else {
                    back()
                    false
                }
            }
            .forEach { append(it) }
    }

    fun nextWhileInLine(predicate: (Char) -> Boolean): String {
        val value = nextWhile { !it.isNewLineChar() && predicate.invoke(it) }
        nextWhile { it.isNewLineChar() }
        return value
    }
}

private enum class TokenType {
    TAG_NAME,
    TAG_VALUE,
    MOVE_NUMBER,
    SINGLE_DOT,
    TRIPLE_DOT,
    MOVE,
    COMMENT,
    GAME_RESULT,
    OPEN_PARENTHESIS,
    CLOSE_PARENTHESIS,
    WHITE_SPACES,
    NEW_LINE,
    EOF
}

private data class Token(
    val value: String,
    val type: TokenType
) {
    private fun noNewLineValue() =
        value.replace("\n", "\\n")
            .replace("\r", "\\r")

    override fun toString() = "Token(value='${noNewLineValue()}', type=$type)"
}

private class PgnTokenizer(charSource: BufferedReader) {
    private var prev: Token? = null
    private var prevBackup: Token? = null
    private val stream = CharStream(charSource)

    fun skipWhitespaces() {
        stream.nextWhile { it.isWhitespace() }
    }

    fun nextToken(vararg expectedTypes: TokenType) =
        nextToken().apply {
            if (type !in expectedTypes) throw Exception(
                "Unexpected token type. Found $this, expecting one of ${expectedTypes.contentToString()}"
            )
        }

    fun nextToken(): Token {
        if (prev != null) {
            val token = prev!!
            prev = null
            return token
        }

        if (!stream.hasNext()) return Token("", TokenType.EOF)

        val c = stream.next()
        stream.back()

        var token: Token? = null

        if (c.isWhitespace() && !c.isNewLineChar()) {
            token = Token(
                stream.nextWhileInLine { it.isWhitespace() },
                TokenType.WHITE_SPACES
            )
        }

        if (c.isNewLineChar()) {
            var prev: Char? = null
            val value = stream.nextWhile {
                when {
                    !it.isNewLineChar() -> false
                    prev == null -> {
                        prev = it
                        true
                    }
                    prev == '\r' && it == '\n' -> {
                        prev = it
                        true
                    }
                    else -> false
                }
            }
            token = Token(value, TokenType.NEW_LINE)
        }

        if (c == '[') {
            stream.next()
            token = Token(stream.nextWhile { !it.isWhitespace() }, TokenType.TAG_NAME)
        }

        if (c == '(') {
            stream.next()
            token = Token(c.toString(), TokenType.OPEN_PARENTHESIS)
        }

        if (c == ')') {
            stream.next()
            token = Token(c.toString(), TokenType.CLOSE_PARENTHESIS)
        }

        if (c == '"') {
            stream.next()
            val value = stream.nextWhile { it != '"' }
            stream.next()
            stream.next()
            token = Token(value, TokenType.TAG_VALUE)
        }

        if (c.isDigit()) {
            val value1 = stream.nextWhile { it.isDigit() }
            val value2 = stream.next()
            stream.back()
            token = if (value2 == '-' || value2 == '/') {
                val value3 = stream.nextWhileInLine { !it.isWhitespace() }
                Token(value1 + value3, TokenType.GAME_RESULT)
            } else {
                Token(value1, TokenType.MOVE_NUMBER)
            }
        }

        if (c == '*') {
            stream.next()
            token = Token(c.toString(), TokenType.GAME_RESULT)
        }

        if (c == '.') {
            val value = stream.nextWhile { it == '.' }
            token = when (value) {
                "." -> Token(c.toString(), TokenType.SINGLE_DOT)
                "..." -> Token(c.toString(), TokenType.TRIPLE_DOT)
                else -> throw Exception("Unexpected $value")
            }
        }

        if (c == '{') {
            var stop = false
            val value = stream.nextWhile {
                when {
                    stop -> false
                    it == '}' -> {
                        stop = true
                        true
                    }
                    else -> true
                }
            }
            token = Token(value.trim(), TokenType.COMMENT)
        }

        if (token == null)
            token = Token(
                stream.nextWhile { !it.isWhitespace() && it != ')' && it != '{' },
                TokenType.MOVE
            )

        prevBackup = token
        return token!!
    }

    fun back() {
        if (prevBackup == null) throw Exception("No previous token to back")
        prev = prevBackup
        prevBackup = null
    }
}

//class PgnMove(
//    val origin: LocalizedPiece,
//    val target: LocalizedPiece,
//    val isCheck: Boolean,
//    val isCheckmate: Boolean,
//    val isPromotion: Boolean,
//    val isCapture: Boolean,
//    val isCastling: Boolean,
//    val isEnPassant: Boolean,
//    val isGoodMove: Boolean,
//    val isPoorMove: Boolean,
//    val isVeryGoodMove: Boolean,
//    val isVeryPoorMove: Boolean,
//    val isSpeculativeMove: Boolean,
//    val isQuestionableMove: Boolean
//) {
//}

class PgnMove(
    val number: Int,
    val sideToMove: Color,
    val move: String,
    val comment: String = ""
) {
}

class PgnGame(
    private val tags: Map<String, Tag>,
    private val moves: List<PgnMove>,
    private val result: GameResult
) {
    fun hasTag(tagName: String) = tags.containsKey(tagName)
    fun getTag(tagName: String): Tag {
        if (!hasTag(tagName)) {
            throw PgnException("Not tag present: $tagName")
        }
        return tags[tagName]!!
    }

    fun getTag(tag: SevenTagRoster) = getTag(tag.tagName)

    fun getEventTag() = getTag(SevenTagRoster.EVENT.tagName)
    fun getSiteTag() = getTag(SevenTagRoster.SITE.tagName)
    fun getDateTag() = getTag(SevenTagRoster.DATE.tagName)
    fun getRoundTag() = getTag(SevenTagRoster.ROUND.tagName)
    fun getWhiteTag() = getTag(SevenTagRoster.WHITE.tagName)
    fun getBlackTag() = getTag(SevenTagRoster.BLACK.tagName)
    fun getResultTag() = getTag(SevenTagRoster.RESULT.tagName)

    fun getResult(): GameResult {
        if (!hasTag(SevenTagRoster.RESULT.tagName)) return GameResult.UNKNOWN
        return GameResult.from(getTag(SevenTagRoster.RESULT.tagName).value)
    }

    override fun toString(): String {
        return "Pgn Game = Tags($tags)"
    }
}

private class PgnGameBuilder {

    private val tags = ArrayList<Tag>()
    private val moves = ArrayList<Move>()
    private var gameResult: GameResult = GameResult.UNKNOWN

    fun addMove(
        number: Int,
        sideToMove: Color,
        move: String
    ): PgnGameBuilder {
        moves.add(
            Move(
                number,
                sideToMove,
                move
            )
        )
        return this
    }

    fun addComment(comment: String) {
        moves.last().comment = comment
    }


    fun addTag(tag: Tag): PgnGameBuilder {
        tags += tag
        return this
    }

    fun setGameResult(gameResult: GameResult): PgnGameBuilder {
        this.gameResult = gameResult
        return this
    }

    fun build() = PgnGame(
        tags = tags.asSequence().associateBy({ it.name }, { it }),
        moves = moves.map { it.toPgnMove() },
        result = gameResult
    )

    private data class Move(
        var number: Int,
        var sideToMove: Color,
        var move: String,
        var comment: String = "",
        var variant: ArrayList<Move> = ArrayList()
    )

    private fun Move.toPgnMove() = PgnMove(
        number = this.number,
        sideToMove = this.sideToMove,
        move = this.move,
        comment = this.comment
    )
}

private enum class ParserState {
    START {
        override fun process(tokenizer: PgnTokenizer, context: Context): ParserState {
            tokenizer.skipWhitespaces()
            return GAME
        }
    },
    GAME {
        override fun process(tokenizer: PgnTokenizer, context: Context): ParserState {
            context.resetGameBuilder()
            return TAGS
        }
    },
    TAGS {
        override fun process(tokenizer: PgnTokenizer, context: Context): ParserState {
            val token = tokenizer.nextToken(TokenType.TAG_NAME, TokenType.NEW_LINE)
            if (token.type == TokenType.NEW_LINE) {
                tokenizer.skipWhitespaces()
                return MOVEMENT
            }
            val tagName = token.value
            tokenizer.nextToken(TokenType.WHITE_SPACES)
            val tagValue = tokenizer.nextToken(TokenType.TAG_VALUE).value
            tokenizer.nextToken(TokenType.NEW_LINE)
            context.getGameBuilder().addTag(Tag(tagName, tagValue))
            return TAGS
        }
    },
    MOVEMENT {
        override fun process(tokenizer: PgnTokenizer, context: Context): ParserState {
            val token = tokenizer.nextToken(TokenType.MOVE_NUMBER, TokenType.GAME_RESULT)
            if (token.type == TokenType.GAME_RESULT) {
                tokenizer.back()
                return GAME_RESULT
            }
            context.setCurrentMoveNumber(token.value.toInt())
            val dotsToken = tokenizer.nextToken(TokenType.SINGLE_DOT, TokenType.TRIPLE_DOT)
            context.setCurrentSideToMove(
                when (dotsToken.type) {
                    TokenType.SINGLE_DOT -> Color.WHITE
                    TokenType.TRIPLE_DOT -> Color.BLACK
                    else -> throw PgnException("unexpected error")
                }
            )
            tokenizer.skipWhitespaces()
            return if (dotsToken.type == TokenType.SINGLE_DOT) WHITE_MOVE
            else BLACK_MOVE
        }
    },
    WHITE_MOVE {
        override fun process(tokenizer: PgnTokenizer, context: Context): ParserState {
            val moveToken = tokenizer.nextToken(TokenType.MOVE)
            context.getGameBuilder().addMove(
                number = context.getCurrentMoveNumber(),
                sideToMove = context.getCurrentSideToMove(),
                move = moveToken.value
            )
            tokenizer.skipWhitespaces()
            val token = tokenizer.nextToken(
                TokenType.MOVE_NUMBER,
                TokenType.COMMENT,
                TokenType.MOVE,
                TokenType.CLOSE_PARENTHESIS,
                TokenType.GAME_RESULT
            )
            tokenizer.back()
            return when (token.type) {
                TokenType.MOVE_NUMBER -> MOVEMENT
                TokenType.MOVE -> BLACK_MOVE
                TokenType.COMMENT -> COMMENT
                TokenType.CLOSE_PARENTHESIS -> CLOSE_VARIANT
                else -> GAME_RESULT
            }
        }
    },
    BLACK_MOVE {
        override fun process(tokenizer: PgnTokenizer, context: Context): ParserState {
            val moveToken = tokenizer.nextToken(TokenType.MOVE)
            context.getGameBuilder().addMove(
                number = context.getCurrentMoveNumber(),
                sideToMove = context.getCurrentSideToMove(),
                move = moveToken.value
            )
            tokenizer.skipWhitespaces()
            val token = tokenizer.nextToken(
                TokenType.MOVE_NUMBER,
                TokenType.COMMENT,
                TokenType.GAME_RESULT,
                TokenType.CLOSE_PARENTHESIS
            )
            tokenizer.back()
            return when (token.type) {
                TokenType.MOVE_NUMBER -> MOVEMENT
                TokenType.COMMENT -> COMMENT
                TokenType.CLOSE_PARENTHESIS -> CLOSE_VARIANT
                else -> GAME_RESULT
            }
        }
    },
    COMMENT {
        override fun process(tokenizer: PgnTokenizer, context: Context): ParserState {
            val commentToken = tokenizer.nextToken(TokenType.COMMENT)
            context.getGameBuilder().addComment(commentToken.value)
            tokenizer.skipWhitespaces()
            val token = tokenizer.nextToken(
                TokenType.MOVE,
                TokenType.MOVE_NUMBER,
                TokenType.GAME_RESULT,
                TokenType.COMMENT,
                TokenType.OPEN_PARENTHESIS
            )
            if (token.type == TokenType.EOF) {
                return GAME_END
            }
            tokenizer.back()
            return when (token.type) {
                TokenType.GAME_RESULT -> GAME_RESULT
                TokenType.MOVE_NUMBER -> MOVEMENT
                TokenType.COMMENT -> COMMENT
                TokenType.OPEN_PARENTHESIS -> OPEN_VARIANT
                else -> when (context.getCurrentSideToMove()) {
                    Color.WHITE -> WHITE_MOVE
                    Color.BLACK -> BLACK_MOVE
                }
            }
        }
    },
    OPEN_VARIANT {
        override fun process(tokenizer: PgnTokenizer, context: Context): ParserState {
            tokenizer.nextToken(TokenType.OPEN_PARENTHESIS)
            tokenizer.skipWhitespaces()
            return MOVEMENT
        }
    },
    CLOSE_VARIANT {
        override fun process(tokenizer: PgnTokenizer, context: Context): ParserState {
            tokenizer.nextToken(TokenType.CLOSE_PARENTHESIS)
            tokenizer.skipWhitespaces()
            return MOVEMENT
        }
    },
    GAME_RESULT {
        override fun process(tokenizer: PgnTokenizer, context: Context): ParserState {
            val resultToken = tokenizer.nextToken(TokenType.GAME_RESULT)
            context.getGameBuilder().setGameResult(GameResult.from(resultToken.value))
            val game = context.getGameBuilder().build()
            context.setCurrentParsedGame(game)
            tokenizer.skipWhitespaces()
            return GAME_END
        }
    },
    GAME_END {
        override fun process(tokenizer: PgnTokenizer, context: Context): ParserState {
            val token = tokenizer.nextToken(TokenType.TAG_NAME, TokenType.EOF)
            if (token.type == TokenType.TAG_NAME) {
                tokenizer.back()
                return GAME
            }
            return END
        }
    },
    END {
        override fun process(
            tokenizer: PgnTokenizer,
            context: Context
        ): ParserState {
            throw UnsupportedOperationException("parsing end")
        }
    };

    abstract fun process(
        tokenizer: PgnTokenizer,
        context: Context
    ): ParserState

//    protected fun parseMove(board: Board, san: String): PgnMove {
//        var isCheck = false
//        var isCheckmate = false
//        var isPromotion = false
//        var isCapture = false
//        var isCastling = false
//        var isEnPassant = false
//        var originPieceType = PieceType.PAWN
//        var targetPieceType = PieceType.PAWN
//        var originPosition: Position? = null
//        var targetPosition: Position? = null
//        var isGoodMove = false
//        var isPoorMove = false
//        var isVeryGoodMove = false
//        var isVeryPoorMove = false
//        var isSpeculativeMove = false
//        var isQuestionableMove = false
//
//        var originRow: Int? = null
//        var originColumn: Int? = null
//
//        var index = san.length - 1
//
//        if (san[index] == '!') {
//            index--
//            when (san[index]) {
//                '!' -> {
//                    isVeryGoodMove = true
//                    index--
//                }
//                '?' -> {
//                    isSpeculativeMove = true
//                    index--
//                }
//                else -> isGoodMove = true
//            }
//        }
//
//        if (san[index] == '?') {
//            index--
//            when (san[index]) {
//                '?' -> {
//                    isVeryPoorMove = true
//                    index--
//                }
//                '!' -> {
//                    isQuestionableMove = true
//                    index--
//                }
//                else -> isPoorMove = true
//            }
//        }
//
//        if (san[index] == '#') {
//            isCheckmate = true
//            index--
//        } else if (san[index] == '+') {
//            isCheck = true
//            index--
//        }
//
//        if (san.startsWith("O-O-O")) {
//            originPosition = board.getBoardState().getKingPosition(board.getSideToMove())
//            targetPosition = board.getBoardState().getLeftRookPosition(board.getSideToMove())
//            isCastling = true
//            originPieceType = PieceType.KING
//            targetPieceType = PieceType.KING
//        } else if (san.startsWith("O-O")) {
//            originPosition = board.getBoardState().getKingPosition(board.getSideToMove())
//            targetPosition = board.getBoardState().getRightRookPosition(board.getSideToMove())
//            isCastling = true
//            originPieceType = PieceType.KING
//            targetPieceType = PieceType.KING
//        } else {
//            if (PieceType.isPieceTypeLetter(san[index])) {
//                targetPieceType = PieceType.from(san[index])
//                isPromotion = true
//                index -= 2
//            }
//
//            targetPosition = Position.from("${san[index - 1]}${san[index]}")
//            index -= 2
//
//            if (index >= 0 && san[index] == 'x') {
//                index--
//                isCapture = true
//            }
//
//            if (index >= 0 && san[index] in '1'..'8') {
//                originRow = Position.rankToRow(san[index] - '0')
//                index--
//            }
//            if (index >= 0 && san[index] in 'a'..'h') {
//                originColumn = Position.fileToColumn(san[index])
//                index--
//            }
//
//            if (index >= 0) {
//                originPieceType = PieceType.from(san[index])
//                targetPieceType = originPieceType
//            }
//
//            originPosition = board
//                .getPiecesCanReach(targetPosition!!, board.getSideToMove())
//                .asSequence()
//                .filter { originRow == null || it.position.row == originRow }
//                .filter { originColumn == null || it.position.column == originColumn }
//                .filter { it.piece.type == originPieceType }
//                .map { it.position }
//                .firstOrNull()
//
//            if (originPosition == null) throw Exception("invalid move: $san")
//
//            isEnPassant = originPieceType.isPawn && isCapture && board.isEmpty(targetPosition)
//        }
//
//        return PgnMove(
//            origin = LocalizedPiece(
//                piece = Piece.from(originPieceType, board.getSideToMove()),
//                position = originPosition!!
//            ),
//            target = LocalizedPiece(
//                piece = Piece.from(targetPieceType, board.getSideToMove()),
//                position = targetPosition!!
//            ),
//            isCheck = isCheck,
//            isCastling = isCastling,
//            isEnPassant = isEnPassant,
//            isCheckmate = isCheckmate,
//            isPromotion = isPromotion,
//            isCapture = isCapture,
//            isGoodMove = isGoodMove,
//            isPoorMove = isPoorMove,
//            isVeryGoodMove = isVeryGoodMove,
//            isVeryPoorMove = isVeryPoorMove,
//            isSpeculativeMove = isSpeculativeMove,
//            isQuestionableMove = isQuestionableMove
//        )
//    }
}

private class PgnParser(
    charSource: BufferedReader
) {
    private val tokenizer = PgnTokenizer(charSource)
    private var state = ParserState.START
    private val context = Context()
    private var counter: Int = 0

    fun nextGame(gameConsumer: (Int, PgnGame) -> Unit): Boolean {
        do {
            state = state.process(tokenizer, context)
            if (state == ParserState.GAME_END) {
                val game = context.getCurrentParsedGame()
                counter++
                gameConsumer.invoke(counter, game)
                return true
            }
        } while (state != ParserState.END)
        return false
    }
}

class PgnReader(
    private val charSource: BufferedReader
) : AutoCloseable {

    constructor(pgn: String) : this(BufferedReader(StringReader(pgn)))

    constructor(path: Path) : this(BufferedReader(FileReader(path.toFile())))

    constructor(
        inputStream: InputStream,
        charset: Charset = StandardCharsets.US_ASCII
    ) : this(
        BufferedReader(
            InputStreamReader(
                inputStream,
                charset
            )
        )
    )

    override fun close() {
        charSource.close()
    }
}

fun _main() {
    val parser =
        PgnParser(BufferedReader(FileReader("d:/lichess_chess960_database\\games/lichess_db_chess960_rated_2013-09.pgn")))
    while (parser.nextGame { i, pgnGame ->
//            println(pgnGame)
        });
}

fun main() {
    class Counter {
        var value: Int = 0

        fun increment() {
            value++
        }

        override fun toString() = value.toString()
    }

    val map = HashMap<String, HashMap<GameResult, Counter>>()
    val files = Files.list(Paths.get("D:\\lichess_chess960_database\\games")).toList()
    var parsedGames = 0
    var startTime = System.currentTimeMillis()
    files.asSequence()
        .filter { it.toString().endsWith(".bz2") }
        .sortedByDescending { it.fileName.toString() }
        .forEachIndexed { index, file ->
            BZip2CompressorInputStream(FileInputStream(file.toString()), true).use { compressedPgn ->
                val parser = PgnParser(BufferedReader(InputStreamReader(compressedPgn)))
                while (true) {
                    try {
                        if (!parser.nextGame { _, game ->
                                val fen = game.getTag("FEN").value
                                val setup = fen.substring(fen.lastIndexOf("/") + 1)
                                val result = game.getResult()
                                map.computeIfAbsent(setup) { _ -> HashMap<GameResult, Counter>() }
                                    .computeIfAbsent(result) { _ -> Counter() }.increment()
                            }) break
                        parsedGames++
                    } catch (e: Exception) {
                        throw e
                    }
                }
                println("================================================================================")
                val velocity = parsedGames / ((System.currentTimeMillis() - startTime) / 1000.0)
                println("Velocity: ${"%.4f".format(velocity)} games/sec")
                println("$file")
                println("${index + 1}/${files.size}")
            }
        }
    println("================================================================================")
    println("Totals:")
    map.forEach {
        println("${it.key} = ${it.value}")
    }
}
