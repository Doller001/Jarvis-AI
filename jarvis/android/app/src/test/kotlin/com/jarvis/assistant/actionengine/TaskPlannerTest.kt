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
        assertEquals(5, plan?.steps?.size)

        assertEquals(ActionType.OPEN_APP, plan?.steps?.get(0)?.action)
        assertEquals("youtube", plan?.steps?.get(0)?.parameters?.get("target"))

        assertEquals(ActionType.WAIT, plan?.steps?.get(1)?.action)

        assertEquals(ActionType.SEARCH_TEXT, plan?.steps?.get(2)?.action)
        assertEquals("arijit singh", plan?.steps?.get(2)?.parameters?.get("text"))
        assertEquals(ActionType.CLICK_ELEMENT, plan?.steps?.get(4)?.action)
    }

    @Test
    fun testYouTubeBelieverPlan() {
        val plan = planner.plan("YouTube kholo aur Believer gaana bajao")
        assertNotNull(plan)
        assertEquals("youtube_play_flow", plan?.intent)
        assertEquals(5, plan?.steps?.size)
        assertEquals("believer", plan?.steps?.get(2)?.parameters?.get("text"))
    }

    @Test
    fun testWhatsAppMultiActionPlan() {
        val plan = planner.plan("WhatsApp kholo aur Mom ko Hello bhejo")
        assertNotNull(plan)
        assertEquals("whatsapp_send_flow", plan?.intent)
        assertEquals(2, plan?.steps?.size)

        assertEquals(ActionType.RESOLVE_CONTACT, plan?.steps?.get(0)?.action)
        assertEquals("Mom", plan?.steps?.get(0)?.parameters?.get("contact"))

        assertEquals(ActionType.SEND_MESSAGE, plan?.steps?.get(1)?.action)
        assertEquals("Mom", plan?.steps?.get(1)?.parameters?.get("contact"))
        assertEquals("Hello", plan?.steps?.get(1)?.parameters?.get("message"))
        assertEquals(RiskLevel.MEDIUM, plan?.steps?.get(1)?.riskLevel)
        assertTrue(plan?.steps?.get(1)?.requiresConfirmation == true)
    }

    @Test
    fun testWhatsAppEnglishPlan() {
        val plan = planner.plan("Open WhatsApp and send Meeting at 5 to Alice")
        assertNotNull(plan)
        assertEquals("whatsapp_send_flow", plan?.intent)
        assertEquals(2, plan?.steps?.size)
        assertEquals("Alice", plan?.steps?.get(0)?.parameters?.get("contact"))
        assertEquals("Meeting at 5", plan?.steps?.get(1)?.parameters?.get("message"))
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
        assertEquals(80, plan?.steps?.get(1)?.parameters?.get("level"))
    }

    @Test
    fun testTorchOffAndVolumeDownPlan() {
        val plan = planner.plan("Torch off karo aur volume kam karo")
        assertNotNull(plan)
        assertEquals("system_torch_volume_flow", plan?.intent)
        assertEquals(2, plan?.steps?.size)

        assertEquals(ActionType.TOGGLE_TORCH, plan?.steps?.get(0)?.action)
        assertEquals("off", plan?.steps?.get(0)?.parameters?.get("state"))

        assertEquals(ActionType.VOLUME_SET, plan?.steps?.get(1)?.action)
        assertEquals(30, plan?.steps?.get(1)?.parameters?.get("level"))
    }

    @Test
    fun testChromeMultiActionPlan() {
        val plan = planner.plan("Chrome kholo aur latest tech news search karo")
        assertNotNull(plan)
        assertEquals("chrome_search_flow", plan?.intent)
        assertEquals(3, plan?.steps?.size)
        assertEquals("latest tech news", plan?.steps?.get(2)?.parameters?.get("text"))
    }

    @Test
    fun testSingleCommandReturnsNullForPlanner() {
        val plan = planner.plan("torch on")
        assertNull(plan) // Should be handled by single intent resolver
    }

    @Test
    fun testYouTubeHeadlightsSearchAndPlayPlan() {
        val plan = planner.plan("open youtube and play headlights song")
        assertNotNull(plan)
        assertEquals("headlights song", plan?.steps?.get(2)?.parameters?.get("text"))
        assertEquals(ActionType.CLICK_ELEMENT, plan?.steps?.last()?.action)
    }

    @Test
    fun testCameraSelfiePlan() {
        val plan = planner.plan("open camera and take a selfie")
        assertEquals("camera_selfie_flow", plan?.intent)
        assertEquals(ActionType.TAKE_SELFIE, plan?.steps?.last()?.action)
    }

    @Test
    fun testSamsungMusicPlan() {
        val plan = planner.plan("music open karo or song play karo")
        assertEquals("samsung_music_play_flow", plan?.intent)
        assertEquals(ActionType.PLAY_MEDIA, plan?.steps?.last()?.action)
    }

    @Test
    fun testWhatsAppUnreadPlan() {
        val plan = planner.plan("open whatsapp and read unread messages")
        assertEquals("whatsapp_read_flow", plan?.intent)
        assertEquals(ActionType.READ_MESSAGES, plan?.steps?.last()?.action)
    }
}
