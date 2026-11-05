package com.mehmetarif.sadegorev.reminder

import org.junit.Assert.*
import org.junit.Test

class ReminderChoiceTest {
    private val due=2_000_000_000_000L
    @Test fun newTaskDefaultsToDueTimeWithoutExtraSelection(){
        val mode=initialReminderChoice(false,null,null)
        assertEquals(due,reminderTime(mode,due,null))
        assertEquals("Görev saatinde",reminderLabel(mode,due))
    }
    @Test fun taskWithoutDateHasNoAlarm(){assertNull(reminderTime(0,null,null))}
    @Test fun existingDisabledReminderStaysDisabled(){
        val mode=initialReminderChoice(true,due,null)
        assertEquals(NO_REMINDER,mode);assertNull(reminderTime(mode,due+1000,null))
    }
    @Test fun addingDateToUndatedTaskDefaultsToDueTime(){assertEquals(0,initialReminderChoice(true,null,null))}
    @Test fun presetsAreIdentifiedAndFollowDateChanges(){
        for(minutes in listOf(0,5,15,30,60)){
            val mode=initialReminderChoice(true,due,due-minutes*60000L)
            assertEquals(minutes,mode)
            assertEquals(due+3600000L-minutes*60000L,reminderTime(mode,due+3600000L,null))
        }
    }
    @Test fun disablingReminderDoesNotReenableWhenDateChanges(){assertNull(reminderTime(NO_REMINDER,due+3600000L,due))}
    @Test fun customTimeRemainsAbsoluteWhenDueChanges(){
        val custom=due-7*60000L
        val mode=initialReminderChoice(true,due,custom)
        assertEquals(CUSTOM_REMINDER,mode)
        assertEquals(custom,reminderTime(mode,due+3600000L,custom))
    }
    @Test fun removingDateClearsEvenCustomReminder(){assertNull(reminderTime(CUSTOM_REMINDER,null,due))}
}
