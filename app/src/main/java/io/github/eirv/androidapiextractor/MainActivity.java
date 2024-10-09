/*
 * This file is part of AndroidApiExtractor.
 *
 * AndroidApiExtractor is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AndroidApiExtractor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AndroidApiExtractor.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.eirv.androidapiextractor;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!hasFilePermission()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.fromParts("package", getPackageName(), null));
                startActivity(intent);
            } else {
                String[] permissions = {
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                };
                requestPermissions(permissions, 0);
            }
        }
        start();
    }

    private void start() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        TextView textView = new TextView(this);
        textView.setTextIsSelectable(true);
        scrollView.addView(
                textView,
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        setContentView(scrollView);
        System.setOut(
                new PrintStream(
                        new ByteArrayOutputStream() {
                            @Override
                            public void write(byte[] buf, int off, int len) {
                                runOnUiThread(() -> textView.append(new String(buf, off, len)));
                            }
                        }));
        System.setErr(System.out);
        new Thread() {
            @Override
            public void run() {
                try {
                    AndroidApiExtractor.main(new String[] {getExternalCacheDir().getPath()});
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    private boolean hasFilePermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ? Environment.isExternalStorageManager()
                : checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED;
    }
}
