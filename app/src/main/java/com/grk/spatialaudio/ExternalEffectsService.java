package com.grk.spatialaudio;
import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.audiofx.*;
import android.os.*;
import java.util.HashMap;

public class ExternalEffectsService extends Service {
 private final HashMap<Integer,Equalizer> effects=new HashMap<>();
 private final BroadcastReceiver receiver=new BroadcastReceiver(){
  public void onReceive(Context c,Intent i){
   int id=i.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION,-1);
   if(id<=0)return;
   if(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION.equals(i.getAction())){release(id);return;}
   if(!AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION.equals(i.getAction())||effects.containsKey(id)||effects.size()>=16)return;
   Equalizer e=null;
   try{e=new Equalizer(0,id);short[] range=e.getBandLevelRange();
    for(short b=0;b<e.getNumberOfBands();b++){double hz=e.getCenterFreq(b)/1000.0; e.setBandLevel(b,(short)Math.max(range[0],Math.min(range[1],hz<160?200:hz>3000?100:0)));}
    if(e.setEnabled(true)==AudioEffect.SUCCESS && e.hasControl()){effects.put(id,e);e=null;}
   }catch(RuntimeException ignored){}finally{if(e!=null)e.release();}
  }
 };
 private void release(int id){Equalizer e=effects.remove(id);if(e!=null){try{e.setEnabled(false);}catch(RuntimeException ignored){}finally{e.release();}}}
 @Override public void onCreate(){super.onCreate();
  NotificationManager nm=getSystemService(NotificationManager.class);
  nm.createNotificationChannel(new NotificationChannel("grk_external","GRK external audio",NotificationManager.IMPORTANCE_LOW));
  PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,ExternalEffectsService.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE);
  Notification n=new Notification.Builder(this,"grk_external").setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("GRK compatible audio sessions").setContentText("Experimental EQ; only players announcing sessions").setOngoing(true).addAction(new Notification.Action.Builder(null,"Stop",stop).build()).build();
  if(Build.VERSION.SDK_INT>=34)startForeground(42,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(42,n);
  IntentFilter f=new IntentFilter(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);f.addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
  androidx.core.content.ContextCompat.registerReceiver(this,receiver,f,androidx.core.content.ContextCompat.RECEIVER_EXPORTED);
 }
 @Override public int onStartCommand(Intent i,int flags,int id){if(i!=null&&"stop".equals(i.getAction()))stopSelf();return START_NOT_STICKY;}
 @Override public void onDestroy(){unregisterReceiver(receiver);for(Integer id:new java.util.ArrayList<>(effects.keySet()))release(id);super.onDestroy();}
 @Override public IBinder onBind(Intent i){return null;}
}
