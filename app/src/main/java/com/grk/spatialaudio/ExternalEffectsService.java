package com.grk.spatialaudio;
import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.audiofx.*;
import android.os.*;
import androidx.core.content.ContextCompat;
import java.util.HashMap;

/** Explicit opt-in native EQ; session zero is legacy, not a guarantee of global coverage. */
public class ExternalEffectsService extends Service {
 public static final String START_GLOBAL="grk.global", START_SESSIONS="grk.sessions", STOP="grk.stop";
 public static volatile String status="Off";
 private final HashMap<Integer,Equalizer> effects=new HashMap<>();
 private final Handler handler=new Handler(Looper.getMainLooper());
 private boolean globalRequested=false, receiverRegistered=false;
 private int preset=1;
 private String lastStatus="";
 private final BroadcastReceiver receiver=new BroadcastReceiver(){
  public void onReceive(Context c,Intent i){
   int id=i.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION,-1);
   if(id<=0||effects.containsKey(0))return;
   if(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION.equals(i.getAction())){release(id);updateStatus();return;}
   if(!AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION.equals(i.getAction())||effects.containsKey(id)||effects.size()>=16)return;
   attach(id);updateStatus();
  }
 };
 // Relative, cut-only profiles avoid adding gain to a mix whose headroom is unknown.
 private static int bandLevel(int p,double hz){
  if(p==0)return hz<160?0:hz<1000?-300:-200;
  if(p==2)return hz<160?-400:hz<500?-250:hz<4000?0:-150;
  return hz<160?-100:hz<1000?-300:0;
 }
 private boolean attach(int id){
  Equalizer e=null;
  try{
   e=new Equalizer(0,id);
   if(!e.hasControl())return false;
   short[] range=e.getBandLevelRange();
   for(short b=0;b<e.getNumberOfBands();b++){
    int target=bandLevel(preset,e.getCenterFreq(b)/1000.0);
    e.setBandLevel(b,(short)Math.max(range[0],Math.min(range[1],target)));
   }
   if(e.setEnabled(true)==AudioEffect.SUCCESS&&e.hasControl()&&e.getEnabled()){
    effects.put(id,e);e=null;return true;
   }
  }catch(RuntimeException ignored){}finally{if(e!=null)try{e.release();}catch(RuntimeException ignored){}}
  return false;
 }
 private boolean active(Equalizer e){try{return e!=null&&e.hasControl()&&e.getEnabled();}catch(RuntimeException ex){return false;}}
 private void release(int id){
  Equalizer e=effects.remove(id);
  if(e!=null){try{if(e.hasControl())e.setEnabled(false);}catch(RuntimeException ignored){}finally{try{e.release();}catch(RuntimeException ignored){}}}
 }
 private void clear(){for(Integer id:new java.util.ArrayList<>(effects.keySet()))release(id);}
 private Notification notification(String message){
  int flags=PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT;
  PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,ExternalEffectsService.class).setAction(STOP),flags);
  PendingIntent open=PendingIntent.getActivity(this,2,new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),flags);
  return new Notification.Builder(this,"grk_external").setSmallIcon(android.R.drawable.ic_media_play)
   .setContentTitle("GRK phone audio • experimental").setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message))
   .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true)
   .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause,"Stop",stop).build()).build();
 }
 private void updateStatus(){
  if(effects.containsKey(0)&&!active(effects.get(0)))release(0);
  int n=0;for(Equalizer e:effects.values())if(active(e))n++;
  String s=active(effects.get(0))?"Global native EQ attached; audible coverage unverified."
   :(globalRequested?"Global EQ unavailable; compatible-session fallback: ":"Compatible-session mode: ")+n+" active session(s).";
  status=s;
  if(!s.equals(lastStatus)){lastStatus=s;getSystemService(NotificationManager.class).notify(42,notification(s));}
 }
 private final Runnable poll=new Runnable(){public void run(){updateStatus();handler.postDelayed(this,2000);}};
 @Override public void onCreate(){super.onCreate();
  getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("grk_external","GRK phone audio",NotificationManager.IMPORTANCE_LOW));
  Notification n=notification("Checking native audio compatibility…");
  if(Build.VERSION.SDK_INT>=34)startForeground(42,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(42,n);
  IntentFilter f=new IntentFilter(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);f.addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
  ContextCompat.registerReceiver(this,receiver,f,ContextCompat.RECEIVER_EXPORTED);receiverRegistered=true;
 }
 @Override public int onStartCommand(Intent i,int flags,int id){
  if(i==null||STOP.equals(i.getAction())||"stop".equals(i.getAction())){stopSelf();return START_NOT_STICKY;}
  handler.removeCallbacks(poll);clear();
  globalRequested=START_GLOBAL.equals(i.getAction());
  preset=Math.max(0,Math.min(2,i.getIntExtra("preset",1)));
  if(globalRequested)attach(0);
  updateStatus();handler.postDelayed(poll,2000);return START_NOT_STICKY;
 }
 @Override public void onDestroy(){handler.removeCallbacks(poll);if(receiverRegistered)unregisterReceiver(receiver);clear();status="Off";stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
 @Override public IBinder onBind(Intent i){return null;}
}
