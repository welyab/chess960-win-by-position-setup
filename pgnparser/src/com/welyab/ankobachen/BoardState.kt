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

interface BoardState {
    fun getPlyCounter(): Int

    fun getSideToMove(): Color

    fun getHalfMoveClock(): Int
    fun getFullMoveCounter(): Int

    fun isKingPresent(color: Color): Boolean
    fun isNotKingPresent(color: Color) = !isKingPresent(color)
    fun getKingPosition(color: Color): Position

    fun hasLeftCastling(color: Color): Boolean
    fun hasRightCastling(color: Color): Boolean

    fun getLeftRookPosition(color: Color): Position
    fun getRightRookPosition(color: Color): Position
}
