package com.difusion.app.service

import android.content.Context
import com.difusion.app.data.AppDatabase
import com.difusion.app.storage.WaPrefs
import org.json.JSONArray
import org.json.JSONObject

/**
 * Flujos conversacionales del bot: un mapa de nodos conectados que se ejecutan
 * según lo que responde el cliente. Se configuran en la app como un "mapa
 * conceptual" y el runner los recorre en tiempo real.
 *
 * Nodos:
 *  - "message": envía un mensaje y espera la respuesta del cliente. Sus
 *    `branches` (palabra clave -> nodo destino) deciden hacia dónde continuar;
 *    `nextId` es el destino por defecto si ninguna rama coincide.
 *  - "action": ejecuta una acción (cambiar seguimiento/estado/medio, etiquetar,
 *    programar) y continúa por `nextId`.
 *  - "end": termina el flujo y olvida el estado del cliente.
 */
object BotFlowStore {

    data class Branch(val keyword: String, val targetId: String)

    data class Node(
        val id: String,
        val kind: String,          // message | action | end
        val text: String = "",     // message: texto a enviar · action: valor
        val action: String = "",   // action: gestion|estado|medio|tag|schedule
        val minutes: Int = 0,      // action schedule: en cuántos minutos
        val nextId: String = "",   // message: destino por defecto · action: siguiente
        val branches: List<Branch> = emptyList()
    )

    data class Flow(
        val id: String,
        val name: String,
        val trigger: String = "",   // palabra clave que inicia el flujo (opcional)
        val enabled: Boolean = true,
        val startId: String = "",
        val nodes: List<Node> = emptyList()
    )

    fun newId(): String = java.util.UUID.randomUUID().toString().substring(0, 8)

    val ACTION_LABELS = mapOf(
        "gestion" to "Cambiar seguimiento",
        "estado" to "Cambiar status",
        "medio" to "Cambiar medio",
        "tag" to "Agregar etiqueta",
        "schedule" to "Programar mensaje"
    )

    // ---------- JSON ----------

    private fun branchToJson(b: Branch) = JSONObject().put("keyword", b.keyword).put("targetId", b.targetId)

    private fun nodeToJson(n: Node): JSONObject = JSONObject().apply {
        put("id", n.id)
        put("kind", n.kind)
        put("text", n.text)
        put("action", n.action)
        put("minutes", n.minutes)
        put("nextId", n.nextId)
        val arr = JSONArray()
        n.branches.forEach { arr.put(branchToJson(it)) }
        put("branches", arr)
    }

    private fun flowToJson(f: Flow): JSONObject = JSONObject().apply {
        put("id", f.id)
        put("name", f.name)
        put("trigger", f.trigger)
        put("enabled", f.enabled)
        put("startId", f.startId)
        val arr = JSONArray()
        f.nodes.forEach { arr.put(nodeToJson(it)) }
        put("nodes", arr)
    }

    fun flowsToJson(flows: List<Flow>): String {
        val arr = JSONArray()
        flows.forEach { arr.put(flowToJson(it)) }
        return arr.toString()
    }

    private fun parseBranch(o: JSONObject) = Branch(o.optString("keyword", ""), o.optString("targetId", ""))

    private fun parseNode(o: JSONObject): Node {
        val arr = o.optJSONArray("branches")
        val branches = mutableListOf<Branch>()
        if (arr != null) for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { branches.add(parseBranch(it)) }
        }
        return Node(
            id = o.optString("id", ""),
            kind = o.optString("kind", "message"),
            text = o.optString("text", ""),
            action = o.optString("action", ""),
            minutes = o.optInt("minutes", 0),
            nextId = o.optString("nextId", ""),
            branches = branches
        )
    }

    fun parseFlows(json: String): List<Flow> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val nodesArr = o.optJSONArray("nodes")
            val nodes = mutableListOf<Node>()
            if (nodesArr != null) for (j in 0 until nodesArr.length()) {
                nodesArr.optJSONObject(j)?.let { nodes.add(parseNode(it)) }
            }
            Flow(
                id = o.optString("id", ""),
                name = o.optString("name", ""),
                trigger = o.optString("trigger", ""),
                enabled = o.optBoolean("enabled", true),
                startId = o.optString("startId", ""),
                nodes = nodes
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun saveFlows(context: Context, flows: List<Flow>) =
        WaPrefs.setFlowsJson(context, flowsToJson(flows))

    fun loadFlows(context: Context): List<Flow> = parseFlows(WaPrefs.getFlowsJson(context))

    // ---------- Estado por cliente (qué nodo está esperando) ----------

    private fun phoneKey(phone: String): String =
        phone.filter { it.isDigit() }.takeLast(10)

    fun getState(context: Context, phone: String): Pair<String, String>? {
        val json = WaPrefs.getFlowStateJson(context)
        val key = phoneKey(phone)
        if (key.isBlank()) return null
        return try {
            val root = JSONObject(json)
            val o = root.optJSONObject(key) ?: return null
            val flowId = o.optString("flowId", "")
            val nodeId = o.optString("nodeId", "")
            if (flowId.isBlank() || nodeId.isBlank()) null else flowId to nodeId
        } catch (_: Exception) { null }
    }

    fun setState(context: Context, phone: String, flowId: String, nodeId: String) {
        val key = phoneKey(phone)
        if (key.isBlank()) return
        val root = try { JSONObject(WaPrefs.getFlowStateJson(context)) } catch (_: Exception) { JSONObject() }
        root.put(key, JSONObject().put("flowId", flowId).put("nodeId", nodeId))
        WaPrefs.setFlowStateJson(context, root.toString())
    }

    fun clearState(context: Context, phone: String) {
        val key = phoneKey(phone)
        val root = try { JSONObject(WaPrefs.getFlowStateJson(context)) } catch (_: Exception) { JSONObject() }
        root.remove(key)
        WaPrefs.setFlowStateJson(context, root.toString())
    }

    fun nodeById(flow: Flow, id: String): Node? = flow.nodes.firstOrNull { it.id == id }

    fun defaultStart(flow: Flow): String =
        flow.startId.ifBlank { flow.nodes.firstOrNull { it.kind != "end" }?.id ?: "" }
}

/**
 * Ejecuta un flujo para el mensaje entrante de un cliente. Devuelve true si
 * el flujo "atendió" el mensaje (inició o continuó una conversación).
 */
object BotFlowRunner {

    suspend fun handle(context: Context, jid: String, text: String): Boolean {
        val phone = jid.substringBefore("@")
        val flows = BotFlowStore.loadFlows(context).filter { it.enabled }
        if (flows.isEmpty()) return false

        val state = BotFlowStore.getState(context, phone)
        var flow: BotFlowStore.Flow? = null
        var curId: String? = null

        if (state != null) {
            flow = flows.firstOrNull { it.id == state.first }
            val node = flow?.let { BotFlowStore.nodeById(it, state.second) }
            if (flow == null || node == null) {
                BotFlowStore.clearState(context, phone)
                return false
            }
            // Estamos respondiendo al nodo actual (un "message"): evaluar ramas.
            curId = if (node.kind == "message") {
                node.branches.firstOrNull { text.contains(it.keyword, ignoreCase = true) }?.targetId
                    ?: node.nextId
            } else {
                node.nextId
            }
        } else {
            flow = flows.firstOrNull {
                it.trigger.isNotBlank() && text.contains(it.trigger, ignoreCase = true)
            } ?: return false
            curId = BotFlowStore.defaultStart(flow)
        }

        if (flow == null) return false

        var guard = 0
        while (curId != null && curId.isNotBlank() && guard++ < 40) {
            val node = BotFlowStore.nodeById(flow, curId) ?: break
            when (node.kind) {
                "message" -> {
                    WhatsAppBridge.send(context, jid, node.text.replace("{nombre}", phone))
                    BotFlowStore.setState(context, phone, flow.id, node.id)
                    return true
                }
                "action" -> {
                    runAction(context, phone, node)
                    curId = node.nextId
                }
                "end" -> {
                    BotFlowStore.clearState(context, phone)
                    return true
                }
                else -> curId = node.nextId
            }
        }
        BotFlowStore.clearState(context, phone)
        return true
    }

    private suspend fun runAction(context: Context, phone: String, node: BotFlowStore.Node) {
        val value = node.text
        val db = AppDatabase.getInstance(context)
        val contact = db.contactDao().getAllOnce().firstOrNull {
            it.phone.filter { c -> c.isDigit() }.takeLast(10) == phone.filter { c -> c.isDigit() }.takeLast(10)
        }
        when (node.action) {
            "gestion" -> if (contact != null && value.isNotBlank())
                db.contactDao().update(contact.copy(gestion = value.trim()))
            "estado" -> if (contact != null && value.isNotBlank())
                db.contactDao().update(contact.copy(estado = value.trim()))
            "medio" -> if (contact != null && value.isNotBlank())
                db.contactDao().update(contact.copy(medio = value.trim()))
            "tag" -> if (contact != null && value.isNotBlank()) {
                val has = contact.tags.split(",").any { it.trim().equals(value.trim(), true) }
                if (!has) {
                    val tags = listOf(contact.tags, value.trim()).filter { it.isNotBlank() }.joinToString(",")
                    db.contactDao().update(contact.copy(tags = tags))
                }
            }
            "schedule" -> if (value.isNotBlank() && node.minutes > 0) {
                val at = System.currentTimeMillis() + node.minutes * 60_000L
                Scheduler.schedule(
                    context,
                    phones = listOf(phone),
                    emails = emptyList(),
                    message = value.replace("{nombre}", phone),
                    subject = "",
                    channel = 2,
                    atMillis = at
                )
            }
        }
    }
}
