package rova.android;

import android.graphics.*;
import android.media.ExifInterface;
import android.media.FaceDetector;
import java.io.ByteArrayInputStream;
import java.io.IOException;

final class CameraProcessor {
    static final class Output {
        final Bitmap bitmap; final String message;
        Output(Bitmap bitmap,String message) { this.bitmap=bitmap; this.message=message; }
    }
    static Output process(byte[] jpeg,boolean blur) throws IOException {
        Bitmap image=null, detection=null;
        try {
            BitmapFactory.Options bounds=new BitmapFactory.Options(); bounds.inJustDecodeBounds=true;
            BitmapFactory.decodeByteArray(jpeg,0,jpeg.length,bounds);
            if(bounds.outWidth<=0 || bounds.outHeight<=0) throw new IOException("Invalid JPEG");
            BitmapFactory.Options options=new BitmapFactory.Options(); options.inSampleSize=1;
            while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>1600) options.inSampleSize*=2;
            options.inMutable=true; options.inPreferredConfig=Bitmap.Config.ARGB_8888;
            image=BitmapFactory.decodeByteArray(jpeg,0,jpeg.length,options);
            if(image==null) throw new IOException("Decode failed");
            int orientation=new ExifInterface(new ByteArrayInputStream(jpeg)).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);
            Matrix matrix=new Matrix();
            switch(orientation) {
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: matrix.setScale(-1,1); break;
                case ExifInterface.ORIENTATION_ROTATE_180: matrix.setRotate(180); break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL: matrix.setScale(1,-1); break;
                case ExifInterface.ORIENTATION_TRANSPOSE: matrix.setRotate(90); matrix.postScale(-1,1); break;
                case ExifInterface.ORIENTATION_ROTATE_90: matrix.setRotate(90); break;
                case ExifInterface.ORIENTATION_TRANSVERSE: matrix.setRotate(270); matrix.postScale(-1,1); break;
                case ExifInterface.ORIENTATION_ROTATE_270: matrix.setRotate(270); break;
                default: break;
            }
            if(!matrix.isIdentity()) {
                Bitmap upright=Bitmap.createBitmap(image,0,0,image.getWidth(),image.getHeight(),matrix,true);
                if(upright!=image) { image.recycle(); image=upright; }
            }
            if (!image.isMutable()) {
                Bitmap mutable = image.copy(Bitmap.Config.ARGB_8888, true);
                if (mutable == null) throw new IOException("Cannot prepare photo for processing");
                image.recycle();
                image = mutable;
            }
            if (!image.isMutable()) { Bitmap mutable=image.copy(Bitmap.Config.ARGB_8888,true); if(mutable==null) throw new IOException("Cannot allocate output"); image.recycle(); image=mutable; }
            String message="Blur was OFF at capture. Faces remain visible. Review before saving.";
            if(blur) {
                int width=image.getWidth() & ~1;
                if(width<2) throw new IOException("Image too small");
                detection=Bitmap.createBitmap(width,image.getHeight(),Bitmap.Config.RGB_565);
                new Canvas(detection).drawBitmap(image,0,0,null);
                FaceDetector.Face[] faces=new FaceDetector.Face[64];
                int count=new FaceDetector(width,image.getHeight(),faces.length).findFaces(detection,faces);
                if (count == faces.length) throw new IOException("Face detection limit reached");
                if (count == 0) {
                    message = "No faces detected. Review the entire photo for missed faces before saving.";
                } else {
                    for(int i=0;i<count;i++) {
                        if(faces[i]==null) throw new IOException("Invalid face result");
                        PointF middle=new PointF(); faces[i].getMidPoint(middle);
                        float distance=faces[i].eyesDistance();
                        if(!(distance>0) || Float.isNaN(middle.x) || Float.isNaN(middle.y)) throw new IOException("Invalid face bounds");
                        Rect box=new Rect(Math.max(0,(int)(middle.x-distance*2.5f)),Math.max(0,(int)(middle.y-distance*3f)),
                            Math.min(image.getWidth(),(int)Math.ceil(middle.x+distance*2.5f)),Math.min(image.getHeight(),(int)Math.ceil(middle.y+distance*3.5f)));
                        if(box.isEmpty()) throw new IOException("Empty face bounds");
                        soften(image,box);
                    }
                    message="Blur applied to "+count+" detected face(s). Other faces may have been missed. Review the entire photo before saving.";
                }
            }
            Output output=new Output(image,message); image=null; return output;
        } finally { if(detection!=null) detection.recycle(); if(image!=null) image.recycle(); }
    }
    /** Strong low-resolution smoothing; never treats detection as a privacy guarantee. */
    private static void soften(Bitmap image,Rect region) {
        Bitmap tiny=Bitmap.createBitmap(3,3,Bitmap.Config.ARGB_8888);
        try {
            Paint filter=new Paint(Paint.FILTER_BITMAP_FLAG);
            new Canvas(tiny).drawBitmap(image,region,new Rect(0,0,3,3),filter);
            new Canvas(image).drawBitmap(tiny,null,region,filter);
        } finally { tiny.recycle(); }
    }
}
