package com.scantoftp.ui.viewmodel

import com.scantoftp.domain.model.FlashMode
import com.scantoftp.domain.model.FtpSettings
import com.scantoftp.domain.model.RemoteDirectoryEntry
import com.scantoftp.domain.model.RemoteDirectoryListing
import com.scantoftp.domain.model.ServerProfile
import com.scantoftp.domain.repository.SettingsRepository
import com.scantoftp.domain.service.UploadClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    
    private val settingsRepository: SettingsRepository = mock()
    private val uploadClient: UploadClient = mock()

    private val settingsFlow = MutableStateFlow(FtpSettings())
    private val profilesFlow = MutableStateFlow(emptyList<ServerProfile>())
    private val activeProfileIdFlow = MutableStateFlow<String?>(null)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(settingsRepository.settingsFlow()).thenReturn(settingsFlow)
        whenever(settingsRepository.profilesFlow()).thenReturn(profilesFlow)
        whenever(settingsRepository.activeProfileIdFlow()).thenReturn(activeProfileIdFlow)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial states match default configurations`() = runTest {
        val viewModel = SettingsViewModel(settingsRepository, uploadClient)
        advanceUntilIdle()

        assertEquals(FtpSettings(), viewModel.settings.value)
        assertTrue(viewModel.profiles.value.isEmpty())
        assertNull(viewModel.activeProfileId.value)
        assertFalse(viewModel.remoteBrowser.value.isVisible)
        assertFalse(viewModel.remoteBrowser.value.isLoading)
        assertEquals("/", viewModel.remoteBrowser.value.currentPath)
    }

    @Test
    fun `profile lookup and CRUD delegates to settingsRepository`() = runTest {
        val viewModel = SettingsViewModel(settingsRepository, uploadClient)
        val collectJob = launch { viewModel.profiles.collect {} }
        advanceUntilIdle()
        
        val p1 = ServerProfile("id1", "Home NAS", com.scantoftp.domain.model.UploadProtocol.Ftp, "192.168.1.10", 21, "user", "pass", "/scans")
        val p2 = ServerProfile("id2", "Work Server", com.scantoftp.domain.model.UploadProtocol.Ftps, "10.0.0.5", 21, "user2", "pass2", "/work")
        profilesFlow.value = listOf(p1, p2)
        advanceUntilIdle()

        // Test profile lookup
        assertEquals(p1, viewModel.profileById("id1"))
        assertEquals(p2, viewModel.profileById("id2"))
        assertNull(viewModel.profileById("non_existent"))

        // Test CRUD operations
        viewModel.saveProfile(p1)
        advanceUntilIdle()
        verify(settingsRepository).upsertProfile(p1)

        viewModel.deleteProfile("id1")
        advanceUntilIdle()
        verify(settingsRepository).deleteProfile("id1")

        viewModel.setActiveProfile("id2")
        advanceUntilIdle()
        verify(settingsRepository).setActiveProfile("id2")

        collectJob.cancel()
    }

    @Test
    fun `preference updates delegate correctly to settingsRepository`() = runTest {
        val viewModel = SettingsViewModel(settingsRepository, uploadClient)
        advanceUntilIdle()

        viewModel.setImageQuality(85f)
        advanceUntilIdle()
        verify(settingsRepository).setImageQuality(85)

        viewModel.setAutoCapture(true)
        advanceUntilIdle()
        verify(settingsRepository).setAutoCapture(true)

        viewModel.setFlashMode(FlashMode.AlwaysOn)
        advanceUntilIdle()
        verify(settingsRepository).setFlashMode(FlashMode.AlwaysOn)

        viewModel.setOcrRenaming(true)
        advanceUntilIdle()
        verify(settingsRepository).setOcrRenaming(true)

        viewModel.setTripSubfolder("/june_trip")
        advanceUntilIdle()
        verify(settingsRepository).setTripSubfolder("/june_trip")

        viewModel.setUploadOnWifiOnly(true)
        advanceUntilIdle()
        verify(settingsRepository).setUploadOnWifiOnly(true)
    }

    @Test
    fun `remote browser folder browsing updates UI loading and directory states on success`() = runTest {
        val viewModel = SettingsViewModel(settingsRepository, uploadClient)
        advanceUntilIdle()

        val dummyListing = RemoteDirectoryListing(
            currentPath = "/scans/receipts",
            directories = listOf(
                RemoteDirectoryEntry("2026", "/scans/receipts/2026"),
                RemoteDirectoryEntry("archive", "/scans/receipts/archive")
            )
        )

        whenever(uploadClient.browseDirectories(any(), eq("/")))
            .thenReturn(Result.success(RemoteDirectoryListing("/", emptyList())))
        whenever(uploadClient.browseDirectories(any(), eq("/scans")))
            .thenReturn(Result.success(dummyListing))

        // Trigger browsing folder
        viewModel.openRemoteBrowser(FtpSettings()) // initializes config and triggers browse into /
        advanceUntilIdle()
        
        // Directly browse into /scans
        viewModel.browseInto("/scans")
        advanceUntilIdle()

        val state = viewModel.remoteBrowser.value
        assertTrue(state.isVisible)
        assertFalse(state.isLoading)
        assertEquals("/scans/receipts", state.currentPath)
        assertEquals(2, state.directories.size)
        assertEquals("2026", state.directories[0].name)
        assertNull(state.errorMessage)

        // Test picked selection closes the browser
        var pickedFolder: String? = null
        viewModel.selectBrowsedFolder { pickedFolder = it }
        advanceUntilIdle()
        
        assertEquals("/scans/receipts", pickedFolder)
        assertFalse(viewModel.remoteBrowser.value.isVisible)
    }

    @Test
    fun `remote browser directory navigation up handles edge cases correctly`() = runTest {
        val viewModel = SettingsViewModel(settingsRepository, uploadClient)
        advanceUntilIdle()

        // Mock directory responses with their corresponding paths so the state is updated properly
        whenever(uploadClient.browseDirectories(any(), eq("/scans/receipts/2026")))
            .thenReturn(Result.success(RemoteDirectoryListing("/scans/receipts/2026", emptyList())))
        whenever(uploadClient.browseDirectories(any(), eq("/scans/receipts")))
            .thenReturn(Result.success(RemoteDirectoryListing("/scans/receipts", emptyList())))
        whenever(uploadClient.browseDirectories(any(), eq("/scans")))
            .thenReturn(Result.success(RemoteDirectoryListing("/scans", emptyList())))
        whenever(uploadClient.browseDirectories(any(), eq("/")))
            .thenReturn(Result.success(RemoteDirectoryListing("/", emptyList())))

        // Setup various path states inside the browser and call browseUp()
        
        // 1. Path is /scans/receipts/2026 -> parent should be /scans/receipts
        viewModel.openRemoteBrowser(FtpSettings())
        advanceUntilIdle()
        
        viewModel.browseInto("/scans/receipts/2026")
        advanceUntilIdle()
        
        viewModel.browseUp()
        advanceUntilIdle()
        verify(uploadClient).browseDirectories(any(), eq("/scans/receipts"))

        // 2. Path is /scans -> parent should be /
        viewModel.browseInto("/scans")
        advanceUntilIdle()
        
        viewModel.browseUp()
        advanceUntilIdle()
        verify(uploadClient, times(2)).browseDirectories(any(), eq("/"))

        // 3. Path is root / -> parent should stay at /
        viewModel.browseInto("/")
        advanceUntilIdle()
        
        viewModel.browseUp()
        advanceUntilIdle()
        verify(uploadClient, times(4)).browseDirectories(any(), eq("/"))
    }

    @Test
    fun `remote browser sets errorMessage on uploadClient failure`() = runTest {
        val viewModel = SettingsViewModel(settingsRepository, uploadClient)
        advanceUntilIdle()

        whenever(uploadClient.browseDirectories(any(), eq("/unreachable")))
            .thenReturn(Result.failure(RuntimeException("FTP connection timed out")))

        viewModel.browseInto("/unreachable")
        advanceUntilIdle()

        val state = viewModel.remoteBrowser.value
        assertTrue(state.isVisible)
        assertFalse(state.isLoading)
        assertEquals("/unreachable", state.currentPath)
        assertTrue(state.directories.isEmpty())
        assertEquals("FTP connection timed out", state.errorMessage)

        // Dismiss browser
        viewModel.dismissRemoteBrowser()
        advanceUntilIdle()
        assertFalse(viewModel.remoteBrowser.value.isVisible)
    }
}
