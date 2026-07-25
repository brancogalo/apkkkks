package com.example.smsgateway

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log

class SMSReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Received_ACTION) {
            val bundle = intent.extras
            
            try {
                if (bundle != null) {
                    val pdus = bundle.get("pdus") as Array<*>?
                    val messages = mutableListOf<SmsMessage>()
                    
                    for (pdu in pdus!!) {
                        val sms = SmsMessage.createFromPdu(pdu as ByteArray)
                        messages.add(sms)
                    }
                    
                    // Enviar SMS para o servidor
                    for (message in messages) {
                        val sender = message.displayOriginatingAddress
                        val body = message.displayMessageBody
                        val timestamp = System.currentTimeMillis()
                        
                        // Chamar o service para enviar
                        val intent = Intent(context, GatewayService::class.java)
                        intent.action = "SEND_SMS_TO_SERVER"
                        intent.putExtra("sender", sender)
                        intent.putExtra("body", body)
                        intent.putExtra("timestamp", timestamp)
                        context.startService(intent)
                        
                        Log.d("SMSReceiver", "SMS recebido: $sender - $body")
                    }
                    
                    // IMPORTANTE: Silenciar a notificação do SMS
                    abortBroadcast()
                }
            } catch (e: Exception) {
                Log.e("SMSReceiver", "Erro ao processar SMS", e)
            }
        }
    }
}
