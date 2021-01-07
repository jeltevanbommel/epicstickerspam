/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jeltier.epic;

import android.app.AppOpsManager;
import android.content.ClipDescription;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputBinding;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;
import androidx.core.content.FileProvider;
import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.core.view.inputmethod.InputConnectionCompat;
import androidx.core.view.inputmethod.InputContentInfoCompat;

import java.io.*;
import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;


public class ImageKeyboard extends InputMethodService {

    private static final String TAG = "ImageKeyboard";
    private static final String AUTHORITY = "com.jeltier.epic";
    private int MAX_STICKERS = 37;
    private Uri[] uris = new Uri[MAX_STICKERS+1];

    private boolean isCommitContentSupported(
            @Nullable EditorInfo editorInfo, @NonNull String mimeType) {
        if (editorInfo == null) {
            return false;
        }

        final InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return false;
        }

        if (!validatePackageName(editorInfo)) {
            return false;
        }

        final String[] supportedMimeTypes = EditorInfoCompat.getContentMimeTypes(editorInfo);
        for (String supportedMimeType : supportedMimeTypes) {
            Log.e(TAG, supportedMimeType);
            if (ClipDescription.compareMimeTypes(mimeType, supportedMimeType)) {
                return true;
            }
        }
        return false;
    }

    private void doCommitContent(@NonNull String description, @NonNull String mimeType,
                                 @NonNull File file) {
        final EditorInfo editorInfo = getCurrentInputEditorInfo();
        if (!validatePackageName(editorInfo)) {
            return;
        }
        final Uri contentUri = FileProvider.getUriForFile(this, AUTHORITY, file);
        final int flag;
        if (Build.VERSION.SDK_INT >= 25) {
            flag = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION;
        } else {
            flag = 0;
            try {
                // TODO: Use revokeUriPermission to revoke as needed.
                grantUriPermission(
                        editorInfo.packageName, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception e) {
                Log.e(TAG, "grantUriPermission failed packageName=" + editorInfo.packageName
                        + " contentUri=" + contentUri, e);
            }
        }

        final InputContentInfoCompat inputContentInfoCompat = new InputContentInfoCompat(
                contentUri,
                new ClipDescription("Image from Gboard", new String[]{mimeType}),
                null);
        InputConnectionCompat.commitContent(
                getCurrentInputConnection(), getCurrentInputEditorInfo(), inputContentInfoCompat,
                flag, null);
    }

    private boolean validatePackageName(@Nullable EditorInfo editorInfo) {
        if (editorInfo == null) {
            return false;
        }
        final String packageName = editorInfo.packageName;
        if (packageName == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return true;
        }

        final InputBinding inputBinding = getCurrentInputBinding();
        if (inputBinding == null) {
            // Due to b.android.com/225029, it is possible that getCurrentInputBinding() returns
            // null even after onStartInputView() is called.
            // TODO: Come up with a way to work around this bug....
            Log.e(TAG, "inputBinding should not be null here. "
                    + "You are likely to be hitting b.android.com/225029");
            return false;
        }
        final int packageUid = inputBinding.getUid();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            final AppOpsManager appOpsManager =
                    (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            try {
                appOpsManager.checkPackage(packageUid, packageName);
            } catch (Exception e) {
                return false;
            }
            return true;
        }

        final PackageManager packageManager = getPackageManager();
        final String possiblePackageNames[] = packageManager.getPackagesForUid(packageUid);
        for (final String possiblePackageName : possiblePackageNames) {
            if (packageName.equals(possiblePackageName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // TODO: Avoid file I/O in the main thread.
        final File imagesDir = new File(getFilesDir(), "images");
        imagesDir.mkdirs();
        for (int i = 0; i <= MAX_STICKERS; i++) {
            uris[i] = FileProvider.getUriForFile(this, AUTHORITY, getFileForResource(this,
                    getResources().getIdentifier(String.format("file_%02d", i),
                            "raw", getPackageName()), imagesDir, "image" + i + ".webp"));
        }
    }

    public List<Uri> range(List<Uri> list, int min, int max, int iterations) {
        if(iterations == 0){
            return list;
        }
        for (int i = min; i <= max; i++) {
            list.add(uris[i]);
            if(iterations == 1){
                grantUriPermission(getCurrentInputEditorInfo().packageName, uris[i], Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
        }
        return range(list, min, max, --iterations);
    }
    @Override
    public View onCreateInputView() {

        Button mWebp2Button = new Button(this);
        mWebp2Button.setText("Monkey sticker 50x");
        mWebp2Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                for (int i = 0; i < 50; i++) {
                    final InputContentInfoCompat inputContentInfoCompat = new InputContentInfoCompat(
                            uris[0], new ClipDescription("Image from Gboard", new String[]{"image/webp.wasticker"}), null);
                    InputConnectionCompat.commitContent(getCurrentInputConnection(), getCurrentInputEditorInfo(), inputContentInfoCompat, InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null);
                }

            }
        });
        Button mPNG2Button = new Button(this);
        mPNG2Button.setText("Epic sticker x90");
        mPNG2Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                List<Uri> uriList = range(new LinkedList<Uri>(), 1, 30,3);
                for(Uri uri : uriList){

                    final InputContentInfoCompat inputContentInfoCompat = new InputContentInfoCompat(
                            uri, new ClipDescription("Image from Gboard", new String[]{"image/webp.wasticker"}),null); //Uri.parse("https://www.gstatic.com/allo/stickers/pack-5/v3/xxhdpi/2.webp") /* linkUrl */);
                    InputConnectionCompat.commitContent(
                            getCurrentInputConnection(), getCurrentInputEditorInfo(), inputContentInfoCompat,
                            InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null);
                }
            }
        });
        Button mPNG3Button = new Button(this);
        mPNG3Button.setText("Epic sticker x493");
        mPNG3Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                List<Uri> uriList = range(new LinkedList<Uri>(), 1, 30,17);
                for(Uri uri : uriList){

                    final InputContentInfoCompat inputContentInfoCompat = new InputContentInfoCompat(
                            uri, new ClipDescription("Image from Gboard", new String[]{"image/webp.wasticker"}),null); //Uri.parse("https://www.gstatic.com/allo/stickers/pack-5/v3/xxhdpi/2.webp") /* linkUrl */);
                    InputConnectionCompat.commitContent(
                            getCurrentInputConnection(), getCurrentInputEditorInfo(), inputContentInfoCompat,
                            InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null);
                }
            }
        });

        Button mPNG4Button = new Button(this);
        mPNG4Button.setText("Water x250");
        mPNG4Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                List<Uri> uriList = range(new LinkedList<Uri>(), 31, 35,50);
                for(Uri uri : uriList){

                    final InputContentInfoCompat inputContentInfoCompat = new InputContentInfoCompat(
                            uri, new ClipDescription("Image from Gboard", new String[]{"image/webp.wasticker"}),null); //Uri.parse("https://www.gstatic.com/allo/stickers/pack-5/v3/xxhdpi/2.webp") /* linkUrl */);
                    InputConnectionCompat.commitContent(
                            getCurrentInputConnection(), getCurrentInputEditorInfo(), inputContentInfoCompat,
                            InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null);
                }
            }
        });

        Button mPNG5Button = new Button(this);
        mPNG5Button.setText("Hoed x60x2");
        mPNG5Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                List<Uri> uriList = range(new LinkedList<Uri>(), 36, 36,2);
                for(Uri uri : uriList){
                    final InputContentInfoCompat inputContentInfoCompat = new InputContentInfoCompat(
                            uri, new ClipDescription("Image from Gboard", new String[]{"image/webp.wasticker"}), null);
                    InputConnectionCompat.commitContent(getCurrentInputConnection(), getCurrentInputEditorInfo(), inputContentInfoCompat, InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null);
                }
                uriList = range(new LinkedList<Uri>(), 37, 37,120);
                for(Uri uri : uriList){
                    final InputContentInfoCompat inputContentInfoCompat = new InputContentInfoCompat(
                            uri, new ClipDescription("Image from Gboard", new String[]{"image/webp.wasticker"}), null);
                    InputConnectionCompat.commitContent(getCurrentInputConnection(), getCurrentInputEditorInfo(), inputContentInfoCompat, InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null);
                }
            }
        });
        Button mPNG6Button = new Button(this);
        mPNG6Button.setText("Hoed middle x60x2");
        mPNG6Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                List<Uri> uriList = range(new LinkedList<Uri>(), 37, 37, 120);
                for(Uri uri : uriList){
                    final InputContentInfoCompat inputContentInfoCompat = new InputContentInfoCompat(
                            uri, new ClipDescription("Image from Gboard", new String[]{"image/webp.wasticker"}), null);
                    InputConnectionCompat.commitContent(getCurrentInputConnection(), getCurrentInputEditorInfo(), inputContentInfoCompat, InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null);
                }
            }
        });
        final LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(mWebp2Button);
        layout.addView(mPNG2Button);
        layout.addView(mPNG3Button);
        layout.addView(mPNG4Button);
        layout.addView(mPNG5Button);
        layout.addView(mPNG6Button);
        return layout;
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
    }

    private static File getFileForResource(
            @NonNull Context context, @RawRes int res, @NonNull File outputDir,
            @NonNull String filename) {
        final File outputFile = new File(outputDir, filename);
        final byte[] buffer = new byte[4096];
        InputStream resourceReader = null;
        try {
            try {
                resourceReader = context.getResources().openRawResource(res);
                OutputStream dataWriter = null;
                try {
                    dataWriter = new FileOutputStream(outputFile);
                    while (true) {
                        final int numRead = resourceReader.read(buffer);
                        if (numRead <= 0) {
                            break;
                        }
                        dataWriter.write(buffer, 0, numRead);
                    }
                    return outputFile;
                } finally {
                    if (dataWriter != null) {
                        dataWriter.flush();
                        dataWriter.close();
                    }
                }
            } finally {
                if (resourceReader != null) {
                    resourceReader.close();
                }
            }
        } catch (IOException e) {
            return null;
        }
    }
}
