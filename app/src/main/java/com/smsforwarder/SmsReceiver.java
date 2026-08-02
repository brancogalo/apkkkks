package com.smsforwarder;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * CS TELECOM - SMS Receiver v3.0
 *
 * Recebe cada SMS e encaminha ao servidor com:
 *  - numero_remetente: quem enviou o SMS
 *  - mensagem:         o texto do SMS
 *  - aparelho:         números do aparelho (todos os SIMs)
 *  - numero_receptor:  número do SIM específico que recebeu o SMS ← NOVO
 *
 * O servidor usa numero_receptor para rotear o SMS ao membro correto.
 */
public class SmsReceiver extends BroadcastReceiver {

    private static final String TAG = "CS_SmsReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getExtras() == null) return;
        try {
            Bundle bundle = intent.getExtras();
            Object[] pdus = (Object[]) bundle.get("pdus");
            if (pdus == null) return;
            String format = bundle.getString("format");

            // Descobre qual SIM (subscriptionId) recebeu este SMS
            final String numeroReceptor = descobrirNumeroReceptor(context, bundle);

            // Identificação geral do aparelho (todos os SIMs)
            final String aparelho = identificarAparelho(context);

            for (Object pdu : pdus) {
                SmsMessage sms;
                if (format != null) {
                    sms = SmsMessage.createFromPdu((byte[]) pdu, format);
                } else {
                    sms = SmsMessage.createFromPdu((byte[]) pdu);
                }
                final String numero   = sms.getOriginatingAddress();
                final String mensagem = sms.getMessageBody();

                new Thread(() -> enviarParaServidor(numero, mensagem, aparelho, numeroReceptor)).start();
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao processar SMS", e);
        }
    }

    /**
     * Descobre qual número de telefone (SIM) recebeu este SMS específico.
     * Usa o subscriptionId do bundle para localizar o número correto.
     */
    private String descobrirNumeroReceptor(Context context, Bundle bundle) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
                return "";
            }
            if (ContextCompat.checkSelfPermission(context,
                    Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                return "";
            }

            // Pega o subscriptionId do bundle (qual SIM recebeu)
            int subId = -1;
            if (bundle.containsKey("subscription")) {
                subId = bundle.getInt("subscription", -1);
            } else if (bundle.containsKey("phone")) {
                subId = bundle.getInt("phone", -1);
            }

            if (subId < 0) {
                // Não conseguiu o subId — retorna primeiro número disponível
                return primeiroNumeroDisponivel(context);
            }

            SubscriptionManager sm = (SubscriptionManager)
                    context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            if (sm == null) return "";

            SubscriptionInfo info = sm.getActiveSubscriptionInfo(subId);
            if (info != null) {
                String num = null;
                try { num = info.getNumber(); } catch (Exception ignored) {}
                if (num != null && !num.trim().isEmpty()) {
                    return limparNumero(num);
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    /** Retorna o primeiro número de telefone disponível no aparelho. */
    private String primeiroNumeroDisponivel(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                        == PackageManager.PERMISSION_GRANTED) {
                SubscriptionManager sm = (SubscriptionManager)
                        context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                if (sm != null) {
                    List<SubscriptionInfo> lista = sm.getActiveSubscriptionInfoList();
                    if (lista != null && !lista.isEmpty()) {
                        String num = null;
                        try { num = lista.get(0).getNumber(); } catch (Exception ignored) {}
                        if (num != null && !num.trim().isEmpty()) return limparNumero(num);
                    }
                }
            }
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                String num = null;
                try { num = tm.getLine1Number(); } catch (Exception ignored) {}
                if (num != null && !num.trim().isEmpty()) return limparNumero(num);
            }
        } catch (Exception ignored) {}
        return "";
    }

    /** Remove o código +55 e caracteres não numéricos. */
    private String limparNumero(String numero) {
        String digits = numero.replaceAll("\\D", "");
        if (digits.startsWith("55") && digits.length() >= 12) {
            digits = digits.substring(2);
        }
        return digits;
    }

    /** Retorna todos os números do aparelho concatenados (para o campo aparelho). */
    private String identificarAparelho(Context context) {
        try {
            if (ContextCompat.checkSelfPermission(context,
                    Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    SubscriptionManager sm = (SubscriptionManager)
                            context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                    if (sm != null) {
                        List<SubscriptionInfo> lista = sm.getActiveSubscriptionInfoList();
                        if (lista != null) {
                            StringBuilder nums = new StringBuilder();
                            for (SubscriptionInfo info : lista) {
                                String num = null;
                                try { num = info.getNumber(); } catch (Exception ignored) {}
                                if (num != null && !num.trim().isEmpty()) {
                                    if (nums.length() > 0) nums.append(" / ");
                                    nums.append(limparNumero(num));
                                }
                            }
                            if (nums.length() > 0) return nums.toString();
                        }
                    }
                }
                TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                if (tm != null) {
                    String num = null;
                    try { num = tm.getLine1Number(); } catch (Exception ignored) {}
                    if (num != null && !num.trim().isEmpty()) return limparNumero(num);
                }
            }
        } catch (Exception ignored) {}
        // Fallback: modelo do aparelho
        String fab = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER;
        String mod = Build.MODEL       == null ? "" : Build.MODEL;
        return (fab + " " + mod).trim();
    }

    private void enviarParaServidor(String numero, String mensagem,
                                    String aparelho, String numeroReceptor) {
        HttpURLConnection conn = null;
        try {
            String endpoint = MainActivity.SERVER_URL;
            if (!endpoint.endsWith("/")) endpoint += "/";
            endpoint += "api/sms";

            URL url = new URL(endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setDoOutput(true);

            JSONObject json = new JSONObject();
            json.put("numero",          numero);
            json.put("mensagem",        mensagem);
            json.put("aparelho",        aparelho);
            json.put("numero_receptor", numeroReceptor);    // qual SIM recebeu ← chave para roteamento
            json.put("timestamp",       java.time.Instant.now().toString());

            OutputStream os = conn.getOutputStream();
            os.write(json.toString().getBytes("UTF-8"));
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            Log.d(TAG, "Servidor: " + code + " | de=" + numero + " → receptor=" + numeroReceptor);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao enviar SMS", e);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
