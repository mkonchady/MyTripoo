package org.mkonchady.mytripoo.activities;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import org.mkonchady.mytripoo.Logger;

import androidx.core.app.ActivityCompat;

// Used to ask individual permissions in Android 6.0+
public class PermissionActivity extends Activity {
    private final String TAG = "PermissionActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        final int PERM_CODE = 100;
        super.onResume();
        String permission = getIntent().getStringExtra("permission");

        Logger.d(TAG, "Requesting " + permission);
        ActivityCompat.requestPermissions(this, new String[]{permission}, PERM_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        boolean granted;

        Logger.d(TAG, "Permission callback called-------");
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Logger.d(TAG, "Permission Granted");
            granted = true;
        } else {
            Logger.d(TAG, "Permission Denied");
            granted = false;
        }
        Intent data = getIntent();
        data.putExtra("granted", granted + "");
        setResult(Activity.RESULT_OK, data);
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }


}
