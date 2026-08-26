package com.shoshiluna.executive.core.agent

import com.shoshiluna.executive.core.audit.AuditTrail
import com.shoshiluna.executive.core.audit.ExecutiveAudit
import com.shoshiluna.executive.core.empire.EmpireMissionControl

/**
 * Deterministic command bridge for explicit USER commands.
 *
 * This deliberately does not allow an LLM response to directly execute a command.
 * The raw text handled here is the user's own chat input. Unknown text is returned
 * as NotHandled and may then be sent to the AI provider as normal conversation.
 */
sealed class CommandBridgeResult {
    data object NotHandled : CommandBridgeResult()
    data class Executed(
        val message: String,
        val command: String,
        val evidenceAuditId: String
    ) : CommandBridgeResult()
    data class Rejected(
        val message: String,
        val command: String
    ) : CommandBridgeResult()
    data class ClarificationRequired(
        val message: String,
        val command: String
    ) : CommandBridgeResult()
}

class CommandExecutionBridge {

    fun tryExecute(rawUserText: String, selectedAgent: ExecutiveAgent): CommandBridgeResult {
        val text = rawUserText.trim()
        if (text.isBlank()) return CommandBridgeResult.NotHandled
        val normalized = normalize(text)

        return when {
            normalized in HELP_COMMANDS -> executed("HELP", helpText())
            normalized in STATUS_COMMANDS -> executed("STATUS", statusText())
            normalized in MISSIONS_COMMANDS -> executed("MISSIONS", missionsText())
            normalized in HALT_COMMANDS -> executeHalt()
            normalized in RELEASE_COMMANDS -> CommandBridgeResult.Rejected(
                message = "שחרור Emergency Halt דורש אישור מפורש דרך כפתור הבטיחות בממשק.",
                command = "RELEASE_HALT"
            )
            normalized in VAGUE_WORK_COMMANDS -> CommandBridgeResult.ClarificationRequired(
                message = "כמה כסף אתה רוצה שהסוכנות תייצר, ובאיזה טווח זמן? אם לא תגדיר תקציב ורמת סיכון, אתחיל מתכנית ללא הוצאה כספית ואביא אותה לאישור לפני כל פעולה חיצונית או כספית.",
                command = "CLARIFY_REVENUE_GOAL"
            )
            extractMissionTitle(text) != null -> executeCreateMission(
                title = requireNotNull(extractMissionTitle(text)),
                selectedAgent = selectedAgent
            )
            extractAutonomousGoal(text) != null -> executeAutonomousGoal(
                goal = requireNotNull(extractAutonomousGoal(text)),
                selectedAgent = selectedAgent
            )
            else -> CommandBridgeResult.NotHandled
        }
    }

    private fun executeAutonomousGoal(goal: String, selectedAgent: ExecutiveAgent): CommandBridgeResult = runCatching {
        val receipt = AutonomousMissionEngine.submitGoal(goal, selectedAgent)
        executed(
            command = "AUTONOMOUS_GOAL",
            message = "המשימה נפתחה והעבודה חולקה בין SHOSHI ל-LUNA: ${receipt.mission.title} [${receipt.mission.id.take(8)}] · ${receipt.plan.size} שלבים"
        )
    }.getOrElse { error ->
        CommandBridgeResult.Rejected(
            message = "המשימה נחסמה: ${error.message ?: "Unknown mission error"}",
            command = "AUTONOMOUS_GOAL"
        )
    }

    private fun executeCreateMission(title: String, selectedAgent: ExecutiveAgent): CommandBridgeResult {
        if (title.isBlank()) return CommandBridgeResult.Rejected("שם המשימה חסר.", "CREATE_MISSION")
        return runCatching {
            val mission = EmpireMissionControl.createMission(title.trim(), selectedAgent.id)
            executed(
                command = "CREATE_MISSION",
                message = "נוצרה משימה בפועל: ${mission.title} [${mission.id.take(8)}] · ${mission.status}"
            )
        }.getOrElse { error ->
            CommandBridgeResult.Rejected(
                message = "הפקודה נחסמה: ${error.message ?: "Unknown command execution error"}",
                command = "CREATE_MISSION"
            )
        }
    }

    private fun executeHalt(): CommandBridgeResult {
        EmpireMissionControl.emergencyHalt()
        return executed(
            command = "EMERGENCY_HALT",
            message = "Emergency Halt הופעל בפועל. כל המשימות הלא-סופיות הועברו ל-HALTED."
        )
    }

    private fun statusText(): String {
        val state = EmpireMissionControl.state.value
        return "מצב הסוכנות: ${if (state.emergencyHalt) "HALTED" else "ACTIVE"} · משימות: ${state.missions.size}"
    }

    private fun missionsText(): String {
        val missions = EmpireMissionControl.state.value.missions
        if (missions.isEmpty()) return "אין משימות כרגע."
        return buildString {
            appendLine("משימות הסוכנות:")
            missions.forEach { appendLine("• ${it.title} [${it.status}] #${it.id.take(8)}") }
        }.trimEnd()
    }

    private fun helpText(): String = """
        פקודות פעילות:
        • סטטוס
        • משימות
        • צרי משימה <שם>
        • צור משימה <שם>
        • mission <name>
        • עצור הכל
        שחרור Emergency Halt מתבצע רק מכפתור הבטיחות בממשק.
    """.trimIndent()

    private fun executed(command: String, message: String): CommandBridgeResult.Executed {
        val audit = ExecutiveAudit(
            agentId = "USER_COMMAND",
            action = "CHAT_COMMAND_$command",
            result = message
        )
        AuditTrail.append(audit)
        return CommandBridgeResult.Executed(message, command, audit.auditId)
    }

    private fun extractMissionTitle(original: String): String? {
        val trimmed = original.trim()
        val lower = trimmed.lowercase()
        val englishPrefixes = listOf("mission ", "create mission ")
        englishPrefixes.firstOrNull { lower.startsWith(it) }?.let { prefix ->
            return trimmed.substring(prefix.length).trim().takeIf { it.isNotBlank() }
        }
        val hebrewPrefixes = listOf(
            "צרי משימה ", "צור משימה ", "תצרי משימה ", "תיצור משימה ",
            "תצרו משימה ", "פתחי משימה ", "פתח משימה "
        )
        hebrewPrefixes.firstOrNull { trimmed.startsWith(it) }?.let { prefix ->
            return trimmed.removePrefix(prefix).trim().takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun normalize(value: String): String = value.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun extractAutonomousGoal(original: String): String? {
        val trimmed = original.trim()
        val prefixes = listOf(
            "תבנו לי ", "תבני לי ", "תעשו לי ", "תעשי לי ", "תכינו לי ", "תכיני לי ",
            "בנו לי ", "בני לי ", "build me ", "create for me "
        )
        return prefixes.firstOrNull { trimmed.lowercase().startsWith(it) }
            ?.let { trimmed.substring(it.length).trim().takeIf(String::isNotBlank) }
    }

    companion object {
        private val HELP_COMMANDS = setOf("help", "/help", "עזרה", "פקודות")
        private val STATUS_COMMANDS = setOf("status", "סטטוס", "מצב", "מה המצב")
        private val MISSIONS_COMMANDS = setOf("missions", "משימות", "הצג משימות", "תראי משימות")
        private val HALT_COMMANDS = setOf(
            "halt", "emergency halt", "עצור", "עצרי", "עצור הכל", "עצרי הכל", "עצירת חירום"
        )
        private val RELEASE_COMMANDS = setOf("release", "release halt", "שחרר", "שחררי", "שחרר עצירת חירום", "שחררי עצירת חירום")
        private val VAGUE_WORK_COMMANDS = setOf(
            "לכי לעבוד", "לך לעבוד", "לכו לעבוד", "תעבדי", "תעבדו", "תתחילי לעבוד", "תתחילו לעבוד",
            "תעשי כסף", "תעשו כסף", "תכניסי כסף", "תכניסו כסף", "תייצרי כסף", "תייצרו כסף",
            "go work", "start working", "make money", "go make money"
        )
    }
}
