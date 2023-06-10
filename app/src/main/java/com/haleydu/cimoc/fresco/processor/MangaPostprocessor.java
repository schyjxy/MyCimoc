package com.haleydu.cimoc.fresco.processor;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Base64;
import android.util.Log;

import com.facebook.cache.common.CacheKey;
import com.facebook.cache.common.SimpleCacheKey;
import com.facebook.common.references.CloseableReference;
import com.facebook.imagepipeline.bitmaps.PlatformBitmapFactory;
import com.facebook.imagepipeline.request.BasePostprocessor;
import com.haleydu.cimoc.model.ImageUrl;
import com.haleydu.cimoc.rx.RxBus;
import com.haleydu.cimoc.rx.RxEvent;
import com.haleydu.cimoc.utils.DecryptionUtils;
import com.haleydu.cimoc.utils.StringUtils;

import java.io.UnsupportedEncodingException;
import java.util.Objects;

/**
 * Created by Hiroshi on 2017/3/3.
 */

public class MangaPostprocessor extends BasePostprocessor {

    private ImageUrl mImage;
    private boolean isPaging;
    private boolean isPagingReverse;
    private boolean isWhiteEdge;

    private int mWidth, mHeight;
    private int mPosX, mPosY;
    private boolean isDone = false;
    private boolean jmttIsDone = false;

    public MangaPostprocessor(ImageUrl image, boolean paging, boolean pagingReverse, boolean whiteEdge) {
        mImage = image;
        isPaging = paging;
        isPagingReverse = pagingReverse;
        isWhiteEdge = whiteEdge;
    }

    @Override
    public CloseableReference<Bitmap> process(Bitmap sourceBitmap, PlatformBitmapFactory bitmapFactory) {
        mWidth = sourceBitmap.getWidth();
        mHeight = sourceBitmap.getHeight();

        CloseableReference<Bitmap> reference = bitmapFactory.createBitmap(
                mWidth, mHeight, Bitmap.Config.RGB_565);

        decodeJMTTImage(sourceBitmap, reference);

        if (isPaging && !jmttIsDone) {
            preparePaging(isPagingReverse);
            isDone = true;
        }

        if (isWhiteEdge && !jmttIsDone) {
            prepareWhiteEdge(sourceBitmap);
            isDone = true;
        }

        try {
            if (isDone) {
                if (!jmttIsDone) {
                    reference = bitmapFactory.createBitmap(mWidth, mHeight, Bitmap.Config.RGB_565);
                    processing(sourceBitmap, reference.get());
                }
                return CloseableReference.cloneOrNull(reference);

            } else if (jmttIsDone) {
                return CloseableReference.cloneOrNull(reference);
            }
        } finally {
            CloseableReference.closeSafely(reference);
        }
        return super.process(sourceBitmap, bitmapFactory);
    }

    private void preparePaging(boolean reverse) {
        if (needHorizontalPaging()) {
            mWidth = mWidth / 2;
            if (mImage.getState() == ImageUrl.STATE_NULL) {
                mImage.setState(ImageUrl.STATE_PAGE_1);
                RxBus.getInstance().post(new RxEvent(RxEvent.EVENT_PICTURE_PAGING, mImage));
            }
            mPosX = mImage.getState() == ImageUrl.STATE_PAGE_1 ? mWidth : 0;
            if (reverse)
                mPosX = mImage.getState() == ImageUrl.STATE_PAGE_1 ? 0 : mWidth;
            mPosY = 0;
        } else if (needVerticalPaging()) {
            mHeight = mHeight / 2;
            if (mImage.getState() == ImageUrl.STATE_NULL) {
                mImage.setState(ImageUrl.STATE_PAGE_1);
                RxBus.getInstance().post(new RxEvent(RxEvent.EVENT_PICTURE_PAGING, mImage));
            }
            mPosX = 0;
            mPosY = mImage.getState() == ImageUrl.STATE_PAGE_1 ? 0 : mHeight;
            if (reverse)
                mPosY = mImage.getState() == ImageUrl.STATE_PAGE_1 ? mHeight : 0;
        }
    }

    private boolean needVerticalPaging() {
        return mHeight > 3 * mWidth;
    }

    private boolean needHorizontalPaging() {
        return mWidth > 1.2 * mHeight;
    }

    private void prepareWhiteEdge(Bitmap bitmap) {
        int y1, y2, x1, x2;
        int[] pixels = new int[(mWidth > mHeight ? mWidth : mHeight) * 20];
        int limit = mPosY + mHeight / 3;

        for (y1 = mPosY; y1 < limit; ++y1) {
            // 确定上线 y1
            bitmap.getPixels(pixels, 0, mWidth, mPosX, y1, mWidth, 1);
            if (!oneDimensionScan(pixels, mWidth)) {
                bitmap.getPixels(pixels, 0, mWidth, 0, y1, mWidth, 10);
                if (!twoDimensionScan(pixels, mWidth, false, false)) {
                    break;
                }
                y1 += 9;
            }
        }

        limit = mPosY + mHeight * 2 / 3;

        for (y2 = mPosY + mHeight - 1; y2 > limit; --y2) {
            // 确定下线 y2
            bitmap.getPixels(pixels, 0, mWidth, mPosX, y2, mWidth, 1);
            if (!oneDimensionScan(pixels, mWidth)) {
                bitmap.getPixels(pixels, 0, mWidth, 0, y2 - 9, mWidth, 10);
                if (!twoDimensionScan(pixels, mWidth, false, true)) {
                    break;
                }
                y2 -= 9;
            }
        }

        int h = y2 - y1 + 1;
        limit = mPosX + mWidth / 3;

        for (x1 = mPosX; x1 < limit; ++x1) {
            // 确定左线 x1
            bitmap.getPixels(pixels, 0, 1, x1, y1, 1, h);
            if (!oneDimensionScan(pixels, h)) {
                bitmap.getPixels(pixels, 0, 10, x1, y1, 10, h);
                if (!twoDimensionScan(pixels, h, true, false)) {
                    break;
                }
                x1 += 9;
            }
        }

        limit = mPosX + mWidth * 2 / 3;

        for (x2 = mPosX + mWidth - 1; x2 > limit; --x2) {
            // 确定右线 x2
            bitmap.getPixels(pixels, 0, 1, x2, y1, 1, h);
            if (!oneDimensionScan(pixels, h)) {
                bitmap.getPixels(pixels, 0, 10, x2 - 9, y1, 10, h);
                if (!twoDimensionScan(pixels, h, true, true)) {
                    break;
                }
                x2 -= 9;
            }
        }

        mWidth = x2 - x1;
        mHeight = y2 - y1;
        mPosX = x1;
        mPosY = y1;
    }

    private void processing(Bitmap src, Bitmap dst) {
        int unit = mHeight / 20;
        int remain = mHeight - 20 * unit;
        int[] pixels = new int[(remain > unit ? remain : unit) * mWidth];
        for (int j = 0; j < 20; ++j) {
            src.getPixels(pixels, 0, mWidth, mPosX, mPosY + j * unit, mWidth, unit);
            dst.setPixels(pixels, 0, mWidth, 0, j * unit, mWidth, unit);
        }
        if (remain > 0) {
            src.getPixels(pixels, 0, mWidth, mPosX, mPosY + 20 * unit, mWidth, remain);
            dst.setPixels(pixels, 0, mWidth, 0, 20 * unit, mWidth, remain);
        }
    }

    @Override
    public CacheKey getPostprocessorCacheKey() {
        return new SimpleCacheKey(StringUtils.format("%s-post-%d", mImage.getUrl(), mImage.getId()));
    }

    /**
     * @return 全白返回 true
     */
    private boolean oneDimensionScan(int[] pixels, int length) {
        for (int i = 0; i < length; ++i) {
            if (!isWhite(pixels[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * 10 * 20 方格 按 2:3:3:2 划分为四个区域 权值分别为 0 1 2 3
     *
     * @return 加权值 > 60 返回 false
     */
    private boolean twoDimensionScan(int[] pixels, int length, boolean vertical, boolean reverse) {
        if (length < 20) {
            return false;
        }

        int[] value = new int[20];
        int result = 0;
        for (int i = 0; i < length; ++i) {
            if (result > 60) {
                return false;
            }
            result -= value[i % 20];
            value[i % 20] = 0;
            for (int j = 0; j < 10; ++j) {
                int k = vertical ? (i * 10 + j) : (j * length + i);
                value[i % 20] += getValue(isWhite(pixels[k]), reverse, j);
            }
            result += value[i % 20];
        }
        return true;
    }

    /**
     * 根据方向位置计算权值
     */
    private int getValue(boolean white, boolean reverse, int pos) {
        if (white) {
            return 0;
        }
        if (pos < 2) {
            return reverse ? 3 : 0;
        } else if (pos < 5) {
            return reverse ? 2 : 1;
        } else if (pos < 8) {
            return reverse ? 1 : 2;
        }
        return reverse ? 0 : 3;
    }

    /**
     * 固定阈值 根据灰度判断黑白
     */
    private boolean isWhite(int pixel) {
        int red = ((pixel & 0x00FF0000) >> 16);
        int green = ((pixel & 0x0000FF00) >> 8);
        int blue = (pixel & 0x000000FF);
        int gray = red * 30 + green * 59 + blue * 11;
        return gray > 21500;
    }

    public static String  getBase64String(String text)  {
        try {
            byte []hexCode = text.getBytes("UTF-8");
            String ret = Base64.encodeToString(hexCode, 0, hexCode.length, Base64.NO_WRAP);
            return ret;
        }
        catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return  null;
    }

    public static int getNum(String e, String t)
    {
        int a = 10;
        try
        {
            String n = DecryptionUtils.base64Decrypt(e) + DecryptionUtils.base64Decrypt(t);
            int e_int = Integer.parseInt(DecryptionUtils.base64Decrypt(e));
            String src_temp = DecryptionUtils.md5(n);
            String temp = src_temp.substring(src_temp.length() - 1);
            int compare1 =  Integer.parseInt(DecryptionUtils.base64Decrypt("MjY4ODUw"));
            int compare2 = Integer.parseInt(DecryptionUtils.base64Decrypt("NDIxOTI1"));
            int compare3 = Integer.parseInt(DecryptionUtils.base64Decrypt("NDIxOTI2"));
            int n_int = temp.getBytes("UTF-8")[0];

            if (e_int >= compare1 && e_int <= compare2)
            {
                n_int = n_int % 10;
            }
            else if(e_int >= compare3)
            {
                n_int = n_int % 8;
            }

            switch (n_int)
            {
                case 0:
                    a = 2;
                    break;
                case 1:
                    a = 4;
                    break;
                case 2:
                    a = 6;
                    break;
                case 3:
                    a = 8;
                    break;
                case 4:
                    a = 10;
                    break;
                case 5:
                    a = 12;
                    break;
                case 6:
                    a = 14;
                    break;
                case 7:
                    a = 16;
                    break;
                case 8:
                    a = 18;
                    break;
                case 9:
                    a = 20; break;
            }

        }catch (Exception ex)
        {
            ex.printStackTrace();
        }

        return a;
    }

    public void decodeJMTTImage(Bitmap sourceBitmap, CloseableReference<Bitmap> reference){
        String url = mImage.getUrl();
        int scramble_id = 220980;
        int chapterId = 0;
        String findStr = "media/photos";

        if((url.contains(findStr)
                && Integer.parseInt(url.substring(url.indexOf("photos/") + 7, url.lastIndexOf("/"))) > scramble_id)) {
            Bitmap resultBitmap = reference.get();
            String temp = url.substring(url.indexOf(findStr) + findStr.length() + 1);
            String aid = temp.substring(0, temp.indexOf("/"));
            String pageNum = temp.substring(temp.indexOf("/") + 1, temp.indexOf("."));
            int rows = getNum(getBase64String(aid), getBase64String(pageNum));//结果为6;
            int remainder  = mHeight % rows;

            for (int x = 0; x < rows; x++) {
                int chunkHeight = (int)Math.floor(mHeight / rows);
                int py = chunkHeight * (x);
                int y = mHeight - chunkHeight * (x + 1) - remainder;

                if (x == 0) {
                    chunkHeight = chunkHeight + remainder;
                } else {
                    py = py + remainder;
                }
                int[] pixels = new int[(chunkHeight) * mWidth];
                sourceBitmap.getPixels(pixels, 0, mWidth, 0, y, mWidth, chunkHeight);
                resultBitmap.setPixels(pixels, 0, mWidth, 0, py, mWidth, chunkHeight);
//                Log.e("test", String.format(" 源坐标 %d,%d, 裁剪宽 %d, 高 %d, 目标坐标 %d,%d",
//                        0,y, mWidth, chunkHeight, 0, py
//                        ));
            }
            jmttIsDone=true;
        }
    }
}
