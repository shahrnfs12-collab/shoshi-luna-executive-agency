package com.shoshiluna.executive.ui.workingcore

import com.shoshiluna.executive.core.agent.ExecutiveAgent

/**
 * Compatibility factory for legacy positional WorkingCoreMessage calls.
 * New code should prefer named arguments. This overload prevents field-order
 * changes in the data class from silently re-binding positional parameters.
 */
@Suppress("FunctionName")
fun WorkingCoreMessage(
    agent: ExecutiveAgent,
    fromUser: Boolean,
    text: String,
    source: WorkingCoreMessageSource = if (fromUser) WorkingCoreMessageSource.USER else WorkingCoreMessageSource.AI
): WorkingCoreMessage = WorkingCoreMessage(
    agent = agent,
    fromUser = fromUser,
    text = text,
    source = source
)
