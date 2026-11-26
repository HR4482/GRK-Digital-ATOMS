package com.grk.spatialaudio;

import android.app.Activity;
import android.content.*;
import android.os.*;
import android.net.Uri;
import android.view.WindowInsets;
import android.widget.*;

/** Separate PCM player: never claims global or discrete 5.1 output. */
public final class DspActivity extends Activity {
    private static final int PICK=43, BG=0xff101722, FG=0xffe9f1fa, ACCENT=0xff75e0cf;
    private LinearLayout root;
    private TextView status, file;
    private Button start,stop,choose;
    private Uri uri;
    private DspPlaybackService service;
    private boolean bound;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Runnable poll=new Runnable(){public void run(){refreshPlayback();ui.postDelayed(this,300);}};
    private final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName name,IBinder binder){service=((DspPlaybackService.LocalBinder)binder).service();refreshPlayback();}
        public void onServiceDisconnected(ComponentName name){service=null;refreshPlayback();}
    };
    private final double[] trims={0,0,0,0,0,0};
    private double hz=100,ms=15,width=1,preamp=-6;
    private boolean enabled=true,headphones=true,immersive=false;
    private double heightAmount=.65,heightLeft=0,heightRight=0;
    private Switch immersiveToggle;
    private volatile Dsp51.Config config;
    private SeekBar[] gains=new SeekBar[6];
    private SeekBar cross,delay,space,headroom;
    private Switch bypass,phones;
    @Override public void onCreate(Bundle saved){super.onCreate(saved);
        android.content.SharedPreferences prefs=getSharedPreferences("dsp",MODE_PRIVATE);
        for(int i=0;i<6;i++)trims[i]=prefs.getInt("trim"+i,0);
        hz=prefs.getInt("hz",100);ms=prefs.getInt("ms",15);width=prefs.getInt("width",100)/100.0;preamp=prefs.getInt("preamp",-6);
        enabled=prefs.getBoolean("enabled",true);headphones=prefs.getBoolean("headphones",true);immersive=prefs.getBoolean("immersive",false);
        heightAmount=prefs.getInt("height",65)/100.0;heightLeft=prefs.getInt("heightLeft",0);heightRight=prefs.getInt("heightRight",0);
        if(saved!=null)uri=saved.getParcelable("uri");
        ScrollView sc=new ScrollView(this);sc.setBackgroundColor(BG);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(28,28,28,28);sc.addView(root);setContentView(sc);
        if(android.os.Build.VERSION.SDK_INT>=35)root.setOnApplyWindowInsetsListener((v,i)->{android.graphics.Insets b=i.getInsets(WindowInsets.Type.systemBars());v.setPadding(28,28+b.top,28,28+b.bottom);return i;});
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        text("GRK / DSP LAB",16).setTextColor(ACCENT);text("GRK Immersive / 5.1.2",30);text("Virtual height + 5.1 mixer → stereo • 16-bit PCM WAV • No root",15);
        status=text("Select stereo, 5.1 or mapped 5.1.2 PCM WAV.",15);status.setTextColor(ACCENT);file=text(uri==null?"No file selected":"WAV selected",14);
        choose=button("Choose WAV",()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("audio/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK);});
        start=button("Play from start",this::play);start.setEnabled(false);stop=button("Stop",this::cancelPlayback);stop.setEnabled(false);
        bypass=toggle("Enable DSP (OFF = bypass)",enabled,on->{enabled=on;update();});
        phones=toggle("Headphone crossfeed",headphones,on->{headphones=on;update();});
        text("GRK Immersive • original spatial mode",20);
        immersiveToggle=toggle("Virtual height audio",immersive,on->{immersive=on;update();});
        slider("Height intensity",0,100,(int)Math.round(heightAmount*100)," %",v->{heightAmount=v/100.0;update();});
        slider("Height left",-60,6,(int)heightLeft," dB",v->{heightLeft=v;update();});
        slider("Height right",-60,6,(int)heightRight," dB",v->{heightRight=v;update();});
        text("Listening presets",20);
        button("Immersive cinema",()->{preset(new int[]{0,0,2,3,1,1},90,20,110,-9,true);immersiveToggle.setChecked(true);});
        button("Cinema",()->preset(new int[]{0,0,2,3,1,1},90,20,110,-9,true));
        button("Crystal clarity",()->preset(new int[]{0,0,3,-3,-3,-3},100,8,65,-6,true));
        button("Deep bass",()->preset(new int[]{0,0,0,5,-2,-2},110,12,80,-9,true));
        button("Balanced stereo",()->preset(new int[]{0,0,-12,0,-12,-12},90,0,0,-3,false));
        text("6 channel trims",20);
        String[] names={"Front left (FL)","Front right (FR)","Center / dialogue (C)","Subwoofer / bass (LFE)","Surround left (SL)","Surround right (SR)"};
        for(int i=0;i<6;i++){final int k=i;gains[i]=slider(names[i],-60,6,(int)trims[i]," dB",v->{trims[k]=v;update();});}
        text("Bass management and spatial controls",20);
        cross=slider("Crossover",40,200,(int)hz," Hz",v->{hz=v;update();});
        delay=slider("Rear delay",0,40,(int)ms," ms",v->{ms=v;update();});
        space=slider("Surround level",0,150,(int)Math.round(width*100)," %",v->{width=v/100.0;update();});
        headroom=slider("Preamp",-18,0,(int)preamp," dB",v->{preamp=v;update();});
        text("Peak limiter is always active when DSP is enabled. Stereo and 5.1 tracks derive virtual height cues; explicitly mapped 5.1.2 PCM uses recorded height channels. Output is always stereo, including Bluetooth. No Dolby decoder, measured HRTF, or system-wide effect. Background playback uses a media notification. Stop from this screen or the notification (if notifications are allowed).",14);
        update();
        if(android.os.Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED&&!prefs.getBoolean("notificationAsked",false)){
            prefs.edit().putBoolean("notificationAsked",true).apply();requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},81);
        }
    }
    private TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(FG);t.setPadding(0,12,0,12);root.addView(t);return t;}
    private Button button(String s,Runnable r){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setOnClickListener(v->r.run());root.addView(b);return b;}
    private interface Bool{void set(boolean b);} private interface Int{void set(int i);}
    private Switch toggle(String s,boolean on,Bool cb){Switch sw=new Switch(this);sw.setTextColor(FG);sw.setText(s);sw.setChecked(on);root.addView(sw);sw.setOnCheckedChangeListener((v,x)->cb.set(x));return sw;}
    private SeekBar slider(String s,int min,int max,int value,String units,Int cb){TextView t=text(s+": "+value+units,15);SeekBar b=new SeekBar(this);b.setMin(min);b.setMax(max);b.setProgress(value);root.addView(b);b.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar x){}public void onStopTrackingTouch(SeekBar x){}public void onProgressChanged(SeekBar x,int p,boolean user){t.setText(s+": "+p+units);cb.set(p);}});return b;}
    private void preset(int[] db,int h,int m,int w,int p,boolean hp){immersiveToggle.setChecked(false);for(int i=0;i<6;i++)gains[i].setProgress(db[i]);cross.setProgress(h);delay.setProgress(m);space.setProgress(w);headroom.setProgress(p);phones.setChecked(hp);bypass.setChecked(true);update();}
    private void update(){
        config=new Dsp51.Config(trims,hz,ms,width,preamp,enabled,headphones,immersive,heightAmount,heightLeft,heightRight);
        if(service!=null)service.updateConfig(config);
        android.content.SharedPreferences.Editor e=getSharedPreferences("dsp",MODE_PRIVATE).edit();
        for(int i=0;i<6;i++)e.putInt("trim"+i,(int)trims[i]);
        e.putInt("hz",(int)hz).putInt("ms",(int)ms).putInt("width",(int)Math.round(width*100)).putInt("preamp",(int)preamp)
         .putBoolean("enabled",enabled).putBoolean("headphones",headphones).putBoolean("immersive",immersive)
         .putInt("height",(int)Math.round(heightAmount*100)).putInt("heightLeft",(int)heightLeft).putInt("heightRight",(int)heightRight).apply();
    }
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req==PICK&&result==RESULT_OK&&data!=null&&data.getData()!=null){uri=data.getData();file.setText("WAV selected");status.setText("Ready to validate format and play.");refreshPlayback();}}
    private void play(){
        if(uri==null||service==null||service.isBusy())return;
        service.updateConfig(config);
        Intent i=new Intent(this,DspPlaybackService.class).setAction(DspPlaybackService.PLAY).setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{startForegroundService(i);start.setEnabled(false);choose.setEnabled(false);status.setText("Starting background DSP…");}
        catch(RuntimeException e){status.setText("Cannot start playback: "+e.getMessage());refreshPlayback();}
    }
    private void cancelPlayback(){if(service!=null)service.requestStop();}
    private void refreshPlayback(){if(start==null)return;boolean busy=service!=null&&service.isBusy();start.setEnabled(service!=null&&!busy&&uri!=null);choose.setEnabled(!busy);stop.setEnabled(busy);if(service!=null){String message=service.status();if(!message.isEmpty())status.setText(message);}}
    @Override protected void onStart(){super.onStart();bound=bindService(new Intent(this,DspPlaybackService.class),connection,BIND_AUTO_CREATE);ui.post(poll);}
    @Override protected void onStop(){ui.removeCallbacks(poll);if(bound){unbindService(connection);bound=false;}service=null;super.onStop();}
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putParcelable("uri",uri);}
}
