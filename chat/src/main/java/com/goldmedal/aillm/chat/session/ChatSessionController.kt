package com.goldmedal.aillm.chat.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A process-wide "start a new conversation" signal.
 *
 * The History tab's + button does not own a chat surface, and the Chat tab may
 * already be holding a conversation — including one restored from the back
 * stack, which is what makes a plain navigation to Chat show the old thread
 * again. Bumping a counter here lets every live ChatViewModel clear itself,
 * whichever route it belongs to.
 */
@Singleton
class ChatSessionController @Inject constructor() {

    private val _newChatRequests = MutableStateFlow(0L)

    /** Increments once per request. Observers skip the initial value. */
    val newChatRequests: StateFlow<Long> = _newChatRequests.asStateFlow()

    fun requestNewChat() {
        _newChatRequests.value += 1L
    }
}
