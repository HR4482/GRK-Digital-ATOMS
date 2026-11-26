package com.grk.spatialaudio;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.net.Uri;
import android.os.*;
import java.io.*;

/** Started foreground service owns PCM playback independently of any Activity. */
public final class DspPlaybackService extends Service {
    public static final String PLAY="com.grk.spatialaudio.PLAY", STOP="com.grk.spatialaudio.STOP";
    private static final String CHANNEL="grk_media";
    private static final int NOTIFICATION=512;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final LocalBinder binder=new LocalBinder();
    private volatile Dsp51.Config config=new Dsp51.Config(new double[6],100,15,1,-6,true,true);
    private volatile boolean cancel;
    private volatile String message="";
    private Thread worker;
    private AudioManager manager;
    private AudioFocusRequest focus;
    private boolean foreground;
    private boolean receiverRegistered;
    private final BroadcastReceiver noisy=new BroadcastReceiver(){public void onReceive(Context c,Intent i){if(AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(i.getAction()))requestStop();}};
    public final class LocalBinder extends Binder { public DspPlaybackService service(){return DspPlaybackService.this;} }
    @Override public IBinder onBind(Intent i){return binder;}
    @Override public void onCreate(){super.onCreate();manager=getSystemService(AudioManager.class);
        NotificationChannel ch=new NotificationChannel(CHANNEL,"GRK playback",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager.class).createNotificationChannel(ch);
        IntentFilter filter=new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(noisy,filter,Context.RECEIVER_EXPORTED);else registerReceiver(noisy,filter);receiverRegistered=true;
    }
    public void updateConfig(Dsp51.Config c){config=c;}
    public boolean isBusy(){return worker!=null;}
    public String status(){return message;}
    private Notification notification(String text){
        int flags=PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent open=PendingIntent.getActivity(this,1,new Intent(this,DspActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP),flags);
        PendingIntent stop=PendingIntent.getService(this,2,new Intent(this,DspPlaybackService.class).setAction(STOP),flags);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("GRK Immersive Audio")
            .setContentText(text).setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true).setCategory(Notification.CATEGORY_TRANSPORT)
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause,"Stop",stop).build()).build();
    }
    @Override public int onStartCommand(Intent i,int flags,int startId){
        if(i==null){stopSelf();return START_NOT_STICKY;}
        if(STOP.equals(i.getAction())){requestStop();if(worker==null)stopSelf();return START_NOT_STICKY;}
        if(!PLAY.equals(i.getAction())||i.getData()==null){if(worker==null)stopSelf();return START_NOT_STICKY;}
        if(worker!=null)return START_NOT_STICKY;
        try{
            Notification n=notification("Starting PCM playback");
            if(Build.VERSION.SDK_INT>=29)startForeground(NOTIFICATION,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);else startForeground(NOTIFICATION,n);
            foreground=true;
            AudioAttributes attrs=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
            focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attrs).setOnAudioFocusChangeListener(change->{if(change<0)requestStop();},main).build();
            if(manager.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){finish("Audio focus unavailable");return START_NOT_STICKY;}
            cancel=false;message="Opening PCM stream…";Uri uri=i.getData();worker=new Thread(()->stream(uri,attrs),"GRK-PCM-DSP");worker.start();
        }catch(RuntimeException e){finish("Cannot start playback: "+e.getMessage());}
        return START_NOT_STICKY;
    }
    public void requestStop(){cancel=true;if(worker!=null)message="Stopping…";}
    private void finish(String text){message=text;worker=null;if(focus!=null){manager.abandonAudioFocusRequest(focus);focus=null;}if(foreground){stopForeground(STOP_FOREGROUND_REMOVE);foreground=false;}stopSelf();}
    private void stream(Uri uri,AudioAttributes attrs){AudioTrack track=null;PowerManager.WakeLock lock=null;String result="Playback finished";
        try{
            lock=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"GRK:PCMPlayback");lock.setReferenceCounted(false);lock.acquire();
            try(InputStream in=getContentResolver().openInputStream(uri)){
                if(in==null)throw new IOException("Cannot open file");
                try(PcmWav wav=new PcmWav(in)){
                    int rate=wav.rate,ch=wav.channels;
                    int minimum=AudioTrack.getMinBufferSize(rate,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT);
                    if(minimum<=0)throw new IOException("Stereo output rate not supported");
                    track=new AudioTrack.Builder().setAudioAttributes(attrs).setAudioFormat(new AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build()).setBufferSizeInBytes(Math.max(minimum,4096)).setTransferMode(AudioTrack.MODE_STREAM).build();
                    if(track.getState()!=AudioTrack.STATE_INITIALIZED)throw new IOException("AudioTrack init failed");
                    Dsp51 dsp=new Dsp51(rate);short[] input=new short[512*ch],output=new short[1024];long written=0;
                    if(!cancel){track.play();message=(ch==8?"5.1.2 PCM":ch==6?"5.1 PCM":"Stereo PCM")+" → stereo • "+rate+" Hz • Background enabled";
                        main.post(()->{if(!cancel&&foreground)getSystemService(NotificationManager.class).notify(NOTIFICATION,notification("Playing immersive DSP"));});}
                    int frames;
                    while(!cancel&&(frames=wav.read(input))>0){dsp.process(input,frames,ch,output,config);int offset=0;while(!cancel&&offset<frames*2){int n=track.write(output,offset,frames*2-offset,AudioTrack.WRITE_NON_BLOCKING);if(n<0)throw new IOException("AudioTrack write: "+n);if(n==0){Thread.sleep(5);continue;}offset+=n;written+=n;}}
                    long deadline=SystemClock.elapsedRealtime()+Math.max(2000,Math.max(minimum,4096)*1000L/(rate*4)+500);
                    while(!cancel&&Integer.toUnsignedLong(track.getPlaybackHeadPosition())<written/2&&SystemClock.elapsedRealtime()<deadline)Thread.sleep(15);
                }
            }
        }catch(Exception e){result="Playback error: "+e.getMessage();}
        finally{if(track!=null){try{track.pause();track.flush();}catch(RuntimeException ignored){}track.release();}if(lock!=null&&lock.isHeld())lock.release();final String text=result;main.post(()->finish(cancel?"Stopped":text));}
    }
    @Override public void onDestroy(){requestStop();if(receiverRegistered){unregisterReceiver(noisy);receiverRegistered=false;}if(focus!=null){manager.abandonAudioFocusRequest(focus);focus=null;}if(foreground){stopForeground(STOP_FOREGROUND_REMOVE);foreground=false;}super.onDestroy();}
}
