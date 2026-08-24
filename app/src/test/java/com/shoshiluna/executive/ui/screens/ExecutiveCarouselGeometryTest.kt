package com.shoshiluna.executive.ui.screens

import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutiveCarouselGeometryTest {
    @Test
    fun panelPortraitsAndButtonNeverOverlap() {
        assertTrue(ExecutiveCarouselGeometry.PANEL_HEIGHT < ExecutiveCarouselGeometry.portraitTop)
        assertTrue(ExecutiveCarouselGeometry.portraitBottom < ExecutiveCarouselGeometry.buttonTop)
        assertTrue(ExecutiveCarouselGeometry.buttonTop + ExecutiveCarouselGeometry.BUTTON_HEIGHT <= ExecutiveCarouselGeometry.CONTAINER_HEIGHT)
    }
}
