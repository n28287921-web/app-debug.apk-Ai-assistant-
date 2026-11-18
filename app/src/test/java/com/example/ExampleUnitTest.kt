package com.example

import com.example.data.model.ActionType
import com.example.data.model.TaskCategory
import com.example.domain.InfoQuery
import com.example.domain.JarvisCommandEngine
import com.example.domain.MathEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `math evaluator calculates arithmetic expressions accurately`() {
        assertEquals(4500.0, MathEvaluator.evaluate("250 * 18"), 0.001)
        assertEquals(21.0, MathEvaluator.evaluate("(3 + 4) * 3"), 0.001)
        assertEquals(8.0, MathEvaluator.evaluate("2 ^ 3"), 0.001)
    }

    @Test
    fun `command engine parses Hindi and English alarms`() {
        val en = JarvisCommandEngine.parseCommand("Hey Jarvis set alarm for 7:30 am")
        assertTrue(en.handledLocally)
        assertEquals(ActionType.SET_ALARM, en.actions.first().type)
        assertEquals(7, en.actions.first().hour)
        assertEquals(30, en.actions.first().minute)

        val hi = JarvisCommandEngine.parseCommand("हे जार्विस सुबह 6 बजे का अलार्म लगाओ")
        assertTrue(hi.handledLocally)
        assertEquals("hi", hi.languageTag)
        assertEquals(ActionType.SET_ALARM, hi.actions.first().type)
        assertEquals(6, hi.actions.first().hour)
    }

    @Test
    fun `command engine requires confirmation for calls SMS WhatsApp and Facebook posts`() {
        val call = JarvisCommandEngine.parseCommand("Call Mom")
        assertTrue(call.actions.first().requiresConfirmation)
        assertEquals(ActionType.CALL_CONTACT, call.actions.first().type)

        val sms = JarvisCommandEngine.parseCommand("Send SMS to Rahul saying On my way")
        assertTrue(sms.actions.first().requiresConfirmation)
        assertEquals(ActionType.SEND_SMS, sms.actions.first().type)

        val fb = JarvisCommandEngine.parseCommand("Prepare Facebook post saying Enjoying the sunset")
        assertTrue(fb.actions.first().requiresConfirmation)
        assertEquals(ActionType.FACEBOOK_POST, fb.actions.first().type)
    }

    @Test
    fun `command engine detects weather news and birthday reminders`() {
        val weather = JarvisCommandEngine.parseCommand("Weather in Mumbai")
        assertTrue(weather.infoQuery is InfoQuery.Weather)
        assertEquals("Mumbai", (weather.infoQuery as InfoQuery.Weather).city)

        val birthday = JarvisCommandEngine.parseCommand("Add birthday reminder for Priya")
        assertEquals(TaskCategory.BIRTHDAY, birthday.actions.first().taskCategory)

        val openEnded = JarvisCommandEngine.parseCommand("Explain quantum computing in simple Hindi")
        assertFalse(openEnded.handledLocally)
    }
}
