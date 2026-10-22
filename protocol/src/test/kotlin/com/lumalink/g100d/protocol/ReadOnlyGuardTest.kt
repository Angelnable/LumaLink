package com.lumalink.g100d.protocol

import com.google.common.truth.Truth.assertThat
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertThrows
import org.junit.Test

class ReadOnlyGuardTest {

    private val guard = ReadOnlyGuard()

    @Test
    fun allowsMetadataAndFileReads() {
        guard.check("GET", "http://192.168.54.1/cam.cgi?mode=getstate".toHttpUrl())
        guard.check("GET", "http://192.168.54.1/cam.cgi?mode=getinfo".toHttpUrl())
        guard.check("GET", "http://192.168.54.1/cam.cgi?mode=browse&dir=/DCIM".toHttpUrl())
        guard.check("GET", "http://192.168.54.1/DCIM/100_PANA/P1000001.JPG".toHttpUrl())
        guard.check(
            "GET",
            "http://192.168.54.1:50001/DCIM/100_PANA/P1000001.JPG".toHttpUrl(),
        )
    }

    @Test
    fun blocksCaptureAndSettingsCommands() {
        assertThrows(LumixError.ReadOnlyViolation::class.java) {
            guard.check(
                "GET",
                "http://192.168.54.1/cam.cgi?mode=camcmd&value=capture".toHttpUrl(),
            )
        }
        assertThrows(LumixError.ReadOnlyViolation::class.java) {
            guard.check(
                "GET",
                "http://192.168.54.1/cam.cgi?mode=setsetting&type=iso&value=800".toHttpUrl(),
            )
        }
    }

    @Test
    fun blocksWritesAndNonLocalHosts() {
        assertThrows(LumixError.ReadOnlyViolation::class.java) {
            guard.check("POST", "http://192.168.54.1/cam.cgi?mode=browse".toHttpUrl())
        }
        assertThrows(LumixError.ReadOnlyViolation::class.java) {
            guard.check("GET", "https://example.com/cam.cgi?mode=getstate".toHttpUrl())
        }
    }

    @Test
    fun hostGuardOnlyAcceptsPrivateAndLinkLocalAddresses() {
        assertThat(HostGuard.isLocalHost("192.168.54.1")).isTrue()
        assertThat(HostGuard.isLocalHost("10.0.0.2")).isTrue()
        assertThat(HostGuard.isLocalHost("172.20.1.1")).isTrue()
        assertThat(HostGuard.isLocalHost("169.254.1.1")).isTrue()
        assertThat(HostGuard.isLocalHost("127.0.0.1")).isTrue()
        assertThat(HostGuard.isLocalHost("8.8.8.8")).isFalse()
        assertThat(HostGuard.isLocalHost("camera.example.com")).isFalse()
    }
}
