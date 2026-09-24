package com.pointmaker.app.ui

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pointmaker.app.domain.Announcement
import com.pointmaker.app.domain.MatchRules
import com.pointmaker.app.domain.MatchState
import com.pointmaker.app.domain.ScoreFormatter
import com.pointmaker.app.domain.Side
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Application.matchDataStore: DataStore<Preferences> by preferencesDataStore(name = "match_state")

class MatchViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore: DataStore<Preferences> = application.matchDataStore

    private val restored: MatchState = runCatching {
        android.os.StrictMode.allowThreadDiskReads()
        runBlocking(Dispatchers.IO) { readSnapshot() }
    }.getOrElse { MatchState() }

    private val _state = MutableStateFlow(restored)
    val state: StateFlow<MatchState> = _state.asStateFlow()

    /**
     * Aliran peristiwa ucapan, BUKAN StateFlow.
     *
     * Alasan: `MutableStateFlow` melakukan conflation berdasarkan `equals`, sehingga dua reli
     * berturut-turut yang menghasilkan daftar teks identik tidak akan dipancarkan ulang dan
     * ucapan untuk perubahan skor tersebut hilang (melanggar PRD §7.4). Channel dengan buffer
     * berlebih menyimpan setiap peristiwa sehingga setiap perubahan skor tetap menghasilkan
     * tepat satu pemancaran, sekaligus tidak memblokir pemanggil.
     */
    private val announcementChannel = Channel<AnnouncementEvent>(
        capacity = Channel.BUFFERED,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val announcements: Flow<AnnouncementEvent> = announcementChannel.receiveAsFlow()

    /**
     * Antrean tulis snapshot skor. Capacity 1 + DROP_OLDEST berarti hanya snapshot TERBARU yang
     * tertulis saat beberapa perubahan datang beruntun; ini menjaga urutan sekaligus mengurangi
     * jumlah penulisan disk.
     */
    private val persistQueue = Channel<MatchState>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * Default suara MENYALA pada instalasi baru (keputusan product owner): fitur inti aplikasi
     * adalah mengucapkan skor, dan PRD §3 melarang setup rumit. Nilai yang sudah tersimpan selalu
     * menang atas default ini; lihat blok `init` yang menimpanya bila preferensi ada.
     */
    private val _voiceEnabled = MutableStateFlow(true)
    val voiceEnabled: StateFlow<Boolean> = _voiceEnabled.asStateFlow()

    private val _leftLabel = MutableStateFlow("Kiri")
    val leftLabel: StateFlow<String> = _leftLabel.asStateFlow()

    private val _rightLabel = MutableStateFlow("Kanan")
    val rightLabel: StateFlow<String> = _rightLabel.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val prefs = dataStore.data.first()
                prefs[VOICE_ENABLED]?.let { _voiceEnabled.value = it }
                prefs[LEFT_LABEL]?.takeIf { it.isNotBlank() }?.let { _leftLabel.value = it }
                prefs[RIGHT_LABEL]?.takeIf { it.isNotBlank() }?.let { _rightLabel.value = it }
            }
        }
        // Penulis tunggal: menjaga urutan tulis agar snapshot lama tidak menimpa snapshot baru
        // saat tombol ditekan cepat (DataStore.edit dari beberapa coroutine dapat saling sisip).
        viewModelScope.launch(Dispatchers.IO) {
            for (snapshot in persistQueue) {
                runCatching {
                    dataStore.edit { prefs ->
                        prefs[CURRENT_SET] = snapshot.currentSet
                        prefs[LEFT_POINTS] = snapshot.leftPoints
                        prefs[RIGHT_POINTS] = snapshot.rightPoints
                        prefs[SETS_WON_LEFT] = snapshot.setsWonLeft
                        prefs[SETS_WON_RIGHT] = snapshot.setsWonRight
                        prefs[COMPLETED_SETS] = encodeCompletedSets(snapshot.completedSets)
                        prefs[SERVING_SIDE] = snapshot.servingSide.name
                        prefs[MATCH_FINISHED] = snapshot.matchFinished
                    }
                }
            }
        }
    }

    fun addPoint(side: Side) = transition { before ->
        val after = MatchRules.addPoint(before, side)
        Transition(after, Announcement.forPoint(before, after, side).texts, after !== before)
    }

    fun undo() = transition { before -> Transition(MatchRules.undo(before), emptyList(), false) }

    fun startNextSet() = transition { before ->
        val after = MatchRules.startNextSet(before)
        val texts = if (after === before) emptyList() else listOf(ScoreFormatter.nextSetStart(after.currentSet))
        Transition(after, texts, false)
    }

    fun reset() = transition { Transition(MatchState(), emptyList(), false) }

    fun setVoiceEnabled(enabled: Boolean) {
        _voiceEnabled.value = enabled
        persistSettings()
    }

    fun setLeftLabel(label: String) {
        _leftLabel.value = label
        persistSettings()
    }

    fun setRightLabel(label: String) {
        _rightLabel.value = label
        persistSettings()
    }

    private inline fun transition(crossinline compute: (MatchState) -> Transition) {
        val before = _state.value
        val result = compute(before)
        _state.value = result.after
        publish(result.after, result.texts, result.playCue)
    }

    private fun publish(after: MatchState, texts: List<String>, playCue: Boolean) {
        if (texts.isNotEmpty()) announcementChannel.trySend(AnnouncementEvent(texts, playCue))
        persistMatch(after)
    }

    private data class Transition(
        val after: MatchState,
        val texts: List<String>,
        val playCue: Boolean
    )

    private suspend fun readSnapshot(): MatchState {
        val prefs = dataStore.data.first()
        val completed = decodeCompletedSets(prefs[COMPLETED_SETS])
        val snapshot = MatchStateSnapshot(
            currentSet = prefs[CURRENT_SET] ?: 1,
            leftPoints = prefs[LEFT_POINTS] ?: 0,
            rightPoints = prefs[RIGHT_POINTS] ?: 0,
            setsWonLeft = prefs[SETS_WON_LEFT] ?: 0,
            setsWonRight = prefs[SETS_WON_RIGHT] ?: 0,
            completedSets = completed,
            servingSide = Side.of(prefs[SERVING_SIDE] ?: Side.LEFT.name),
            matchFinished = prefs[MATCH_FINISHED] ?: false
        )
        return snapshot.toState()
    }

    private fun decodeCompletedSets(raw: String?): List<Pair<Int, Int>> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(";").mapNotNull { token ->
            val parts = token.split(":")
            if (parts.size != 2) null
            else parts[0].trim().toIntOrNull()?.let { left ->
                parts[1].trim().toIntOrNull()?.let { right -> left to right }
            }
        }
    }

    private fun encodeCompletedSets(sets: List<Pair<Int, Int>>): String =
        sets.joinToString(";") { (left, right) -> "$left:$right" }

    private fun persistMatch(state: MatchState) {
        persistQueue.trySend(state.minimal())
    }

    private fun persistSettings() {
        val enabled = _voiceEnabled.value
        val left = _leftLabel.value
        val right = _rightLabel.value
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                dataStore.edit { prefs ->
                    prefs[VOICE_ENABLED] = enabled
                    prefs[LEFT_LABEL] = left
                    prefs[RIGHT_LABEL] = right
                }
            }
        }
    }

    private companion object {
        val CURRENT_SET = intPreferencesKey("current_set")
        val LEFT_POINTS = intPreferencesKey("left_points")
        val RIGHT_POINTS = intPreferencesKey("right_points")
        val SETS_WON_LEFT = intPreferencesKey("sets_won_left")
        val SETS_WON_RIGHT = intPreferencesKey("sets_won_right")
        val COMPLETED_SETS = stringPreferencesKey("completed_sets")
        val SERVING_SIDE = stringPreferencesKey("serving_side")
        val MATCH_FINISHED = booleanPreferencesKey("match_finished")
        val VOICE_ENABLED = booleanPreferencesKey("voice_enabled")
        val LEFT_LABEL = stringPreferencesKey("left_label")
        val RIGHT_LABEL = stringPreferencesKey("right_label")
    }
}
