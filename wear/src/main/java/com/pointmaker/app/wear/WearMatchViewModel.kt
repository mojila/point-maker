package com.pointmaker.app.wear

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
import com.pointmaker.app.ui.MatchStateSnapshot
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

private val Application.wearMatchDataStore: DataStore<Preferences> by preferencesDataStore(name = "match_state")

class WearMatchViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore: DataStore<Preferences> = application.wearMatchDataStore

    private val restored: MatchState = runCatching {
        android.os.StrictMode.allowThreadDiskReads()
        runBlocking(Dispatchers.IO) { readSnapshot() }
    }.getOrElse { MatchState() }

    private val _state = MutableStateFlow(restored)
    val state: StateFlow<MatchState> = _state.asStateFlow()

    /**
     * Aliran peristiwa ucapan, BUKAN StateFlow.
     *
     * Alasan sama dengan versi HP: `MutableStateFlow` melakukan conflation berdasarkan `equals`
     * sehingga dua reli berturut-turut dengan teks identik tidak dipancarkan ulang dan ucapan
     * untuk perubahan skor tersebut hilang. Channel menyimpan setiap peristiwa sehingga setiap
     * perubahan skor tetap menghasilkan tepat satu pemancaran.
     */
    private val announcementChannel = Channel<WearAnnouncement>(
        capacity = Channel.BUFFERED,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val announcements: Flow<WearAnnouncement> = announcementChannel.receiveAsFlow()

    /**
     * Antrean tulis snapshot skor. Capacity 1 + DROP_OLDEST berarti hanya snapshot TERBARU yang
     * tertulis saat beberapa perubahan datang beruntun. Perangkat `ram.low` sering mematikan
     * proses, jadi setiap perubahan poin langsung masuk antrean ini (tanpa menunggu lifecycle).
     */
    private val persistQueue = Channel<MatchState>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val _voiceEnabled = MutableStateFlow(true)
    val voiceEnabled: StateFlow<Boolean> = _voiceEnabled.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val prefs = dataStore.data.first()
                prefs[VOICE_ENABLED]?.let { _voiceEnabled.value = it }
            }
        }
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
        val announcement = Announcement.forPoint(before, after, side)
        after to WearAnnouncement(
            announcement.texts,
            WearFeedbackPolicy.forPriority(announcement.priority),
            playCue = after !== before
        )
    }

    fun undo() = transition { before ->
        MatchRules.undo(before) to WearAnnouncement(emptyList(), WearFeedback.UNDO, playCue = false)
    }

    fun startNextSet() = transition { before ->
        val after = MatchRules.startNextSet(before)
        val texts = if (after === before) emptyList() else listOf(ScoreFormatter.nextSetStart(after.currentSet))
        after to WearAnnouncement(texts, WearFeedback.POINT, playCue = false)
    }

    fun reset() = transition { MatchState() to WearAnnouncement(emptyList(), WearFeedback.NONE, playCue = false) }

    fun setVoiceEnabled(enabled: Boolean) {
        _voiceEnabled.value = enabled
        persistSettings()
    }

    private inline fun transition(crossinline compute: (MatchState) -> Pair<MatchState, WearAnnouncement>) {
        val before = _state.value
        val (after, announcement) = compute(before)
        _state.value = after
        publish(after, announcement)
    }

    private fun publish(after: MatchState, announcement: WearAnnouncement) {
        if (announcement.feedback != WearFeedback.NONE || announcement.texts.isNotEmpty()) {
            announcementChannel.trySend(announcement)
        }
        persistMatch(after)
    }

    private suspend fun readSnapshot(): MatchState {
        val prefs = dataStore.data.first()
        return MatchStateSnapshot(
            currentSet = prefs[CURRENT_SET] ?: 1,
            leftPoints = prefs[LEFT_POINTS] ?: 0,
            rightPoints = prefs[RIGHT_POINTS] ?: 0,
            setsWonLeft = prefs[SETS_WON_LEFT] ?: 0,
            setsWonRight = prefs[SETS_WON_RIGHT] ?: 0,
            completedSets = decodeCompletedSets(prefs[COMPLETED_SETS]),
            servingSide = Side.of(prefs[SERVING_SIDE] ?: Side.LEFT.name),
            matchFinished = prefs[MATCH_FINISHED] ?: false
        ).toState()
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
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                dataStore.edit { prefs -> prefs[VOICE_ENABLED] = enabled }
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
    }
}
