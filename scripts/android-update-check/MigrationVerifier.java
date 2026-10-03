package com.shiv.rally.migration;
import android.app.Instrumentation;
import android.os.Bundle;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import java.io.File;
import java.security.MessageDigest;
import org.json.JSONObject;
public class MigrationVerifier extends Instrumentation {
 private Bundle arguments;
 public void onCreate(Bundle args) {super.onCreate(args);arguments=args==null?new Bundle():args;start();}
 private static String digest(byte[] bytes) throws Exception {StringBuilder s=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))s.append(String.format("%02x",b));return s.toString();}
 private static byte[] read(File f) throws Exception {return java.nio.file.Files.readAllBytes(f.toPath());}
 private static void check(boolean b,String message) {if(!b)throw new AssertionError(message);}
 public void onStart(){Bundle result=new Bundle();try {
  Context c=getTargetContext();PackageInfo info=c.getPackageManager().getPackageInfo(c.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);
  check(info.getLongVersionCode()==Long.parseLong(arguments.getString("expectedVersionCode", "13")),"Version code");check(arguments.getString("expectedVersionName", "1.0-beta12").equals(info.versionName),"Version name");
  check(info.signingInfo.getApkContentsSigners().length==1,"One current signer");
  check(digest(info.signingInfo.getApkContentsSigners()[0].toByteArray()).equals("79ffff57b611ec7fbbc690007196df16dfd658432dd452458c56108bae55df73"),"Production certificate");
  boolean predecessor=false;for(Signature s:info.signingInfo.getSigningCertificateHistory())predecessor|=digest(s.toByteArray()).equals("211711b801aa9c4ec006b57254ff4e8a0e623dedee4baa8ab1d1d63f9be1425b");check(predecessor,"Verified legacy history");
  check((info.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)==0,"Release must not be debuggable");
  File marker=new File(c.getFilesDir(),"stabilization-update-marker");check(new String(read(marker)).equals("same-data-after-signing-migration"),"Private file retained");
  JSONObject before=new JSONObject(new String(read(new File(c.getExternalFilesDir(null),"stabilization-before.json"))));check(before.getInt("uid")==c.getApplicationInfo().uid,"UID retained");
  JSONObject prefs=before.getJSONObject("preferences");java.util.Iterator<String> keys=prefs.keys();int count=0;while(keys.hasNext()){String name=keys.next();check(prefs.getString(name).equals(digest(read(new File(c.getApplicationInfo().dataDir,"shared_prefs/"+name)))),"Preference digest differs: "+name);count++;}
  marker.delete();result.putString("stream","PASS: production certificate, lineage, release flags, same UID/private file and "+count+" unchanged preference files.\n");finish(-1,result);
 }catch(Throwable e){result.putString("stream","FAIL: "+e.toString()+"\n");finish(0,result);}}
}
