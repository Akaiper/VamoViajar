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
    val car: CarInfo = CarInfo(),
)

/** Dados do carro da viagem, para estimar distância e combustível. Tudo opcional. */
data class CarInfo(
    val destLat: Double? = null, val destLng: Double? = null,     // ponto exato do destino
    val startPlace: String = "",                                   // ponto de partida (ex.: casa)
    val startLat: Double? = null, val startLng: Double? = null,
    val kmPerLiter: Double? = null,
    val fuelPrice: Double? = null,                                 // R$ por litro
    val roundTrip: Boolean = true,
) {
    val hasFuel get() = (kmPerLiter ?: 0.0) > 0 && (fuelPrice ?: 0.0) > 0
    val hasLeg get() = destLat != null && destLng != null && startLat != null && startLng != null

    fun toMap(): Map<String, Any?> = mapOf(
        "destLat" to destLat, "destLng" to destLng,
        "startPlace" to startPlace.trim(), "startLat" to startLat, "startLng" to startLng,
        "kmPerLiter" to kmPerLiter, "fuelPrice" to fuelPrice, "roundTrip" to roundTrip,
    )

    companion object {
        fun from(doc: DocumentSnapshot) = CarInfo(
            destLat = doc.getDouble("destLat"), destLng = doc.getDouble("destLng"),
            startPlace = doc.getString("startPlace").orEmpty(),
            startLat = doc.getDouble("startLat"), startLng = doc.getDouble("startLng"),
            kmPerLiter = doc.getDouble("kmPerLiter"), fuelPrice = doc.getDouble("fuelPrice"),
            roundTrip = doc.getBoolean("roundTrip") ?: true,
        )
    }
}

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
        car = CarInfo.from(this),
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
    val bookingIds: List<String> = emptyList(), // reservas ligadas a esta atividade
    val placeLat: Double? = null, // preenchidos quando o local foi escolhido nas sugestões
    val placeLng: Double? = null,
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
        bookingIds = (get("bookingIds") as? List<*>)?.filterIsInstance<String>().orEmpty(),
        placeLat = getDouble("placeLat"),
        placeLng = getDouble("placeLng"),
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

/** Sempre na ordem em que foram adicionados: marcar um item não muda a posição dele. */
fun List<CheckItem>.sortedForList(): List<CheckItem> =
    sortedWith(compareBy<CheckItem>({ it.createdAtMs }, { it.id }))

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

// ---------- Reservas ----------

enum class BookingType(val id: String, val label: String) {
    PASSAGEM("passagem", "Passagem"),
    HOSPEDAGEM("hospedagem", "Hospedagem"),
    TRANSPORTE("transporte", "Transporte"),
    INGRESSO("ingresso", "Ingresso ou passeio");

    companion object {
        fun from(id: String) = entries.firstOrNull { it.id == id } ?: INGRESSO
    }
}

enum class TravelMode(val id: String, val label: String) {
    AVIAO("aviao", "Avião"),
    ONIBUS("onibus", "Ônibus"),
    TREM("trem", "Trem"),
    BARCO("barco", "Barco");

    companion object {
        fun from(id: String) = entries.firstOrNull { it.id == id } ?: AVIAO
    }
}

data class Booking(
    val id: String = "",
    val type: String = "passagem",
    val mode: String = "aviao",      // só passagem
    val title: String = "",          // companhia, hotel, locadora, nome do passeio
    val number: String = "",         // número do voo/linha
    val from: String = "",           // origem / local de retirada
    val to: String = "",             // destino / local de devolução
    val address: String = "",        // hospedagem e ingresso
    val startDate: String = "", val startTime: String = "",
    val endDate: String = "", val endTime: String = "",
    val code: String = "",
    val link: String = "",
    val price: Double? = null,
    val paidById: String = "", val paidByName: String = "",
    val notes: String = "",
    val createdByName: String = "",
    val split: Split = Split(), // como o valor é dividido nos gastos
    // Coordenadas exatas, quando o local foi escolhido nas sugestões
    val addressLat: Double? = null, val addressLng: Double? = null,
    val fromLat: Double? = null, val fromLng: Double? = null,
    val toLat: Double? = null, val toLng: Double? = null,
) {
    val bookingType get() = BookingType.from(type)

    // Locais para mostrar: link do Maps aparece como "Local no Google Maps", não como o endereço cru
    private val fromShown get() = MapLinks.display(from)
    private val toShown get() = MapLinks.display(to)

    /** Uma linha curta que descreve a reserva. */
    fun summary(): String = when (bookingType) {
        BookingType.PASSAGEM -> listOf(
            TravelMode.from(mode).label,
            number,
            listOf(fromShown, toShown).filter { it.isNotBlank() }.joinToString(" → "),
        ).filter { it.isNotBlank() }.joinToString(" · ")
        BookingType.HOSPEDAGEM, BookingType.INGRESSO -> MapLinks.display(address)
        BookingType.TRANSPORTE -> listOf(fromShown, toShown).filter { it.isNotBlank() }.distinct().joinToString(" → ")
    }

    /** Os momentos desta reserva que aparecem no roteiro. */
    fun events(): List<BookingEvent> {
        val name = title.ifBlank { bookingType.label }
        val list = mutableListOf<BookingEvent>()
        fun add(day: String, time: String, label: String) {
            if (day.isNotBlank()) list += BookingEvent(this, day, time, label)
        }
        when (bookingType) {
            BookingType.PASSAGEM -> {
                val route = listOf(fromShown, toShown).filter { it.isNotBlank() }.joinToString(" → ")
                val base = listOf(name, number).filter { it.isNotBlank() }.joinToString(" ")
                add(startDate, startTime, "Saída: $base" + if (route.isNotBlank()) " ($route)" else "")
                if (endDate.isNotBlank() && (endDate != startDate || endTime.isNotBlank())) {
                    add(endDate, endTime, "Chegada: $base" + if (to.isNotBlank()) " em $toShown" else "")
                }
            }
            BookingType.HOSPEDAGEM -> {
                add(startDate, startTime, "Check-in: $name")
                add(endDate, endTime, "Check-out: $name")
            }
            BookingType.TRANSPORTE -> {
                add(startDate, startTime, "Retirada: $name" + if (from.isNotBlank()) " em $fromShown" else "")
                add(endDate, endTime, "Devolução: $name" + if (to.isNotBlank()) " em $toShown" else "")
            }
            BookingType.INGRESSO -> add(startDate, startTime, name)
        }
        return list
    }
}

data class BookingEvent(val booking: Booking, val day: String, val time: String, val label: String)

fun DocumentSnapshot.toBooking(): Booking? {
    if (!exists()) return null
    return Booking(
        id = id,
        type = getString("type") ?: "passagem",
        mode = getString("mode") ?: "aviao",
        title = getString("title").orEmpty(),
        number = getString("number").orEmpty(),
        from = getString("from").orEmpty(),
        to = getString("to").orEmpty(),
        address = getString("address").orEmpty(),
        startDate = getString("startDate").orEmpty(),
        startTime = getString("startTime").orEmpty(),
        endDate = getString("endDate").orEmpty(),
        endTime = getString("endTime").orEmpty(),
        code = getString("code").orEmpty(),
        link = getString("link").orEmpty(),
        price = getDouble("price"),
        paidById = getString("paidById").orEmpty(),
        paidByName = getString("paidByName").orEmpty(),
        notes = getString("notes").orEmpty(),
        createdByName = getString("createdByName").orEmpty(),
        split = Split.from(this),
        addressLat = getDouble("addressLat"), addressLng = getDouble("addressLng"),
        fromLat = getDouble("fromLat"), fromLng = getDouble("fromLng"),
        toLat = getDouble("toLat"), toLng = getDouble("toLng"),
    )
}

object Money {
    private val brl = java.text.NumberFormat.getCurrencyInstance(java.util.Locale("pt", "BR"))
    fun format(v: Double): String = brl.format(v)

    /** Aceita "1.234,56", "1234,56" ou "1234.56". */
    fun parse(text: String): Double? {
        val t = text.trim().replace("R$", "").replace(" ", "")
        if (t.isBlank()) return null
        val normalized = if (t.contains(",")) t.replace(".", "").replace(",", ".") else t
        return normalized.toDoubleOrNull()
    }

    /** Valor para mostrar num campo de texto (sem R$). */
    fun toField(v: Double?): String =
        if (v == null) "" else String.format(java.util.Locale("pt", "BR"), "%.2f", v)
}
