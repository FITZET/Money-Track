package com.moneytrack.app;

import android.annotation.SuppressLint;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

// minSdk 26 uses the patched platform implementation; keeping this dependency-free is intentional.
@SuppressLint("ExifInterface")
public final class PdfMaterialMerger {
    private static final int A4_WIDTH = 595;
    private static final int A4_HEIGHT = 842;
    private static final int MARGIN = 24;

    private PdfMaterialMerger() {}

    public static File merge(Context context, String eventTitle,
                             List<ExpenseAttachment> attachments) throws IOException {
        if (attachments.isEmpty()) throw new IOException("没有可合并的附件");
        File directory = new File(context.getCacheDir(), "shared_pdfs");
        if (!directory.exists() && !directory.mkdirs()) throw new IOException("无法创建临时目录");
        String safeTitle = eventTitle.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (safeTitle.length() > 40) safeTitle = safeTitle.substring(0, 40);
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(new Date());
        File output = new File(directory, safeTitle + "_报销材料_" + stamp + ".pdf");
        PdfDocument document = new PdfDocument();
        int pageNumber = 1;
        try {
            for (ExpenseAttachment attachment : attachments) {
                try {
                    if (attachment.mimeType.equals("application/pdf") ||
                            attachment.displayName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                        pageNumber = appendPdf(context.getContentResolver(), Uri.parse(attachment.uri),
                                document, pageNumber);
                    } else {
                        Bitmap bitmap = decodeImage(context.getContentResolver(), Uri.parse(attachment.uri));
                        if (bitmap == null) throw new IOException("无法读取图片");
                        appendBitmap(document, bitmap, pageNumber++);
                        bitmap.recycle();
                    }
                } catch (Exception exception) {
                    throw new IOException("附件“" + attachment.displayName + "”处理失败：" +
                            (exception.getMessage() == null ? "文件不可用" : exception.getMessage()), exception);
                }
            }
            try (FileOutputStream stream = new FileOutputStream(output)) {
                document.writeTo(stream);
            }
            return output;
        } catch (IOException exception) {
            if (output.exists()) output.delete();
            throw exception;
        } finally {
            document.close();
        }
    }

    private static int appendPdf(ContentResolver resolver, Uri uri, PdfDocument output,
                                 int pageNumber) throws IOException {
        ParcelFileDescriptor descriptor = resolver.openFileDescriptor(uri, "r");
        if (descriptor == null) throw new IOException("无法打开 PDF");
        try (PdfRenderer renderer = new PdfRenderer(descriptor)) {
            for (int i = 0; i < renderer.getPageCount(); i++) {
                try (PdfRenderer.Page page = renderer.openPage(i)) {
                    float scale = Math.min(2f, 1800f / Math.max(page.getWidth(), page.getHeight()));
                    int width = Math.max(1, Math.round(page.getWidth() * scale));
                    int height = Math.max(1, Math.round(page.getHeight() * scale));
                    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    bitmap.eraseColor(Color.WHITE);
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
                    appendBitmap(output, bitmap, pageNumber++);
                    bitmap.recycle();
                }
            }
            if (renderer.getPageCount() == 0) throw new IOException("PDF 没有页面");
        }
        return pageNumber;
    }

    private static Bitmap decodeImage(ContentResolver resolver, Uri uri) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream stream = resolver.openInputStream(uri)) {
            BitmapFactory.decodeStream(stream, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("无法识别图片");
        int sample = 1;
        while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample) > 2400) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        Bitmap bitmap;
        try (InputStream stream = resolver.openInputStream(uri)) {
            bitmap = BitmapFactory.decodeStream(stream, null, options);
        }
        if (bitmap == null) throw new IOException("无法读取图片");
        int rotation = 0;
        try (InputStream stream = resolver.openInputStream(uri)) {
            ExifInterface exif = new ExifInterface(stream);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) rotation = 90;
            else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) rotation = 180;
            else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) rotation = 270;
        } catch (RuntimeException ignored) {}
        if (rotation == 0) return bitmap;
        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (rotated != bitmap) bitmap.recycle();
        return rotated;
    }

    private static void appendBitmap(PdfDocument document, Bitmap bitmap, int pageNumber) {
        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageNumber).create();
        PdfDocument.Page page = document.startPage(info);
        Canvas canvas = page.getCanvas();
        canvas.drawColor(Color.WHITE);
        float[] fit = PageFit.calculate(bitmap.getWidth(), bitmap.getHeight(), A4_WIDTH, A4_HEIGHT, MARGIN);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(bitmap, new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight()),
                new RectF(fit[0], fit[1], fit[2], fit[3]), paint);
        document.finishPage(page);
    }
}
