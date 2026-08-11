package com.joon.ringout.presentation.destination.components

import kotlin.test.Test
import kotlin.test.assertEquals

class DestinationShortcutLabelTest {
    @Test
    fun labelShowsUpToFiveCharactersWithoutTruncation() {
        assertEquals("목적지라벨", "목적지라벨".toDestinationShortcutLabel())
    }

    @Test
    fun labelKeepsFiveCharactersAndAddsEllipsisWhenLonger() {
        assertEquals("공부하러가…", "공부하러가야지정말".toDestinationShortcutLabel())
    }
}
