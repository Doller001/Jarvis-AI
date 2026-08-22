package com.jarvis.assistant.actionengine

import com.jarvis.assistant.actionengine.model.ActionType
import com.jarvis.assistant.actionengine.model.RiskLevel
import com.jarvis.assistant.actionengine.planner.LocalTaskPlanner
import org.junit.Assert.*
import org.junit.Test

class TaskPlannerTest {

    private val planner = LocalTaskPlanner()

    @Test
    fun testYouTubeMultiActionPlan() {
        val plan = planner.plan("YouTube kholo aur Arijit Singh ka gaana bajao")
        assertNotNull(plan)
        assertEquals("youtube_play_flow", plan?.intent)
        assertEquals(3, plan?.steps?.size)

        assertEquals(ActionType.OPEN_APP, plan?.steps?.get(0)?.action)
        assertEquals("youtube", plan?.steps?.get(0)?.parameters?.get("target"))

        assertEquals(ActionType.WAIT, plan?.steps?.get(1)?.action)

        assertEquals(ActionType.SEARCH_TEXT, plan?.steps?.get(2)?.action)
        assertTrue((plan?.steps?.get(2)?.parameters?.get("text") as String).contains("arijit singh"))
    }

    @Test
    fun testWhatsAppMultiActionPlan() {
        val plan = planner.plan("WhatsApp kholo aur Mom ko Hello bhejo")
        assertNotNull(plan)
        assertEquals("whatsapp_send_flow", plan?.intent)
        assertEquals(2, plan?.steps?.size)

        assertEquals(ActionType.RESOLVE_CONTACT, plan?.steps?.get(0)?.action)
        assertEquals(ActionType.SEND_MESSAGE, plan?.steps?.get(1)?.action)
        assertEquals(RiskLevel.MEDIUM, plan?.steps?.get(1)?.riskLevel)
        assertTrue(plan?.steps?.get(1)?.requiresConfirmation == true)
    }

    @Test
    fun testTorchAndVolumeMultiActionPlan() {
        val plan = planner.plan("Torch on karo aur volume badhao")
        assertNotNull(plan)
        assertEquals("system_torch_volume_flow", plan?.intent)
        assertEquals(2, plan?.steps?.size)

        assertEquals(ActionType.TOGGLE_TORCH, plan?.steps?.get(0)?.action)
        assertEquals("on", plan?.steps?.get(0)?.parameters?.get("state"))

        assertEquals(ActionType.VOLUME_SET, plan?.steps?.get(1)?.action)
    }

    @Test
    fun testSingleCommandReturnsNullForPlanner() {
        val plan = planner.plan("torch on")
        assertNull(plan) // Should be handled by single intent resolver
    }
}
