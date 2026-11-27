package com.lughati3.spite

import com.lughati3.spite.model.GameId
import com.lughati3.spite.ui.help.guideFor
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideTest {
    @Test fun everyGameHasADetailedGuide() {
        for (g in GameId.entries) {
            val guide = guideFor(g)
            assertTrue("${g.title} summary", guide.summary.isNotBlank())
            assertTrue("${g.title} needs several sections", guide.sections.size >= 4)
            assertTrue("${g.title} has a Goal", guide.sections.any { it.title.startsWith("Goal") })
            assertTrue(guide.sections.all { it.lines.isNotEmpty() && it.lines.all { l -> l.isNotBlank() } })
        }
    }

    @Test fun bankGuideDescribesTheFillThenTakiRule() {
        val text = guideFor(GameId.BANK).sections.flatMap { listOf(it.title) + it.lines }.joinToString(" ")
        assertTrue(text.contains("can't end your turn while any of your 5 ground slots is empty"))
        assertTrue(text.contains("draw 5 new cards"))
        assertTrue(text.contains("only 3 shared piles"))
        assertTrue(text.contains("Stalemate") || text.contains("stalemate"))
    }
}
