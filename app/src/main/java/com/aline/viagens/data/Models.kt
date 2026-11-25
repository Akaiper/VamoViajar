package com.aline.viagens.data

import com.google.firebase.firestore.DocumentSnapshot

data class Trip(
    val id: String,
    val name: String,
    val destination: String,
    val startDate: String, // formato yyyy-MM-dd
    val endDate: String,
    val ownerId: String,
    val members: List<String>,
    val memberNames: Map<String, String>,
    val inviteCode: String,
)

data class UserPrefs(
    val palette: String = "oceano",
    val mode: String = "sistema", // sistema | claro | escuro
)

sealed interface TripState {
    data object Loading : TripState
    data object Missing : TripState
    data class Found(val trip: Trip) : TripState
}

@Suppress("UNCHECKED_CAST")
fun DocumentSnapshot.toTrip(): Trip? {
    if (!exists()) return null
    return Trip(
        id = id,
        name = getString("name").orEmpty(),
        destination = getString("destination").orEmpty(),
        startDate = getString("startDate").orEmpty(),
        endDate = getString("endDate").orEmpty(),
        ownerId = getString("ownerId").orEmpty(),
        members = (get("members") as? List<String>).orEmpty(),
        memberNames = (get("memberNames") as? Map<String, String>).orEmpty(),
        inviteCode = getString("inviteCode").orEmpty(),
    )
}

/** Uma atividade do roteiro. day vazio = "ideia sem dia". */
data class Activity(
    val id: String = "",
    val title: String = "",
    val day: String = "",   // yyyy-MM-dd, ou vazio
    val time: String = "",  // HH:mm, ou vazio
    val place: String = "",
    val notes: String = "",
    val category: String = "passeio",
    val order: Double? = null, // preenchido só quando o dia foi reordenado na mão
    val createdByName: String = "",
)

fun DocumentSnapshot.toActivity(): Activity? {
    if (!exists()) return null
    return Activity(
        id = id,
        title = getString("title").orEmpty(),
        day = getString("day").orEmpty(),
        time = getString("time").orEmpty(),
        place = getString("place").orEmpty(),
        notes = getString("notes").orEmpty(),
        category = getString("category") ?: "passeio",
        order = getDouble("order"),
        createdByName = getString("createdByName").orEmpty(),
    )
}

/** Ordem de um dia: se alguém reordenou na mão, usa essa ordem; senão, pelo horário (sem horário vai pro fim). */
fun List<Activity>.sortedForDay(): List<Activity> {
    val byTime = compareBy<Activity>({ it.time.isBlank() }, { it.time }, { it.title.lowercase() })
    return if (any { it.order != null }) {
        sortedWith(compareBy<Activity> { it.order ?: Double.MAX_VALUE }.then(byTime))
    } else sortedWith(byTime)
}

/** Uma lista do checklist. personal = só quem criou vê. */
data class Checklist(
    val id: String,
    val name: String,
    val personal: Boolean,
    val ownerId: String,
    val createdAtMs: Long,
)

data class CheckItem(
    val id: String,
    val text: String,
    val done: Boolean,
    val doneByName: String,
    val assigneeId: String,
    val assigneeName: String,
    val createdAtMs: Long,
)

fun DocumentSnapshot.toChecklist(): Checklist? {
    if (!exists()) return null
    return Checklist(
        id = id,
        name = getString("name").orEmpty(),
        personal = getBoolean("personal") ?: false,
        ownerId = getString("ownerId").orEmpty(),
        createdAtMs = getLong("createdAtMs") ?: 0L,
    )
}

fun DocumentSnapshot.toCheckItem(): CheckItem? {
    if (!exists()) return null
    return CheckItem(
        id = id,
        text = getString("text").orEmpty(),
        done = getBoolean("done") ?: false,
        doneByName = getString("doneByName").orEmpty(),
        assigneeId = getString("assigneeId").orEmpty(),
        assigneeName = getString("assigneeName").orEmpty(),
        createdAtMs = getLong("createdAtMs") ?: 0L,
    )
}

/** Pendentes primeiro, na ordem em que foram adicionados; feitos no fim. */
fun List<CheckItem>.sortedForList(): List<CheckItem> =
    sortedWith(compareBy<CheckItem>({ it.done }, { it.createdAtMs }))

/** Modelos de lista com itens sugeridos. */
enum class ChecklistTemplate(val label: String, val items: List<String>) {
    VAZIA("Vazia", emptyList()),
    MALA(
        "Mala", listOf(
            "Roupas para os dias da viagem", "Roupa íntima e meias", "Pijama", "Calçado confortável",
            "Itens de higiene", "Remédios de uso pessoal", "Carregador do celular", "Carregador portátil",
            "Fones de ouvido", "Óculos de sol", "Protetor solar",
        )
    ),
    DOCUMENTOS(
        "Documentos", listOf(
            "RG ou CNH", "Passaporte (se for internacional)", "Cartões de crédito e débito",
            "Comprovantes das reservas", "Seguro viagem", "Carteira de vacinação (se exigida)", "Um pouco de dinheiro em espécie",
        )
    ),
    ANTES(
        "Antes de viajar", listOf(
            "Confirmar as reservas", "Fazer check-in online", "Avisar o banco sobre a viagem", "Baixar mapas offline",
            "Combinar quem cuida das plantas e dos pets", "Tirar o lixo", "Desligar aparelhos da tomada",
            "Combinar o transporte até o aeroporto ou rodoviária",
        )
    ),
    PRAIA(
        "Praia", listOf(
            "Roupa de banho", "Toalha de praia", "Chinelo", "Protetor solar", "Boné ou chapéu",
            "Saída de praia", "Bolsa à prova d'água para o celular",
        )
    ),
}
