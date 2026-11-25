package com.aline.viagens.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.aline.viagens.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.launch
import com.google.firebase.firestore.DocumentReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom

/** Erro com mensagem pronta para mostrar na tela. */
class UserMessageException(message: String) : Exception(message)

object Repo {
    private const val TAG = "VamoViajar"
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private lateinit var local: SharedPreferences
    /** Para tarefas que devem terminar mesmo se a pessoa sair da tela. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun init(context: Context) {
        local = context.getSharedPreferences("vamoviajar", Context.MODE_PRIVATE)
        auth.setLanguageCode("pt") // e-mails de verificação e de senha em português
    }

    // ---------- Login ----------

    val currentUser: FirebaseUser? get() = auth.currentUser

    fun authState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    fun displayName(user: FirebaseUser? = auth.currentUser): String =
        user?.displayName?.takeIf { it.isNotBlank() }
            ?: user?.email?.substringBefore("@")
            ?: "Viajante"

    /** Retorna false se a pessoa só fechou a janela de escolha de conta. */
    suspend fun signInWithGoogle(context: Context): Boolean {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(context.getString(R.string.default_web_client_id))
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = try {
            CredentialManager.create(context).getCredential(context, request)
        } catch (e: GetCredentialCancellationException) {
            return false
        } catch (e: NoCredentialException) {
            throw UserMessageException("Nenhuma conta Google encontrada neste celular.")
        }
        val credential = result.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) throw UserMessageException("Não foi possível entrar com o Google.")

        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        runAuth { auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await() }
        return true
    }

    suspend fun signInWithEmail(email: String, password: String) {
        runAuth { auth.signInWithEmailAndPassword(email.trim(), password).await() }
    }

    suspend fun signUpWithEmail(name: String, email: String, password: String) {
        runAuth {
            val user = auth.createUserWithEmailAndPassword(email.trim(), password).await().user
            user?.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build())?.await()
            user?.reload()?.await()
            runCatching { user?.sendEmailVerification()?.await() }
        }
    }

    suspend fun sendPasswordReset(email: String) {
        runAuth { auth.sendPasswordResetEmail(email.trim()).await() }
    }

    fun signOut() = auth.signOut()

    private suspend fun <T> runAuth(block: suspend () -> T): T = try {
        block()
    } catch (e: UserMessageException) {
        throw e
    } catch (e: FirebaseAuthUserCollisionException) {
        throw UserMessageException("Já existe uma conta com esse e-mail. Se você criou com o Google, toque em \"Entrar com Google\".")
    } catch (e: FirebaseAuthWeakPasswordException) {
        throw UserMessageException("Senha fraca: use pelo menos 6 caracteres.")
    } catch (e: FirebaseAuthInvalidUserException) {
        throw UserMessageException("Não encontramos uma conta com esse e-mail.")
    } catch (e: FirebaseAuthInvalidCredentialsException) {
        throw UserMessageException("E-mail ou senha incorretos.")
    } catch (e: FirebaseNetworkException) {
        throw UserMessageException("Sem conexão com a internet.")
    } catch (e: Exception) {
        Log.e(TAG, "Erro de login", e)
        throw UserMessageException("Algo deu errado. Tente de novo.")
    }

    // ---------- Preferências de cor ----------

    fun cachedPrefs(): UserPrefs = UserPrefs(
        palette = local.getString("palette", null) ?: "oceano",
        mode = local.getString("mode", null) ?: "sistema",
    )

    fun prefs(uid: String): Flow<UserPrefs> = callbackFlow {
        trySend(cachedPrefs())
        val reg = db.collection("users").document(uid).addSnapshotListener { snap, _ ->
            if (snap != null && snap.exists()) {
                val p = UserPrefs(
                    palette = snap.getString("palette") ?: "oceano",
                    mode = snap.getString("mode") ?: "sistema",
                )
                saveLocal(p)
                trySend(p)
            }
        }
        awaitClose { reg.remove() }
    }

    fun setPrefs(uid: String, prefs: UserPrefs) {
        saveLocal(prefs)
        db.collection("users").document(uid)
            .set(mapOf("palette" to prefs.palette, "mode" to prefs.mode), SetOptions.merge())
            .addOnFailureListener { Log.e(TAG, "Erro ao salvar cores", it) }
    }

    private fun saveLocal(p: UserPrefs) {
        local.edit().putString("palette", p.palette).putString("mode", p.mode).apply()
    }

    // ---------- Viagens ----------

    fun trips(uid: String): Flow<List<Trip>> = callbackFlow {
        val reg = db.collection("trips").whereArrayContains("members", uid)
            .addSnapshotListener { snap, err ->
                if (err != null) Log.e(TAG, "Erro ao ler viagens", err)
                if (snap != null) trySend(snap.documents.mapNotNull { it.toTrip() })
            }
        awaitClose { reg.remove() }
    }

    fun trip(id: String): Flow<TripState> = callbackFlow {
        trySend(TripState.Loading)
        val reg = db.collection("trips").document(id).addSnapshotListener { snap, err ->
            when {
                err != null -> trySend(TripState.Missing)
                snap != null -> trySend(snap.toTrip()?.let { TripState.Found(it) } ?: TripState.Missing)
            }
        }
        awaitClose { reg.remove() }
    }

    /** Cria a viagem e o código de convite. Funciona offline: sincroniza quando a internet voltar. */
    fun createTrip(name: String, destination: String, start: String, end: String): String {
        val user = auth.currentUser ?: throw UserMessageException("Faça login de novo.")
        val ref = db.collection("trips").document()
        val code = newInviteCode()
        db.batch()
            .set(
                ref, mapOf(
                    "name" to name.trim(),
                    "destination" to destination.trim(),
                    "startDate" to start,
                    "endDate" to end,
                    "ownerId" to user.uid,
                    "members" to listOf(user.uid),
                    "memberNames" to mapOf(user.uid to displayName(user)),
                    "inviteCode" to code,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            )
            .set(db.collection("invites").document(code), mapOf("tripId" to ref.id, "createdBy" to user.uid))
            .commit()
            .addOnFailureListener { Log.e(TAG, "Erro ao criar viagem", it) }
        return ref.id
    }

    fun updateTrip(id: String, name: String, destination: String, start: String, end: String) {
        db.collection("trips").document(id)
            .update(
                mapOf(
                    "name" to name.trim(),
                    "destination" to destination.trim(),
                    "startDate" to start,
                    "endDate" to end,
                )
            )
            .addOnFailureListener { Log.e(TAG, "Erro ao editar viagem", it) }
    }

    /** Apaga a viagem, o convite, o roteiro e os checklists que a pessoa consegue ver. */
    fun deleteTrip(trip: Trip) {
        val uid = auth.currentUser?.uid ?: return
        val tripRef = db.collection("trips").document(trip.id)
        appScope.launch {
            try {
                val refs = mutableListOf<DocumentReference>()
                refs += tripRef.collection("activities").get().await().documents.map { it.reference }
                val lists = tripRef.collection("checklists").whereEqualTo("personal", false).get().await().documents +
                    tripRef.collection("checklists").whereEqualTo("ownerId", uid).get().await().documents
                lists.distinctBy { it.id }.forEach { list ->
                    refs += list.reference.collection("items").get().await().documents.map { it.reference }
                    refs += list.reference
                }
                // Lotes de até 400 para ficar abaixo do limite do Firestore
                refs.chunked(400).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { batch.delete(it) }
                    batch.commit().await()
                }
                val last = db.batch()
                if (trip.inviteCode.isNotBlank()) last.delete(db.collection("invites").document(trip.inviteCode))
                last.delete(tripRef)
                last.commit().await()
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao apagar viagem", e)
            }
        }
    }

    /** Entra numa viagem pelo código. Retorna o id da viagem. */
    suspend fun joinTrip(rawCode: String): String {
        val user = auth.currentUser ?: throw UserMessageException("Faça login de novo.")
        val code = rawCode.trim().uppercase()
        if (code.length != 6) throw UserMessageException("O código tem 6 letras/números.")
        try {
            val invite = db.collection("invites").document(code).get().await()
            val tripId = invite.getString("tripId")
                ?: throw UserMessageException("Código não encontrado. Confere se digitou certinho.")
            db.collection("trips").document(tripId).update(
                mapOf(
                    "members" to FieldValue.arrayUnion(user.uid),
                    "memberNames.${user.uid}" to displayName(user),
                )
            ).await()
            return tripId
        } catch (e: UserMessageException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            Log.e(TAG, "Erro ao entrar na viagem", e)
            throw when (e.code) {
                FirebaseFirestoreException.Code.UNAVAILABLE -> UserMessageException("Sem conexão. Para entrar numa viagem você precisa de internet.")
                FirebaseFirestoreException.Code.NOT_FOUND -> UserMessageException("Essa viagem não existe mais.")
                else -> UserMessageException("Não foi possível entrar na viagem.")
            }
        }
    }

    // ---------- Roteiro ----------

    private fun activitiesRef(tripId: String) =
        db.collection("trips").document(tripId).collection("activities")

    fun activities(tripId: String): Flow<List<Activity>> = callbackFlow {
        val reg = activitiesRef(tripId).addSnapshotListener { snap, err ->
            if (err != null) Log.e(TAG, "Erro ao ler roteiro", err)
            if (snap != null) trySend(snap.documents.mapNotNull { it.toActivity() })
        }
        awaitClose { reg.remove() }
    }

    fun activity(tripId: String, id: String): Flow<Activity?> = callbackFlow {
        val reg = activitiesRef(tripId).document(id).addSnapshotListener { snap, _ ->
            trySend(snap?.toActivity())
        }
        awaitClose { reg.remove() }
    }

    /** Cria (id vazio) ou atualiza uma atividade. Funciona offline. */
    fun saveActivity(tripId: String, a: Activity, movedDay: Boolean) {
        val data = mutableMapOf<String, Any?>(
            "title" to a.title.trim(),
            "day" to a.day,
            "time" to a.time,
            "place" to a.place.trim(),
            "notes" to a.notes.trim(),
            "category" to a.category,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (a.id.isBlank()) {
            data["createdByName"] = displayName()
            activitiesRef(tripId).add(data).addOnFailureListener { Log.e(TAG, "Erro ao criar atividade", it) }
        } else {
            // Mudou de dia: sai da ordem manual do dia antigo e entra no fim do novo
            if (movedDay) data["order"] = FieldValue.delete()
            activitiesRef(tripId).document(a.id).update(data)
                .addOnFailureListener { Log.e(TAG, "Erro ao salvar atividade", it) }
        }
    }

    fun deleteActivity(tripId: String, id: String) {
        activitiesRef(tripId).document(id).delete()
            .addOnFailureListener { Log.e(TAG, "Erro ao apagar atividade", it) }
    }

    /** Salva a ordem manual de um dia (lista de ids na ordem desejada). */
    fun setDayOrder(tripId: String, orderedIds: List<String>) {
        val batch = db.batch()
        orderedIds.forEachIndexed { i, id ->
            batch.update(activitiesRef(tripId).document(id), "order", i.toDouble())
        }
        batch.commit().addOnFailureListener { Log.e(TAG, "Erro ao reordenar", it) }
    }

    /** Volta o dia para a ordem automática por horário. */
    fun clearDayOrder(tripId: String, ids: List<String>) {
        val batch = db.batch()
        ids.forEach { batch.update(activitiesRef(tripId).document(it), "order", FieldValue.delete()) }
        batch.commit().addOnFailureListener { Log.e(TAG, "Erro ao limpar ordem", it) }
    }

    // ---------- Checklist ----------

    private fun listsRef(tripId: String) =
        db.collection("trips").document(tripId).collection("checklists")

    private fun itemsRef(tripId: String, listId: String) =
        listsRef(tripId).document(listId).collection("items")

    /** Listas do grupo + listas pessoais de quem está usando. */
    fun checklists(tripId: String): Flow<List<Checklist>> = callbackFlow {
        val uid = auth.currentUser?.uid.orEmpty()
        var shared: List<Checklist>? = null
        var mine: List<Checklist>? = null
        fun emit() {
            val s = shared ?: return
            val m = mine ?: return
            trySend((s + m).distinctBy { it.id }.sortedBy { it.createdAtMs })
        }
        val r1 = listsRef(tripId).whereEqualTo("personal", false).addSnapshotListener { snap, err ->
            if (err != null) Log.e(TAG, "Erro ao ler listas", err)
            if (snap != null) { shared = snap.documents.mapNotNull { it.toChecklist() }; emit() }
        }
        val r2 = listsRef(tripId).whereEqualTo("ownerId", uid).addSnapshotListener { snap, err ->
            if (err != null) Log.e(TAG, "Erro ao ler listas pessoais", err)
            if (snap != null) { mine = snap.documents.mapNotNull { it.toChecklist() }; emit() }
        }
        awaitClose { r1.remove(); r2.remove() }
    }

    fun checklist(tripId: String, listId: String): Flow<Checklist?> = callbackFlow {
        val reg = listsRef(tripId).document(listId).addSnapshotListener { snap, _ -> trySend(snap?.toChecklist()) }
        awaitClose { reg.remove() }
    }

    fun items(tripId: String, listId: String): Flow<List<CheckItem>> = callbackFlow {
        val reg = itemsRef(tripId, listId).addSnapshotListener { snap, err ->
            if (err != null) Log.e(TAG, "Erro ao ler itens", err)
            if (snap != null) trySend(snap.documents.mapNotNull { it.toCheckItem() })
        }
        awaitClose { reg.remove() }
    }

    /** Cria a lista já com os itens do modelo. Retorna o id. */
    fun createChecklist(tripId: String, name: String, personal: Boolean, templateItems: List<String>): String {
        val uid = auth.currentUser?.uid ?: throw UserMessageException("Faça login de novo.")
        val ref = listsRef(tripId).document()
        val now = System.currentTimeMillis()
        val batch = db.batch()
        batch.set(
            ref, mapOf(
                "name" to name.trim(),
                "personal" to personal,
                "ownerId" to if (personal) uid else "",
                "createdAtMs" to now,
            )
        )
        templateItems.forEachIndexed { i, text ->
            batch.set(ref.collection("items").document(), newItemData(text, now + i))
        }
        batch.commit().addOnFailureListener { Log.e(TAG, "Erro ao criar lista", it) }
        return ref.id
    }

    fun renameChecklist(tripId: String, listId: String, name: String) {
        listsRef(tripId).document(listId).update("name", name.trim())
            .addOnFailureListener { Log.e(TAG, "Erro ao renomear lista", it) }
    }

    fun deleteChecklist(tripId: String, listId: String) {
        val listRef = listsRef(tripId).document(listId)
        appScope.launch {
            try {
                val items = listRef.collection("items").get().await().documents.map { it.reference }
                (items + listRef).chunked(400).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { batch.delete(it) }
                    batch.commit().await()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao apagar lista", e)
            }
        }
    }

    private fun newItemData(text: String, createdAtMs: Long) = mapOf(
        "text" to text.trim(),
        "done" to false,
        "doneByName" to "",
        "assigneeId" to "",
        "assigneeName" to "",
        "createdAtMs" to createdAtMs,
    )

    fun addItem(tripId: String, listId: String, text: String) {
        itemsRef(tripId, listId).add(newItemData(text, System.currentTimeMillis()))
            .addOnFailureListener { Log.e(TAG, "Erro ao adicionar item", it) }
    }

    fun setItemDone(tripId: String, listId: String, itemId: String, done: Boolean) {
        itemsRef(tripId, listId).document(itemId)
            .update(mapOf("done" to done, "doneByName" to if (done) displayName() else ""))
            .addOnFailureListener { Log.e(TAG, "Erro ao marcar item", it) }
    }

    fun updateItem(tripId: String, listId: String, itemId: String, text: String, assigneeId: String, assigneeName: String) {
        itemsRef(tripId, listId).document(itemId)
            .update(mapOf("text" to text.trim(), "assigneeId" to assigneeId, "assigneeName" to assigneeName))
            .addOnFailureListener { Log.e(TAG, "Erro ao editar item", it) }
    }

    fun deleteItem(tripId: String, listId: String, itemId: String) {
        itemsRef(tripId, listId).document(itemId).delete()
            .addOnFailureListener { Log.e(TAG, "Erro ao apagar item", it) }
    }

    fun uncheckAll(tripId: String, listId: String, itemIds: List<String>) {
        itemIds.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.update(itemsRef(tripId, listId).document(it), mapOf("done" to false, "doneByName" to "")) }
            batch.commit().addOnFailureListener { Log.e(TAG, "Erro ao desmarcar", it) }
        }
    }

    private fun newInviteCode(): String {
        // Sem letras que confundem (0/O, 1/I/L)
        val alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        val rnd = SecureRandom()
        return (1..6).map { alphabet[rnd.nextInt(alphabet.length)] }.joinToString("")
    }
}
