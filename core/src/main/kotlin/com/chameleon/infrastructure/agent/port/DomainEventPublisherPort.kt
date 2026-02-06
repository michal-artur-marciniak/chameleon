package com.chameleon.agent.port

import com.chameleon.agent.domain.AgentLoopDomainEvent

/**
 * Port for publishing domain events.
 * Infrastructure adapters implement this to publish events to various subscribers.
 */
interface DomainEventPublisherPort {
    fun publish(event: AgentLoopDomainEvent)
}
