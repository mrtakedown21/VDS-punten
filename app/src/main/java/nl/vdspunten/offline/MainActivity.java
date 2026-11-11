package nl.vdspunten.offline;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class MainActivity extends Activity {
    private static final int TAKE_PHOTO = 201;
    private static final int SAVE_DOCX = 202;
    private static final int SAVE_BACKUP = 203;
    private static final int LOAD_BACKUP = 204;
    private WebView webView;
    private File pendingPhotoFile;
    private String pendingPhotoId;
    private String pendingFolderId;
    private String pendingWordMode;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xff0b0e14);
        getWindow().setNavigationBarColor(0xff0b0e14);
        webView=new WebView(this);
        webView.setBackgroundColor(0xff0b0e14);
        WebSettings settings=webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        webView.addJavascriptInterface(new Bridge(),"VDS");
        webView.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri=request.getUrl();
                if("vds-photo.local".equals(uri.getHost())) {
                    String path=uri.getPath();
                    if(path!=null && path.startsWith("/p/") && path.endsWith(".jpg")) {
                        String id=path.substring(3,path.length()-4);
                        try {File f=photoFile(id);if(f.isFile())return new WebResourceResponse("image/jpeg",null,new FileInputStream(f));}
                        catch(Exception ignored) { }
                    }
                    return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
                }
                return super.shouldInterceptRequest(view,request);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri=request.getUrl();
                return !"file".equals(uri.getScheme()); // allow bundled assets only; no external navigation
            }
        });
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    private File dataFile(){return new File(getFilesDir(),"vds_punten_data.json");}
    private File photosDirectory(){File f=new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES),"vds-punten");if(!f.exists())f.mkdirs();return f;}
    private File photoFile(String id) {
        if(id==null || !id.matches("[a-zA-Z0-9-]{1,70}"))throw new IllegalArgumentException("Ongeldig foto-ID");
        return new File(photosDirectory(),id+".jpg");
    }
    private String readData(){
        try {File f=dataFile();if(!f.exists())return "";try(FileInputStream in=new FileInputStream(f)){return new String(readLimited(in, 8_000_000),StandardCharsets.UTF_8);}}
        catch(Exception e){return "";}
    }
    private synchronized void writeData(String json) throws IOException {
        if(json==null||json.length()>5_000_000)throw new IOException("Gegevensbestand te groot");
        try {new JSONObject(json);}catch(JSONException e){throw new IOException("Geen geldige gegevens",e);}
        File temp=new File(getFilesDir(),"vds_punten_data.tmp");
        try(FileOutputStream out=new FileOutputStream(temp)){out.write(json.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
        File dest=dataFile();
        if(!temp.renameTo(dest)) {
            try(FileInputStream in=new FileInputStream(temp);FileOutputStream out=new FileOutputStream(dest)){copyStream(in,out,8_000_000);}
            temp.delete();
        }
    }
    private static byte[] readLimited(InputStream in,long max) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();copyStream(in,out,max);return out.toByteArray();
    }
    private static void copyStream(InputStream in,OutputStream out,long max) throws IOException {
        byte[] buffer=new byte[8192];int r;long amount=0;
        while((r=in.read(buffer))!=-1){amount+=r;if(amount>max)throw new IOException("Bestand te groot");out.write(buffer,0,r);}
    }
    private void onUi(Runnable action){runOnUiThread(action);}
    private void inform(String text){onUi(()->Toast.makeText(this,text,Toast.LENGTH_LONG).show());}
    private void callJs(String function,String... args){
        StringBuilder s=new StringBuilder("window.").append(function).append("(");
        for(int i=0;i<args.length;i++){if(i>0)s.append(',');s.append(JSONObject.quote(args[i]));}
        s.append(")");onUi(()->webView.evaluateJavascript(s.toString(),null));
    }
    private String timestamp(){return new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm",java.util.Locale.ROOT).format(new java.util.Date());}
    private void chooseSave(String mime,String name,int code){
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType(mime);intent.putExtra(Intent.EXTRA_TITLE,name);
        startActivityForResult(intent,code);
    }
    private void takePhoto(String folderId){
        try {
            Intent camera=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if(camera.resolveActivity(getPackageManager())==null){inform("Geen camera-app gevonden.");return;}
            pendingPhotoId=UUID.randomUUID().toString();
            pendingFolderId=folderId;
            pendingPhotoFile=photoFile(pendingPhotoId);
            Uri uri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",pendingPhotoFile);
            camera.putExtra(MediaStore.EXTRA_OUTPUT,uri);
            camera.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(camera,TAKE_PHOTO);
        }catch(Exception e){inform("Camera kan niet worden geopend: "+e.getMessage());}
    }
    // Normalise orientation and downsize large source photos; no network or cloud upload.
    private void normalisePhoto(File f) throws IOException {
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(f.getAbsolutePath(),bounds);
        if(bounds.outWidth<1||bounds.outHeight<1)throw new IOException("Foto ontbreekt");
        int sample=1;while(Math.max(bounds.outWidth,bounds.outHeight)/sample>2300)sample*=2;
        BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=sample;
        Bitmap image=BitmapFactory.decodeFile(f.getAbsolutePath(),opts);if(image==null)throw new IOException("Foto onleesbaar");
        int rotation=0;
        try{int orientation=new ExifInterface(f.getAbsolutePath()).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);
            if(orientation==ExifInterface.ORIENTATION_ROTATE_90)rotation=90;
            if(orientation==ExifInterface.ORIENTATION_ROTATE_180)rotation=180;
            if(orientation==ExifInterface.ORIENTATION_ROTATE_270)rotation=270;
        }catch(Exception ignored){}
        if(rotation!=0){Matrix m=new Matrix();m.postRotate(rotation);Bitmap turned=Bitmap.createBitmap(image,0,0,image.getWidth(),image.getHeight(),m,true);if(turned!=image){image.recycle();image=turned;}}
        Bitmap finalImage=image;
        int maxDimension=Math.max(image.getWidth(),image.getHeight());
        if(maxDimension>2100){float scale=2100f/maxDimension;finalImage=Bitmap.createScaledBitmap(image,Math.round(image.getWidth()*scale),Math.round(image.getHeight()*scale),true);if(finalImage!=image)image.recycle();}
        try(FileOutputStream out=new FileOutputStream(f)){if(!finalImage.compress(Bitmap.CompressFormat.JPEG,83,out))throw new IOException("Foto opslaan mislukt");}
        finalImage.recycle();
    }
    private void writeBackup(OutputStream output) throws IOException {
        JSONObject json;try{json=new JSONObject(readData().isEmpty()?"{}":readData());}catch(JSONException e){throw new IOException("Back-upgegevens ongeldig",e);}
        try(ZipOutputStream zip=new ZipOutputStream(output)){
            zip.putNextEntry(new ZipEntry("vds_data.json"));zip.write(json.toString().getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            JSONArray photos=json.optJSONArray("photos");if(photos==null)return;
            for(int i=0;i<photos.length();i++){
                String id=photos.optJSONObject(i)==null?"":photos.optJSONObject(i).optString("id");
                try {File f=photoFile(id);if(!f.isFile())continue;
                    zip.putNextEntry(new ZipEntry("photos/"+id+".jpg"));try(FileInputStream in=new FileInputStream(f)){copyStream(in,zip,16_000_000);}zip.closeEntry();
                }catch(IllegalArgumentException ignored){}
            }
        }
    }
    private void loadBackup(InputStream input) throws IOException {
        File tempDir=new File(getCacheDir(),"vds_restore_"+System.currentTimeMillis());if(!tempDir.mkdirs())throw new IOException("Kan geen tijdelijke map maken");
        String metadata=null;long total=0;int imageCount=0;
        try(ZipInputStream zip=new ZipInputStream(input)){
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null){
                String name=entry.getName();if(entry.isDirectory()){zip.closeEntry();continue;}
                if("vds_data.json".equals(name))metadata=new String(readLimited(zip,8_000_000),StandardCharsets.UTF_8);
                else if(name.matches("photos/[a-zA-Z0-9-]{1,70}\\.jpg")){
                    if(++imageCount>6000)throw new IOException("Te veel foto's");
                    File dest=new File(tempDir,name.substring("photos/".length()));
                    try(FileOutputStream out=new FileOutputStream(dest)){copyStream(zip,out,16_000_000);}
                    total+=dest.length();if(total>1_000_000_000L)throw new IOException("Back-up te groot");
                }
                zip.closeEntry();
            }
        }catch(Exception e){deleteDirectory(tempDir);throw e;}
        if(metadata==null){deleteDirectory(tempDir);throw new IOException("Geen VDS-back-up gevonden");}
        JSONObject parsed;try{parsed=new JSONObject(metadata);if(parsed.optJSONArray("photos")==null||parsed.optJSONArray("folders")==null)throw new JSONException("Niet compleet");}
        catch(JSONException e){deleteDirectory(tempDir);throw new IOException("Geen geldige VDS-back-up",e);}
        File[] images=tempDir.listFiles();if(images!=null)for(File f:images){File target=new File(photosDirectory(),f.getName());if(!f.renameTo(target))try(FileInputStream in=new FileInputStream(f);FileOutputStream out=new FileOutputStream(target)){copyStream(in,out,16_000_000);}}
        writeData(metadata);deleteDirectory(tempDir);
    }
    private void deleteDirectory(File dir){File[] files=dir.listFiles();if(files!=null)for(File f:files)f.delete();dir.delete();}

    @Override protected void onActivityResult(int code,int result,Intent intent){
        super.onActivityResult(code,result,intent);
        if(code==TAKE_PHOTO){
            final File file=pendingPhotoFile; final String id=pendingPhotoId; final String folder=pendingFolderId;
            pendingPhotoFile=null;pendingPhotoId=null;pendingFolderId=null;
            if(result!=RESULT_OK||file==null){if(file!=null)file.delete();return;}
            new Thread(()->{try{normalisePhoto(file);callJs("onVDSPhotoSaved",id,folder);}catch(Exception e){file.delete();inform("Foto opslaan mislukt: "+e.getMessage());}}).start();return;
        }
        if(result!=RESULT_OK||intent==null||intent.getData()==null)return;
        Uri location=intent.getData();
        if(code==SAVE_DOCX){final String mode=pendingWordMode;new Thread(()->{
            try(OutputStream out=getContentResolver().openOutputStream(location)){if(out==null)throw new IOException("Kan bestand niet openen");WordReport.write(out,readData(),photosDirectory(),mode);inform("Word-rapport opgeslagen.");}
            catch(Exception e){inform("Word-export mislukt: "+e.getMessage());}
        }).start();}
        if(code==SAVE_BACKUP){new Thread(()->{
            try(OutputStream out=getContentResolver().openOutputStream(location)){if(out==null)throw new IOException("Kan bestand niet openen");writeBackup(out);inform("Back-up opgeslagen.");}
            catch(Exception e){inform("Back-up mislukt: "+e.getMessage());}
        }).start();}
        if(code==LOAD_BACKUP){new Thread(()->{
            try(InputStream in=getContentResolver().openInputStream(location)){if(in==null)throw new IOException("Kan bestand niet openen");loadBackup(in);callJs("onVDSBackupRestored");}
            catch(Exception e){inform("Back-up terugzetten mislukt: "+e.getMessage());}
        }).start();}
    }
    public final class Bridge {
        @JavascriptInterface public String loadData(){return readData();}
        @JavascriptInterface public void saveData(String json){try{writeData(json);}catch(Exception e){inform("Kan gegevens niet opslaan: "+e.getMessage());}}
        @JavascriptInterface public void capturePhoto(String folderId){onUi(()->takePhoto(folderId));}
        @JavascriptInterface public void deletePhoto(String photoId){try{photoFile(photoId).delete();}catch(Exception ignored){}}
        @JavascriptInterface public void exportWord(String mode){onUi(()->{pendingWordMode=("open".equals(mode)||"done".equals(mode))?mode:"all";chooseSave("application/vnd.openxmlformats-officedocument.wordprocessingml.document","VDS_punten_"+pendingWordMode+"_"+timestamp()+".docx",SAVE_DOCX);});}
        @JavascriptInterface public void backupData(){onUi(()->chooseSave("application/zip","VDS_punten_backup_"+timestamp()+".zip",SAVE_BACKUP));}
        @JavascriptInterface public void restoreBackup(){onUi(()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/zip");startActivityForResult(i,LOAD_BACKUP);});}
    }
    @Override public void onDestroy(){if(webView!=null)webView.destroy();super.onDestroy();}
}
