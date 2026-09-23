package com.quistock.quistock.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quistock.quistock.app.di.roomModule
import com.quistock.quistock.app.di.roomSdkModule
import com.quistock.quistock.data.local.dao.BigNumbersDao
import com.quistock.quistock.data.local.entity.BigNumbersEntity
import com.quistock.quistock.data.local.repository.RoomBigNumbersRepository
import com.quistock.quistock.domain.cache.CachedValue
import com.quistock.quistock.domain.model.BigNumbers
import com.quistock.quistock.domain.port.CachedBigNumbersRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.koinApplication
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class RoomBigNumbersIntegrationTests {
    private lateinit var database: AppDatabase

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun latestQueryReturnsMostRecentStoredEntry() = runBlocking {
        val earlier = Instant.parse("2026-09-22T12:00:00Z")
        val later = Instant.parse("2026-09-23T12:00:00Z")
        val dao = database.bigNumbersDao()
        dao.insert(entity(savedAt = later, count = 2))
        dao.insert(entity(savedAt = earlier, count = 1))

        assertEquals(2, dao.getLatest()?.nearExpirationProductCount)
    }

    @Test
    fun repositoryPersistsAndReadsCachedValueThroughRoom() = runBlocking {
        val repository = RoomBigNumbersRepository(database.bigNumbersDao())
        val savedAt = Instant.parse("2026-09-23T12:34:56.789Z")
        val expiresAt = Instant.parse("2026-09-24T00:00:00Z")
        val numbers = BigNumbers(12, 3, 7)

        repository.save(CachedValue(numbers, savedAt, expiresAt))

        val result = repository.read()
        assertEquals(numbers, result?.value)
        assertEquals(savedAt, result?.savedAt)
        assertEquals(expiresAt, result?.expiresAt)
    }

    @Test
    fun roomModulesResolveRepositoryAndDao() {
        val koinApp = koinApplication {
            androidContext(InstrumentationRegistry.getInstrumentation().targetContext)
            modules(roomSdkModule, roomModule)
        }

        try {
            val repository = koinApp.koin.get<CachedBigNumbersRepository>()
            assertTrue(repository is RoomBigNumbersRepository)
            assertEquals(koinApp.koin.get<AppDatabase>().bigNumbersDao(), koinApp.koin.get<BigNumbersDao>())
        } finally {
            koinApp.koin.get<AppDatabase>().close()
            koinApp.close()
        }
    }

    private fun entity(savedAt: Instant, count: Int) = BigNumbersEntity(
        savedAt = savedAt,
        expiresAt = Instant.parse("2026-09-24T00:00:00Z"),
        nearExpirationProductCount = count,
        criticalAnalyzedFlowCount = 3,
        activeActionCount = 7,
    )
}
