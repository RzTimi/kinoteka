package com.kinoteka.app

import com.kinoteka.app.data.sync.SyncManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Когда при входе спрашивать, что делать с оценками на телефоне. */
class AccountSwitchTest {
    @Test fun `another account with data on the phone asks`() =
        assertTrue(SyncManager.needsSwitchPrompt(owner = "a@gmail.com", email = "b@gmail.com", hasEntries = true))

    @Test fun `same account does not ask (case-insensitive)`() =
        assertFalse(SyncManager.needsSwitchPrompt(owner = "A@gmail.com", email = "a@gmail.com", hasEntries = true))

    @Test fun `empty phone does not ask`() =
        assertFalse(SyncManager.needsSwitchPrompt(owner = "a@gmail.com", email = "b@gmail.com", hasEntries = false))

    @Test fun `ratings never synced to anyone are simply moved to the account`() =
        assertFalse(SyncManager.needsSwitchPrompt(owner = null, email = "b@gmail.com", hasEntries = true))
}
