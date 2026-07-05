package com.guardia.app.core.alerts

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import com.guardia.app.data.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Properties
import javax.activation.DataHandler
import javax.inject.Inject
import javax.inject.Singleton
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.util.ByteArrayDataSource

/**
 * Sends intruder/lock alerts over email (SMTP) and SMS, and is the entry point for
 * find-my-phone responses. All configuration comes from [AppPreferences]; nothing is sent
 * unless the user has enabled and configured a channel.
 */
@Singleton
class AlertsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences,
) {
    /** Fired by [com.guardia.app.core.guard.Responder] when an intruder is detected. */
    suspend fun onSecurityEvent(summary: String, jpegBytes: ByteArray?) {
        runCatching { maybeSendEmail("Guardia security alert", summary, jpegBytes) }
            .onFailure { Log.w(TAG, "email alert failed", it) }
        runCatching { maybeSendSms("Guardia: $summary") }
            .onFailure { Log.w(TAG, "sms alert failed", it) }
    }

    private suspend fun maybeSendSms(text: String) {
        if (!prefs.smsAlertsEnabled.first()) return
        val number = prefs.trustedNumber.first()
        if (number.isBlank()) return
        sendSms(number, text)
    }

    fun sendSms(number: String, text: String) {
        val sms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION") SmsManager.getDefault()
        }
        val parts = sms.divideMessage(text)
        sms.sendMultipartTextMessage(number, null, parts, null, null)
    }

    private suspend fun maybeSendEmail(subject: String, body: String, jpegBytes: ByteArray?) {
        if (!prefs.emailAlertsEnabled.first()) return
        val host = prefs.smtpHost.first()
        val port = prefs.smtpPort.first()
        val user = prefs.smtpUser.first()
        val pass = prefs.smtpPassword.first()
        val recipient = prefs.alertRecipient.first()
        if (user.isBlank() || pass.isBlank() || recipient.isBlank()) return

        withContext(Dispatchers.IO) {
            val props = Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.host", host)
                put("mail.smtp.port", port.toString())
                put("mail.smtp.connectiontimeout", "15000")
                put("mail.smtp.timeout", "15000")
            }
            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication() = PasswordAuthentication(user, pass)
            })
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(user))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient))
                this.subject = subject
            }
            val multipart = MimeMultipart()
            multipart.addBodyPart(MimeBodyPart().apply { setText(body) })
            if (jpegBytes != null && jpegBytes.isNotEmpty()) {
                multipart.addBodyPart(MimeBodyPart().apply {
                    dataHandler = DataHandler(ByteArrayDataSource(jpegBytes, "image/jpeg"))
                    fileName = "intruder.jpg"
                })
            }
            message.setContent(multipart)
            Transport.send(message)
        }
    }

    companion object {
        private const val TAG = "AlertsManager"
    }
}
