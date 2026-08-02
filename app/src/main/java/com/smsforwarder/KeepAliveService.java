package com.smsforwarder;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * CS TELECOM - KeepAlive Service v3.3
 *
 * A cada 1 minuto: heartbeat pro servidor (registro do aparelho)
 * A cada 60 minutos: verifica se há nova versão do app disponível
 */
public class KeepAliveService extends Service {

    private static final String TAG       = "CS_KeepAlive";
    private static final String CH_UPDATE = "cs_update";
    private static final int    NOTIF_ID  = 1001;

    // Versão atual do app — atualizar a cada novo APK gerado
    public static final String APP_VERSAO = "3.3";

    private Handler  handler;
    private Runnable tarefa;
    private int      tickCount = 0;

    private static final long INTERVALO_HEARTBEAT = 60_000L;   // 1 minuto
    private static final int  TICKS_UPDATE_CHECK  = 60;        // a cada 60 ticks = 60 min

    @Override
    public void onCreate() {
        super.onCreate();
        criarCanalNotificacao();
        handler = new Handler(Looper.getMainLooper());
        tarefa  = new Runnable() {
            @Override
            public void run() {
                enviarHeartbeat();
                tickCount++;
                if (tickCount % TICKS_UPDATE_CHECK == 0) {
                    verificarAtualizacao();
                }
                handler.postDelayed(this, INTERVALO_HEARTBEAT);
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handler.removeCallbacks(tarefa);
        handler.post(tarefa);
        verificarAtualizacao(); // também verifica ao iniciar
        return START_STICKY;
    }

    // ── HEARTBEAT ──────────────────────────────────────────────────────────────
    private void enviarHeartbeat() {
        final List<MainActivity.InfoSim> sims = MainActivity.descobrirSIMs(this);
        final String modelo = nomeAparelho();

        new Thread(() -> {
            try {
                String endpoint = MainActivity.SERVER_URL;
                if (!endpoint.endsWith("/")) endpoint += "/";
                endpoint += "api/registro";

                JSONArray numArray = new JSONArray();
                StringBuilder aparStr = new StringBuilder();
                String primeiraOp = "";

                for (MainActivity.InfoSim sim : sims) {
                    numArray.put(sim.numero);
                    if (aparStr.length() > 0) aparStr.append(" / ");
                    aparStr.append(sim.numero);
                    if (primeiraOp.isEmpty() && !sim.operadora.isEmpty()) {
                        primeiraOp = sim.operadora;
                    }
                }
                if (numArray.length() == 0) aparStr.append(modelo);

                JSONObject json = new JSONObject();
                json.put("aparelho",  aparStr.toString());
                json.put("modelo",    modelo);
                json.put("numeros",   numArray);
                json.put("operadora", primeiraOp);
                json.put("versao",    APP_VERSAO);

                HttpURLConnection conn =
                        (HttpURLConnection) new URL(endpoint).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setDoOutput(true);
                conn.getOutputStream().write(json.toString().getBytes("UTF-8"));
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception ignored) {}
        }).start();
    }

    // ── VERIFICAÇÃO DE ATUALIZAÇÃO ─────────────────────────────────────────────
    private void verificarAtualizacao() {
        new Thread(() -> {
            try {
                String endpoint = MainActivity.SERVER_URL;
                if (!endpoint.endsWith("/")) endpoint += "/";
                endpoint += "api/app/versao";

                HttpURLConnection conn =
                        (HttpURLConnection) new URL(endpoint).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                conn.disconnect();

                JSONObject resp    = new JSONObject(sb.toString());
                String versaoSrv   = resp.optString("versao", "");
                String apkUrl      = resp.optString("apk_url", "");
                String notas       = resp.optString("notas", "Nova versão disponível");

                if (versaoSrv.isEmpty() || apkUrl.isEmpty()) return;

                // Já notificamos sobre essa versão antes?
                SharedPreferences prefs = getSharedPreferences("cs_prefs", Context.MODE_PRIVATE);
                String jaNotificou = prefs.getString("versao_notificada", "");

                if (!versaoSrv.equals(APP_VERSAO) && !versaoSrv.equals(jaNotificou)) {
                    mostrarNotificacaoAtualizacao(versaoSrv, apkUrl, notas);
                    prefs.edit().putString("versao_notificada", versaoSrv).apply();
                    Log.i(TAG, "Atualização disponível: " + versaoSrv);
                }
            } catch (Exception e) {
                Log.d(TAG, "Verificação de update: " + e.getMessage());
            }
        }).start();
    }

    private void mostrarNotificacaoAtualizacao(String versao, String apkUrl, String notas) {
        NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent pi = PendingIntent.getActivity(
                this, 0, intent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT
        );

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CH_UPDATE)
                        .setSmallIcon(android.R.drawable.stat_sys_download)
                        .setContentTitle("CS Telecom — Atualização v" + versao)
                        .setContentText(notas.isEmpty() ? "Toque para baixar e instalar" : notas)
                        .setStyle(new NotificationCompat.BigTextStyle()
                                .bigText(notas + "\n\nToque para baixar e instalar o novo APK."))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(pi)
                        .setAutoCancel(false)
                        .setOngoing(true);

        nm.notify(NOTIF_ID, builder.build());
    }

    private void criarCanalNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CH_UPDATE,
                    "Atualizações CS Telecom",
                    NotificationManager.IMPORTANCE_HIGH
            );
            ch.setDescription("Notificações de nova versão do app");
            NotificationManager nm =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private String nomeAparelho() {
        String fab = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER;
        String mod = Build.MODEL       == null ? "" : Build.MODEL;
        String nome = (fab + " " + mod).trim();
        return nome.isEmpty() ? "Aparelho Android" : nome;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null) handler.removeCallbacks(tarefa);
        try { startService(new Intent(this, KeepAliveService.class)); } catch (Exception ignored) {}
    }
}
