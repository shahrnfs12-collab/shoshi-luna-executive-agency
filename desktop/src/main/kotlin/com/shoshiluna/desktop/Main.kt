package com.shoshiluna.desktop

import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.*

private const val EXECUTIVE_PROMPT = """
You are SHOSHI/LUNA Executive Agency running on Windows.
The owner gives goals; do not delegate routine work back to the owner.
Infer reasonable details, research/plan proactively, and propose the strongest safe path.
Ask the owner only for irreducible missing information or approval for external commitments,
money spend, credential-sensitive/destructive/irreversible actions, or capability construction.
If a path fails, record why and search materially different alternatives before declaring failure.
For capability acquisition: free-first, then cost/benefit, ROI, risk, evidence, then explicit approval.
Never claim an action, payment, build, verification, or revenue occurred without evidence.
"""

fun main() = SwingUtilities.invokeLater { DesktopFrame().isVisible = true }

class DesktopFrame : JFrame("SHOSHI LUNA DESKTOP") {
    private val transcript = JTextArea()
    private val input = JTextArea(5, 60)
    private val provider = JComboBox(DesktopProvider.entries.toTypedArray())
    private val model = JTextField("openrouter/free", 22)
    private val apiKey = JPasswordField(28)
    private val agent = JComboBox(Agent.entries.toTypedArray())
    private val send = JButton("Send")
    private val plan = JButton("Build Mission Plan")
    private val status = JLabel("Ready • API keys are session-only and are not saved to disk")

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        minimumSize = Dimension(980, 700)
        setLocationRelativeTo(null)

        transcript.isEditable = false
        transcript.lineWrap = true
        transcript.wrapStyleWord = true
        input.lineWrap = true
        input.wrapStyleWord = true

        val top = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(JLabel("Agent:")); add(agent)
            add(JLabel("Provider:")); add(provider)
            add(JLabel("Model:")); add(model)
            add(JLabel("API key:")); add(apiKey)
        }
        val controls = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
            add(plan); add(send)
        }
        val bottom = JPanel(BorderLayout()).apply {
            add(JScrollPane(input), BorderLayout.CENTER)
            add(controls, BorderLayout.SOUTH)
        }

        contentPane.layout = BorderLayout(8, 8)
        contentPane.add(top, BorderLayout.NORTH)
        contentPane.add(JScrollPane(transcript), BorderLayout.CENTER)
        contentPane.add(bottom, BorderLayout.SOUTH)
        contentPane.add(status, BorderLayout.PAGE_END)

        // BorderLayout only keeps one SOUTH/PAGE_END component, so wrap input/status together.
        val south = JPanel(BorderLayout()).apply {
            add(bottom, BorderLayout.CENTER)
            add(status, BorderLayout.SOUTH)
        }
        contentPane.remove(bottom)
        contentPane.remove(status)
        contentPane.add(south, BorderLayout.SOUTH)

        provider.addActionListener {
            model.text = when (provider.selectedItem as DesktopProvider) {
                DesktopProvider.OPENROUTER -> "openrouter/free"
                DesktopProvider.OPENAI -> "gpt-5.6"
            }
        }
        plan.addActionListener { buildPlan() }
        send.addActionListener { sendToAgent() }
        pack()
    }

    private fun buildPlan() {
        val goal = input.text.trim()
        if (goal.isEmpty()) return setStatus("Enter a goal first")
        val mission = runCatching { DesktopPolicies.plan(goal) }.getOrElse {
            return setStatus(it.message ?: "Could not build plan")
        }
        append("MISSION", mission.goal)
        mission.steps.forEachIndexed { i, step -> transcript.append("${i + 1}. $step\n") }
        transcript.append("\n")
        setStatus("Mission plan created • no external action executed")
    }

    private fun sendToAgent() {
        val text = input.text.trim()
        if (text.isEmpty()) return setStatus("Enter a message first")
        val key = String(apiKey.password)
        if (key.isBlank()) return setStatus("Enter an API key for this session")
        val cfg = DesktopAiConfig(provider.selectedItem as DesktopProvider, key, model.text.trim())
        val selectedAgent = agent.selectedItem as Agent
        append("YOU", text)
        input.text = ""
        send.isEnabled = false
        plan.isEnabled = false
        setStatus("$selectedAgent is working…")

        Thread {
            val result = runCatching {
                AiGateway.complete(cfg, "$EXECUTIVE_PROMPT\nYou are ${selectedAgent.name}.", text)
            }
            SwingUtilities.invokeLater {
                result.onSuccess { append(selectedAgent.name, it); setStatus("Ready") }
                    .onFailure { append("ERROR", it.message ?: it.javaClass.simpleName); setStatus("Request failed — review error and try an alternative") }
                send.isEnabled = true
                plan.isEnabled = true
            }
        }.start()
    }

    private fun append(who: String, text: String) {
        transcript.append("[$who]\n$text\n\n")
        transcript.caretPosition = transcript.document.length
    }

    private fun setStatus(text: String) { status.text = text }
}
