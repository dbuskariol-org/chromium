// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.share.qrcode.share_tab;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.View;

import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.share.SaveImageNotificationManager;
import org.chromium.chrome.browser.share.ShareImageFileUtils;
import org.chromium.chrome.browser.share.qrcode.QRCodeGenerationRequest;
import org.chromium.ui.modelutil.PropertyModel;
import org.chromium.ui.widget.Toast;
import org.chromium.url.GURL;

/**
 * QrCodeShareMediator is in charge of calculating and setting values for QrCodeShareViewProperties.
 */
class QrCodeShareMediator implements ShareImageFileUtils.OnImageSaveListener {
    private final Context mContext;
    private final PropertyModel mPropertyModel;

    // The number of times the user has attempted to download the QR code in this dialog.
    private int mNumDownloads;

    private long mDownloadStartTime;
    private boolean mIsDownloadInProgress;

    /**
     * The QrCodeScanMediator constructor.
     * @param context The context to use.
     * @param propertyModel The property modelto use to communicate with views.
     */
    QrCodeShareMediator(Context context, PropertyModel propertyModel) {
        mContext = context;
        mPropertyModel = propertyModel;

        if (context instanceof ChromeActivity) {
            GURL url = ((ChromeActivity) context).getActivityTabProvider().get().getUrl();
            refreshQrCode(url.getSpec());
        }
    }

    /**
     * Refreshes the QR Code bitmap for given data.
     * @param data The data to encode.
     */
    protected void refreshQrCode(String data) {
        QRCodeGenerationRequest.QRCodeServiceCallback callback =
                new QRCodeGenerationRequest.QRCodeServiceCallback() {
                    @Override
                    public void onQRCodeAvailable(Bitmap bitmap) {
                        // TODO(skare): If bitmap is null, surface an error.
                        if (bitmap != null) {
                            mPropertyModel.set(QrCodeShareViewProperties.QRCODE_BITMAP, bitmap);
                        }
                    }
                };
        new QRCodeGenerationRequest(data, callback);
    }

    /** Triggers download for the generated QR code bitmap if available. */
    protected void downloadQrCode(View view) {
        Bitmap qrcodeBitmap = mPropertyModel.get(QrCodeShareViewProperties.QRCODE_BITMAP);
        if (qrcodeBitmap != null && !mIsDownloadInProgress) {
            String fileName = mContext.getString(
                    R.string.qr_code_filename_prefix, String.valueOf(System.currentTimeMillis()));
            mIsDownloadInProgress = true;
            mDownloadStartTime = System.currentTimeMillis();
            ShareImageFileUtils.saveBitmapToExternalStorage(mContext, fileName, qrcodeBitmap, this);
        }
        logDownload();
    }

    /** Logs user actions when attempting to download a QR code. */
    private void logDownload() {
        // Always log the singular metric; otherwise it's easy to miss during analysis.
        RecordUserAction.record("SharingQRCode.DownloadQRCode");
        if (mNumDownloads > 0) {
            RecordUserAction.record("SharingQRCode.DownloadQRCodeMultipleAttempts");
        }
        mNumDownloads++;
    }

    // ShareImageFileUtils.OnImageSaveListener implementation.
    @Override
    public void onImageSaved(Uri uri, String displayName) {
        RecordUserAction.record("SharingQRCode.DownloadQRCode.Succeeded");

        mIsDownloadInProgress = false;
        long delta = System.currentTimeMillis() - mDownloadStartTime;
        RecordHistogram.recordMediumTimesHistogram("Sharing.DownloadQRCode.Succeeded.Time", delta);

        // Notify success.
        Toast.makeText(mContext,
                     mContext.getResources().getString(R.string.qr_code_successful_download_title),
                     Toast.LENGTH_LONG)
                .show();
        SaveImageNotificationManager.showSuccessNotification(mContext, uri);
    }

    @Override
    public void onImageSaveError(String displayName) {
        RecordUserAction.record("SharingQRCode.DownloadQRCode.Failed");

        mIsDownloadInProgress = false;
        long delta = System.currentTimeMillis() - mDownloadStartTime;
        RecordHistogram.recordMediumTimesHistogram("Sharing.DownloadQRCode.Failed.Time", delta);

        // Notify failure.
        Toast.makeText(mContext,
                     mContext.getResources().getString(R.string.qr_code_failed_download_title),
                     Toast.LENGTH_LONG)
                .show();
        SaveImageNotificationManager.showFailureNotification(mContext, null);
    }
}
