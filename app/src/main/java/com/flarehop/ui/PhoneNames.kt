package com.flarehop.ui

import com.flarehop.flare.SosRecord

// Phone IDs are random per session (Contract 1), which means nothing to a person. Each phone gets
// a small number the first time this phone hears it, and keeps it for the whole session, so a
// card never changes name while someone is reading it. Numbers are local: another phone may
// number the same survivors in a different order.
data class PhoneNames(
    private val survivors: Map<Int, Int> = emptyMap(),
    private val phones: Map<Int, Int> = emptyMap(),
    private val messages: Map<Long, Int> = emptyMap(),
    private val me: Set<Int> = emptySet(),
) {
    fun update(
        records: List<SosRecord>,
        otherPhones: Collection<Int>,
        myIds: Set<Int>,
    ): PhoneNames {
        val nextSurvivors = LinkedHashMap(survivors)
        records
            .filter { it.originId !in myIds }
            .sortedWith(compareBy({ it.firstHeardAt }, { it.originId }))
            .forEach { nextSurvivors.getOrPut(it.originId) { nextSurvivors.size + 1 } }
        val nextPhones = LinkedHashMap(phones)
        (records.map { it.carrierId } + otherPhones)
            .filter { it !in myIds && it !in nextSurvivors }
            .forEach { nextPhones.getOrPut(it) { nextPhones.size + 1 } }
        val nextMessages = messages + records.associate { it.messageId to it.originId }
        val next = PhoneNames(nextSurvivors, nextPhones, nextMessages, myIds)
        return if (next == this) this else next
    }

    fun survivor(originId: Int): String = when {
        originId in me -> "You"
        else -> survivors[originId]?.let { "Survivor $it" } ?: "Survivor"
    }

    // A phone that sent its own SOS keeps its survivor name everywhere, even as a carrier.
    fun phone(id: Int): String = when {
        id in me -> "This phone"
        id in survivors -> survivor(id)
        else -> phones[id]?.let { "Phone $it" } ?: "Another phone"
    }

    fun sos(messageId: Long): String = messages[messageId]?.let { origin ->
        if (origin in me) "Your SOS" else "${survivor(origin)}'s SOS"
    } ?: "An SOS"

    fun survivorNumber(originId: Int): Int? = survivors[originId]
}
