package com.pointmaker.app.ui

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Mengunci kontrak aliran peristiwa ucapan.
 *
 * Regresi yang dicegah: sebelumnya `announcements` adalah `MutableStateFlow`, yang melakukan
 * conflation berdasarkan `equals`. Dua reli berturut-turut dengan teks identik tidak dipancarkan
 * ulang sehingga ucapan hilang (melanggar PRD §7.4). Channel tidak berperilaku demikian.
 */
class AnnouncementStreamTest {

    @Test
    fun `stateflow hanya menyimpan nilai terakhir sehingga peristiwa identik hilang`() = runBlocking {
        val conflated = MutableStateFlow<List<String>>(emptyList())
        val identical = listOf("satu, nol")

        conflated.value = identical
        assertEquals(identical, conflated.value)

        // StateFlow adalah penyimpan nilai, bukan antrean peristiwa: tidak ada cara membedakan
        // "belum pernah diucapkan" dari "sudah diucapkan nilainya sama" lewat nilainya saja.
        conflated.value = identical
        assertEquals(identical, conflated.value)
    }

    @Test
    fun `channel menyampaikan setiap peristiwa walau isinya identik`() = runBlocking {
        val channel = Channel<List<String>>(capacity = Channel.BUFFERED)
        val identical = listOf("satu, nol")

        channel.trySend(identical)
        channel.trySend(identical)
        channel.trySend(identical)
        channel.close()

        val received = mutableListOf<List<String>>()
        while (true) {
            val result = channel.tryReceive()
            if (result.isSuccess) received.add(result.getOrThrow()) else break
        }

        assertEquals(3, received.size)
    }
}
