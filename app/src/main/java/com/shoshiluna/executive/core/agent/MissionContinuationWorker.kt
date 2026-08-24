package com.shoshiluna.executive.core.agent

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shoshiluna.executive.core.empire.EmpireMissionControl
import com.shoshiluna.executive.core.empire.MissionStatus
import com.shoshiluna.executive.core.engine.TitanEngine

class MissionContinuationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // WorkManager may recreate the process without ever opening MainActivity.
        TitanEngine.initialize(applicationContext)
        val missionId = inputData.getString(KEY_MISSION_ID) ?: return Result.failure()
        val initiatorId = inputData.getString(KEY_INITIATOR_ID) ?: return Result.failure()
        val targetId = inputData.getString(KEY_TARGET_ID) ?: return Result.failure()
        val topic = inputData.getString(KEY_TOPIC) ?: return Result.failure()
        val mission = EmpireMissionControl.state.value.missions.firstOrNull { it.id == missionId } ?: return Result.failure()
        if (EmpireMissionControl.state.value.emergencyHalt || mission.status !in resumableStatuses) return Result.success()
        return runCatching {
            CollaborationEngine.runConsultation(missionId, initiatorId, targetId, topic)
        }.fold(
            onSuccess = {
                EmpireMissionControl.state.value.missions.firstOrNull { it.id == missionId }?.let { updated ->
                    BackgroundMissionNotifications.show(applicationContext, updated.id, updated.title, updated.status)
                }
                Result.success()
            },
            onFailure = {
                if (runAttemptCount < 3) Result.retry() else {
                    BackgroundMissionNotifications.show(applicationContext, mission.id, mission.title, MissionStatus.FAILED)
                    Result.failure()
                }
            }
        )
    }

    companion object {
        private const val KEY_MISSION_ID = "mission_id"
        private const val KEY_INITIATOR_ID = "initiator_id"
        private const val KEY_TARGET_ID = "target_id"
        private const val KEY_TOPIC = "topic"
        private val resumableStatuses = setOf(MissionStatus.RUNNING, MissionStatus.EXECUTING, MissionStatus.WAITING_FOR_EXTERNAL_RESPONSE)

        fun schedule(context: Context, missionId: String, initiatorId: String, targetId: String, topic: String) {
            val data = Data.Builder()
                .putString(KEY_MISSION_ID, missionId)
                .putString(KEY_INITIATOR_ID, initiatorId)
                .putString(KEY_TARGET_ID, targetId)
                .putString(KEY_TOPIC, topic)
                .build()
            val request = OneTimeWorkRequestBuilder<MissionContinuationWorker>()
                .setInputData(data)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("mission-$missionId", ExistingWorkPolicy.KEEP, request)
        }
    }
}
