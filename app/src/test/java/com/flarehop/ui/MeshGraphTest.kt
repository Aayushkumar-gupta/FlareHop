package com.flarehop.ui

import com.flarehop.flare.GroupMember
import com.flarehop.flare.MySos
import com.flarehop.flare.Role
import com.flarehop.flare.SosRecord
import com.flarehop.flare.SosStatus
import com.flarehop.flare.SoundType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshGraphTest {
    private val me = 0x0001
    private val victim = 0x1A2B
    private val carrier = 0x3C4D
    private val rescuer = 0x5E6F
    private val now = 100_000L

    private fun sos(
        origin: Int = victim,
        carrierId: Int = origin,
        heardAt: Long = now,
        cancelled: Boolean = false,
        messageId: Long = 7L,
    ) = SosRecord(
        messageId = messageId,
        originId = origin,
        carrierId = carrierId,
        latitude = null,
        longitude = null,
        accuracyM = null,
        fixAgeMin = null,
        people = 0,
        injured = false,
        trapped = false,
        water = false,
        medical = false,
        fire = false,
        battery = 40,
        sound = SoundType.NONE,
        soundAgeMin = null,
        firstHeardAt = heardAt,
        lastHeardAt = heardAt,
        lastRssi = -61,
        acked = false,
        cancelled = cancelled,
    )

    private fun member(id: Int) = GroupMember(id, battery = 80, rssi = -55, now, carriedCount = 0)

    private fun build(
        role: Role = Role.SURVIVOR,
        mySos: MySos? = null,
        known: List<SosRecord> = emptyList(),
        carried: List<SosRecord> = emptyList(),
        group: List<GroupMember> = emptyList(),
        receiptsBy: Map<Long, Int> = emptyMap(),
    ) = MeshGraphBuilder.build(me, role, mySos, known, carried, group, receiptsBy, now)

    private fun MeshGraph.kindOf(id: Int) = others.single { it.phoneId == id }.kind

    private fun MeshGraph.edge(from: Int, to: Int, kind: EdgeKind) =
        edges.singleOrNull { it.from == from && it.to == to && it.kind == kind }

    @Test
    fun aPhoneInTheGroupIsHeardWithItsSignal() {
        val graph = build(group = listOf(member(carrier)))
        assertEquals(NodeKind.PHONE, graph.kindOf(carrier))
        assertEquals(-55, graph.edge(carrier, me, EdgeKind.HEARS)!!.rssi)
        assertEquals(NodeKind.ME, graph.me.kind)
    }

    @Test
    fun aVictimSendingItsOwnSosIsHeardDirectly() {
        val graph = build(known = listOf(sos()))
        assertEquals(NodeKind.VICTIM, graph.kindOf(victim))
        assertEquals(-61, graph.edge(victim, me, EdgeKind.HEARS)!!.rssi)
    }

    @Test
    fun aHandedOffSosShowsWhoCarriesItAndWhoIsHeard() {
        val graph = build(known = listOf(sos(carrierId = carrier)))
        assertEquals(NodeKind.VICTIM, graph.kindOf(victim))
        assertEquals(NodeKind.CARRIER, graph.kindOf(carrier))
        assertTrue(graph.edge(victim, carrier, EdgeKind.CARRIES) != null)
        assertTrue(graph.edge(carrier, me, EdgeKind.HEARS) != null)
        assertNull(graph.edge(victim, me, EdgeKind.HEARS))
        assertNull(graph.others.single { it.phoneId == victim }.rssi)
    }

    @Test
    fun aCarrierThatIsAlsoInTheGroupIsDrawnAsTheCarrier() {
        val graph = build(known = listOf(sos(carrierId = carrier)), group = listOf(member(carrier)))
        assertEquals(NodeKind.CARRIER, graph.kindOf(carrier))
        assertEquals(1, graph.edges.count { it.from == carrier && it.kind == EdgeKind.HEARS })
    }

    @Test
    fun aSilentOrCancelledSosIsNotDrawn() {
        val stale = sos(heardAt = now - MeshGraphBuilder.STALE_MS - 1)
        val cancelled = sos(origin = carrier, cancelled = true, messageId = 8L)
        val graph = build(known = listOf(stale, cancelled))
        assertTrue(graph.others.isEmpty())
        assertTrue(graph.edges.isEmpty())
    }

    @Test
    fun aRescuerAnswersEverySosItHears() {
        val graph = build(role = Role.RESCUER, known = listOf(sos()))
        assertEquals(NodeKind.RESCUER, graph.me.kind)
        assertTrue(graph.edge(me, victim, EdgeKind.RECEIPT) != null)
    }

    @Test
    fun anotherRescuersReceiptIsDrawnToTheVictim() {
        val graph = build(known = listOf(sos()), receiptsBy = mapOf(7L to rescuer))
        assertEquals(NodeKind.RESCUER, graph.kindOf(rescuer))
        assertTrue(graph.edge(rescuer, victim, EdgeKind.RECEIPT) != null)
    }

    @Test
    fun theReceiptForMyOwnSosPointsAtMe() {
        val mine = MySos(sos(origin = me, messageId = 9L), SosStatus.SENDING)
        val graph = build(mySos = mine, receiptsBy = mapOf(9L to rescuer))
        assertTrue(graph.edge(rescuer, me, EdgeKind.RECEIPT) != null)
    }

    @Test
    fun anSosICarryPointsAtMe() {
        val graph = build(carried = listOf(sos()))
        assertTrue(graph.edge(victim, me, EdgeKind.CARRIES) != null)
    }

    @Test
    fun myHandedOffSosPointsAtItsCarrier() {
        val mine =
            MySos(sos(origin = me, carrierId = carrier, messageId = 9L), SosStatus.HANDED_OFF)
        val graph = build(mySos = mine)
        assertEquals(NodeKind.CARRIER, graph.kindOf(carrier))
        assertTrue(graph.edge(me, carrier, EdgeKind.CARRIES) != null)
    }

    @Test
    fun phonesKeepTheirOrderWhateverTheSignalDoes() {
        val graph = build(group = listOf(member(rescuer), member(carrier), member(victim)))
        assertEquals(listOf(victim, carrier, rescuer), graph.others.map { it.phoneId })
    }

    @Test
    fun theFirstSlotIsStraightUpAndTheSlotsAreEven() {
        val slots = graphSlots(4)
        assertEquals(0f, slots[0].first, 0.001f)
        assertEquals(-1f, slots[0].second, 0.001f)
        assertEquals(1f, slots[1].first, 0.001f)
        assertEquals(0f, slots[2].first, 0.001f)
        assertEquals(1f, slots[2].second, 0.001f)
    }
}
