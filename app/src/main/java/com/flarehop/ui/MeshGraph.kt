package com.flarehop.ui

import com.flarehop.flare.GroupMember
import com.flarehop.flare.MySos
import com.flarehop.flare.Role
import com.flarehop.flare.SosRecord
import com.flarehop.flare.SosStatus

// The order is the priority when one phone plays two parts: a carrier that is also in the group
// is drawn as a carrier.
enum class NodeKind { ME, RESCUER, VICTIM, CARRIER, PHONE }

// Phones never connect; every arrow is something this phone learned from a packet.
// HEARS: this phone hears the sender's packets. CARRIES: an SOS passed from its victim to the
// phone broadcasting it now. RECEIPT: a rescuer answered that victim's SOS.
enum class EdgeKind { HEARS, CARRIES, RECEIPT }

data class GraphNode(
    val phoneId: Int,
    val kind: NodeKind,
    val battery: Int? = null,
    val rssi: Int? = null,
)

data class GraphEdge(val from: Int, val to: Int, val kind: EdgeKind, val rssi: Int? = null)

data class MeshGraph(val me: GraphNode, val others: List<GraphNode>, val edges: List<GraphEdge>)

// Kept free of Android so it can be checked without a phone.
object MeshGraphBuilder {
    // An SOS not heard for this long is no longer drawn: it is out of range or its phone is off.
    const val STALE_MS = 60_000L

    fun build(
        myId: Int,
        role: Role,
        mySos: MySos?,
        known: List<SosRecord>,
        carried: List<SosRecord>,
        group: List<GroupMember>,
        receiptsBy: Map<Long, Int>,
        now: Long,
    ): MeshGraph {
        val nodes = LinkedHashMap<Int, GraphNode>()
        val edges = LinkedHashMap<Triple<Int, Int, EdgeKind>, GraphEdge>()
        fun node(phoneId: Int, kind: NodeKind, battery: Int? = null, rssi: Int? = null) {
            val was = nodes[phoneId]
            val pick = if (was == null || kind.ordinal < was.kind.ordinal) kind else was.kind
            nodes[phoneId] = GraphNode(
                phoneId = phoneId,
                kind = pick,
                battery = battery ?: was?.battery,
                rssi = rssi ?: was?.rssi,
            )
        }
        fun edge(from: Int, to: Int, kind: EdgeKind, rssi: Int? = null) {
            if (from == to) return
            edges.putIfAbsent(Triple(from, to, kind), GraphEdge(from, to, kind, rssi))
        }

        node(myId, if (role == Role.RESCUER) NodeKind.RESCUER else NodeKind.ME)

        group.forEach { member ->
            node(member.phoneId, NodeKind.PHONE, member.battery, member.rssi)
            edge(member.phoneId, myId, EdgeKind.HEARS, member.rssi)
        }

        known
            .filter { !it.cancelled && it.originId != myId && now - it.lastHeardAt <= STALE_MS }
            .forEach { record ->
                val victim = record.originId
                val carrier = record.carrierId
                if (carrier == victim) {
                    node(victim, NodeKind.VICTIM, record.battery, record.lastRssi)
                } else {
                    node(victim, NodeKind.VICTIM)
                    node(carrier, NodeKind.CARRIER, record.battery, record.lastRssi)
                    edge(victim, carrier, EdgeKind.CARRIES)
                }
                edge(carrier, myId, EdgeKind.HEARS, record.lastRssi)
                // A rescuer answers every SOS it hears, so this phone's own receipts need no event.
                val rescuer = if (role == Role.RESCUER) myId else receiptsBy[record.messageId]
                if (rescuer != null) {
                    node(rescuer, NodeKind.RESCUER)
                    edge(rescuer, victim, EdgeKind.RECEIPT)
                }
            }

        carried.forEach { record ->
            node(record.originId, NodeKind.VICTIM)
            edge(record.originId, myId, EdgeKind.CARRIES)
        }

        mySos?.let { mine ->
            if (mine.status == SosStatus.HANDED_OFF) {
                node(mine.record.carrierId, NodeKind.CARRIER)
                edge(myId, mine.record.carrierId, EdgeKind.CARRIES)
            }
            receiptsBy[mine.record.messageId]?.let { rescuer ->
                node(rescuer, NodeKind.RESCUER)
                edge(rescuer, myId, EdgeKind.RECEIPT)
            }
        }

        val me = nodes.getValue(myId)
        return MeshGraph(
            me = me,
            others = nodes.values.filter { it.phoneId != myId }.sortedBy { it.phoneId },
            edges = edges.values.toList(),
        )
    }
}
