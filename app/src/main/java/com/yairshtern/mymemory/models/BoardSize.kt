package com.yairshtern.mymemory.models

import android.content.res.Configuration

enum class BoardSize(val numCards: Int) {
    EASY(8),
    MEDIUM(18),
    HARD(32);

    companion object {
        fun getByValue(value: Int) = entries.first() { it.numCards == value }
    }

    fun getWidth(orientation: Int): Int {
        return if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            when (this) {
                EASY -> 2
                MEDIUM -> 3
                HARD -> 4
            }
        } else {
            when (this) {
                EASY -> 4
                MEDIUM -> 6
                HARD -> 8
            }
        }

    }

    fun getHeight(orientation: Int): Int {
        return numCards / getWidth(orientation)
    }

    fun getNumPairs(): Int {
        return numCards / 2
    }

}