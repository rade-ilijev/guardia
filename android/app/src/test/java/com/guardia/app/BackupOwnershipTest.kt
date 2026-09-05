package com.guardia.app

import com.guardia.app.core.backup.BackupOwnership
import com.guardia.app.core.security.PinManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A backup file can reset a forgotten PIN, so the question "is this file mine?" is a credential
 * check and gets tested like one. The failure that matters is a false accept: a stranger's backup
 * being taken for the owner's would let anyone holding the phone reset its PIN.
 */
class BackupOwnershipTest {

    private val mine = PinManager.newToken()
    private val theirs = PinManager.newToken()
    private val enrolled = setOf("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222")

    @Test
    fun matchingTokenIsAccepted() {
        assertTrue(
            BackupOwnership.isOwnedByThisInstall(
                fileToken = mine,
                installToken = mine,
                backupPersonIds = emptyList(),
                knownPersonIds = emptySet(),
            )
        )
    }

    @Test
    fun aStrangersBackupIsRejectedEvenWithTheRightPassword() {
        // The attack this whole mechanism exists to stop: export a backup from your own install,
        // carry it to someone else's phone, and type the password you chose.
        assertFalse(
            BackupOwnership.isOwnedByThisInstall(
                fileToken = theirs,
                installToken = mine,
                backupPersonIds = emptyList(),
                knownPersonIds = emptySet(),
            )
        )
    }

    @Test
    fun aTokenMismatchIsNotRescuedByMatchingPeople() {
        // Otherwise a forger could downgrade the check: they cannot guess a 256-bit token, but a
        // backup made *from the victim's own data* would carry the victim's person IDs.
        assertFalse(
            BackupOwnership.isOwnedByThisInstall(
                fileToken = theirs,
                installToken = mine,
                backupPersonIds = enrolled.toList(),
                knownPersonIds = enrolled,
            )
        )
    }

    @Test
    fun aTokenedFileIsRejectedWhenThisInstallHasNoToken() {
        assertFalse(
            BackupOwnership.isOwnedByThisInstall(
                fileToken = theirs,
                installToken = null,
                backupPersonIds = enrolled.toList(),
                knownPersonIds = enrolled,
            )
        )
    }

    @Test
    fun preTokenBackupsFallBackToPersonIds() {
        assertTrue(
            BackupOwnership.isOwnedByThisInstall(
                fileToken = null,
                installToken = mine,
                backupPersonIds = listOf("22222222-2222-2222-2222-222222222222"),
                knownPersonIds = enrolled,
            )
        )
    }

    @Test
    fun preTokenBackupOfSomeoneElsesPeopleIsRejected() {
        assertFalse(
            BackupOwnership.isOwnedByThisInstall(
                fileToken = null,
                installToken = mine,
                backupPersonIds = listOf("99999999-9999-9999-9999-999999999999"),
                knownPersonIds = enrolled,
            )
        )
    }

    @Test
    fun preTokenBackupProvesNothingWhenNobodyIsEnrolled() {
        // The gap the token was introduced to close: with no enrolled people there is nothing on
        // this device for an old backup to match, so it cannot prove anything either way.
        assertFalse(
            BackupOwnership.isOwnedByThisInstall(
                fileToken = null,
                installToken = mine,
                backupPersonIds = listOf("11111111-1111-1111-1111-111111111111"),
                knownPersonIds = emptySet(),
            )
        )
    }

    @Test
    fun emptyPersonIdsNeverCountAsAMatch() {
        // A malformed entry reads back as "", which must not match an equally malformed row here.
        assertFalse(
            BackupOwnership.isOwnedByThisInstall(
                fileToken = null,
                installToken = null,
                backupPersonIds = listOf(""),
                knownPersonIds = setOf(""),
            )
        )
    }

    @Test
    fun aTruncatedTokenIsRejected() {
        assertFalse(
            BackupOwnership.isOwnedByThisInstall(
                fileToken = mine.dropLast(1),
                installToken = mine,
                backupPersonIds = emptyList(),
                knownPersonIds = emptySet(),
            )
        )
    }
}
