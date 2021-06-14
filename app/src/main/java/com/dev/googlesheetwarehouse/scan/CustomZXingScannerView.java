package com.dev.googlesheetwarehouse.scan;

import android.content.Context;
import android.graphics.Rect;
import android.hardware.Camera;

import me.dm7.barcodescanner.zxing.ZXingScannerView;

public class CustomZXingScannerView extends ZXingScannerView {

    private byte[] mImageData;

    private Camera mCamera;

    public CustomZXingScannerView(Context context) {
        super(context);
    }

/*
    @Override
    protected IViewFinder createViewFinderView(Context context) {
        return new CustomViewFinderView(context);
    }
*/

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        try {
            super.onPreviewFrame(data, camera);
            this.mImageData = data;
            this.mCamera = camera;
        } catch (Exception e) {
            // It is possible that this method is invoked after camera is released.
            e.printStackTrace();
        }
    }

    @Override
    public void setResultHandler(ResultHandler resultHandler) {
        super.setResultHandler(resultHandler);
    }

    public byte[] getImageData() {
        return mImageData;
    }

    public Camera getCamera() {
        return mCamera;
    }

    @Override
    public synchronized Rect getFramingRectInPreview(int previewWidth, int previewHeight) {
        return super.getFramingRectInPreview(previewWidth, previewHeight);
    }
}