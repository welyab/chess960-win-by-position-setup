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

class ColorException(message: String) : ChessException(message, null)

enum class Color {

    WHITE {
        override val opposite get() = BLACK
        override val letter = WHITE_LETTER

        override val isWhite = true
        override val isBlack = false
    },
    BLACK {
        override val opposite get() = WHITE
        override val letter = BLACK_LETTER

        override val isWhite = false
        override val isBlack = true
    };

    abstract val opposite: Color
    abstract val letter: Char

    abstract val isWhite: Boolean
    abstract val isBlack: Boolean

    override fun toString() = name.toLowerCase().capitalize()

    companion object {
        fun from(letter: Char) = when (letter) {
            WHITE_LETTER -> WHITE
            BLACK_LETTER -> BLACK
            else -> throw ColorException("Unexpected color letter: $letter")
        }
    }
}
