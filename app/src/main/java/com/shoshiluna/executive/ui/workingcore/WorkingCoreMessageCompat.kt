package com.shoshiluna.executive.ui.workingcore

import com.shoshiluna.executive.core.agent.ExecutiveAgent
import java.util.UUID

/**
 * Compatibility factory for legacy positional WorkingCoreMessage calls.
 * New code should prefer named arguments. Supplying the primary constructor's
 * id explicitly ensures this factory cannot recursively resolve to itself.
 */
@Suppress("FunctionName")
fun WorkingCoreMessage(
    agent: ExecutiveAgent,
    fromUser: Boolean,
    text: String,
    source: WorkingCoreMessageSource = if (fromUser) WorkingCoreMessageSource.USER else WorkingCoreMessageSource.AI
): WorkingCoreMessage = WorkingCoreMessage(
    id = UUID.randomUUID().toString(),
    agent = agent,
    fromUser = fromUser,
    text = text,
    source = source
)
