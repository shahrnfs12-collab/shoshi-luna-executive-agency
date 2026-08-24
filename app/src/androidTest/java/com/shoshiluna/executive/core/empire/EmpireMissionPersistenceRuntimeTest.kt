package com.shoshiluna.executive.core.empire

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shoshiluna.executive.core.business.Environment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmpireMissionPersistenceRuntimeTest {
    @Test
    fun missionStateSurvivesStoreRecreation() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("empire_missions_v1", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val mission = EmpireMission(
            title = "בדיקת שחזור",
            ownerId = "SHOSHI",
            environment = Environment.SIMULATION,
            workPlan = listOf("1|SHOSHI|false|בדיקה")
        )
        EmpireMissionStore(context).save(EmpireState(missions = listOf(mission)))

        val restored = EmpireMissionStore(context).load()

        assertFalse(restored.emergencyHalt)
        assertEquals(1, restored.missions.size)
        assertEquals("בדיקת שחזור", restored.missions.single().title)
        assertEquals(mission.workPlan, restored.missions.single().workPlan)
    }
}
