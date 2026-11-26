package com.grk.spatialaudio;

import android.app.Activity;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.*;
import android.media.audiofx.*;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.view.*;
import android.widget.*;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int PICK = 21;
    private static final int BG = 0xff101722, FG = 0xffe9f1fa, ACCENT = 0xff75e0cf;
    private MediaPlayer player;
    private Equalizer eq;
    private BassBoost bass;
    private Virtualizer spatial;
    private int session = -1;
    private boolean enabled = true, external = false, ready = false, registered = false;
    private int bassValue = 0, spatialValue = 0, preset = 0;
    private TextView status, track;
    private LinearLayout root, eqPanel;
    private SeekBar bassBar, spatialBar;
    private Button play;
    private final String[] presets = AudioProfile.NAMES;
    private final BroadcastReceiver sessionReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (!external) return;
            int id = i.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1);
            if (id <= 0) return;
            if (AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION.equals(i.getAction())) attach(id);
            else if (AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION.equals(i.getAction()) && session == id) { releaseEffects(); refresh(); }
        }
    };
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(BG);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(20),dp(24),dp(20),dp(28));
        scroll.addView(root); setContentView(scroll);
        if (Build.VERSION.SDK_INT >= 35) root.setOnApplyWindowInsetsListener((v,insets)-> {
            android.graphics.Insets ins = insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(dp(20),dp(24)+ins.top,dp(20),dp(28)+ins.bottom); return insets;
        });
        TextView brand = label("GRK  /  AUDIO LAB",14); brand.setTextColor(ACCENT); brand.setTypeface(null,Typeface.BOLD);
        TextView heading = label("Spatial Audio",32); heading.setTypeface(null,Typeface.BOLD);
        button("Open GRK Immersive 5.1.2 DSP player",v->{external=false;stopPlayer();refresh();startActivity(new Intent(this,DspActivity.class));});
        label("Real playback-session effects • No root",15);
        status = label("Select an audio file to activate processing.",15); status.setTextColor(ACCENT);
        track = label("No track selected",17);
        button("Choose audio file",v->{ external=false; stopPlayer(); Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("audio/*"); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i,PICK); });
        play = button("Play / pause",v->{ if (ready && player!=null) { if(player.isPlaying()) player.pause(); else player.start(); refresh(); }}); play.setEnabled(false);
        button("Stop playback",v->{external=false;stopPlayer(); refresh();});
        Switch master = new Switch(this); master.setText("Enable audio processing"); master.setTextColor(FG); master.setChecked(true); root.addView(master);
        master.setOnCheckedChangeListener((v,on)->{enabled=on; applyEnabled(); refresh();});
        label("Sound profile",19);
        Spinner options = new Spinner(this); options.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,presets)); root.addView(options);
        options.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){} public void onItemSelected(AdapterView<?> p,View v,int pos,long id){preset=pos; applyPreset(); renderBands();}});
        eqPanel = new LinearLayout(this); eqPanel.setOrientation(LinearLayout.VERTICAL); root.addView(eqPanel);
        bassBar = strength("Bass enhancement",v->{bassValue=v; updateStrength();});
        spatialBar = strength("Surround-style virtualizer (if supported)",v->{spatialValue=v; updateStrength();});
        button("Listen for compatible external players (experimental)",v->{stopPlayer(); external=true; refresh();});
        button("Exit external mode",v->{external=false; if(player==null)releaseEffects(); refresh();});
        label("External mode requires a player to announce its audio session. It is not a system-wide effect. No licensed Dolby processing is included. Keep GRK running during use.",13);
        IntentFilter f=new IntentFilter(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION); f.addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        // External player announcements intentionally originate in other apps.
        androidx.core.content.ContextCompat.registerReceiver(this,sessionReceiver,f,androidx.core.content.ContextCompat.RECEIVER_EXPORTED); registered=true;
        refresh();
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView label(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(FG);t.setPadding(0,dp(8),0,dp(8));root.addView(t);return t;}
    private Button button(String s,View.OnClickListener l){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setOnClickListener(l);root.addView(b);return b;}
    private interface Changed {void value(int v);}
    private SeekBar strength(String name,Changed listener){label(name,15);SeekBar s=new SeekBar(this);s.setMax(1000);root.addView(s);s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int p,boolean user){if(user)listener.value(p);}});return s;}
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req!=PICK||result!=RESULT_OK||data==null||data.getData()==null)return;open(data.getData());}
    private String filename(Uri uri){try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())return c.getString(0);}catch(Exception ignored){}return "Selected audio";}
    private void open(Uri uri){stopPlayer();external=false;track.setText(filename(uri));try{
        player=new MediaPlayer();player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());player.setDataSource(this,uri);
        player.setOnPreparedListener(p->{if(player!=p)return;ready=true;attach(p.getAudioSessionId());p.start();refresh();});
        player.setOnCompletionListener(p->{if(player==p)refresh();});
        player.setOnErrorListener((p,w,e)->{if(player==p){stopPlayer();refresh();status.setText("Playback error: "+w+" / "+e);}return true;});
        player.prepareAsync();status.setText("Preparing audio…");
    }catch(Exception e){stopPlayer();refresh();status.setText("Could not open audio: "+e.getClass().getSimpleName());}}
    private void attach(int id){releaseEffects();session=id;
        try{eq=new Equalizer(0,id);}catch(RuntimeException ignored){}
        try{bass=new BassBoost(0,id);}catch(RuntimeException ignored){}
        try{spatial=new Virtualizer(0,id);}catch(RuntimeException ignored){}
        applyPreset();updateStrength();applyEnabled();refresh();
    }
    private void applyEnabled(){try{if(eq!=null)eq.setEnabled(enabled);}catch(RuntimeException ignored){}try{if(bass!=null)bass.setEnabled(enabled&&bassValue>0);}catch(RuntimeException ignored){}try{if(spatial!=null)spatial.setEnabled(enabled&&spatialValue>0);}catch(RuntimeException ignored){}headroom();}
    private void updateStrength(){try{if(bass!=null&&bass.getStrengthSupported())bass.setStrength((short)bassValue);}catch(RuntimeException ignored){}try{if(spatial!=null&&spatial.getStrengthSupported())spatial.setStrength((short)spatialValue);}catch(RuntimeException ignored){}applyEnabled();refreshStatus();}
    private void applyPreset(){if(eq!=null)try{short[] range=eq.getBandLevelRange();for(short b=0;b<eq.getNumberOfBands();b++){
        double hz=eq.getCenterFreq(b)/1000.0;
        eq.setBandLevel(b,(short)AudioProfile.bandGain(preset,hz,range[0],range[1]));}
        }catch(RuntimeException ignored){}headroom();}
    private void headroom(){if(player==null||!ready)return;float gain=1f;if(enabled){int max=0;try{if(eq!=null)for(short b=0;b<eq.getNumberOfBands();b++)max=Math.max(max,eq.getBandLevel(b));}catch(RuntimeException ignored){}gain=AudioProfile.volume(enabled,active(eq)?max:0,active(bass),active(spatial));}try{player.setVolume(gain,gain);}catch(RuntimeException ignored){}}
    private void renderBands(){eqPanel.removeAllViews();if(eq==null){TextView t=new TextView(this);t.setText("Equalizer not attached / unsupported");t.setTextColor(FG);eqPanel.addView(t);return;}try{short[] r=eq.getBandLevelRange();for(short b=0;b<eq.getNumberOfBands();b++){
        final short band=b;final int min=r[0];TextView t=new TextView(this);t.setTextColor(FG);String hz=String.format(Locale.US,"%.0f Hz",eq.getCenterFreq(b)/1000.0);t.setText(hz+"   "+format(eq.getBandLevel(b)));eqPanel.addView(t);
        SeekBar s=new SeekBar(this);s.setMax(r[1]-min);s.setProgress(eq.getBandLevel(b)-min);eqPanel.addView(s);s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar a){}public void onStopTrackingTouch(SeekBar a){}public void onProgressChanged(SeekBar a,int p,boolean u){if(!u||eq==null)return;try{eq.setBandLevel(band,(short)(p+min));t.setText(hz+"   "+format(eq.getBandLevel(band)));headroom();refreshStatus();}catch(RuntimeException e){status.setText("Equalizer adjustment rejected by device.");}}});
    }}catch(RuntimeException e){status.setText("Device equalizer query failed.");}}
    private String format(int mb){return String.format(Locale.US,"%+.1f dB",mb/100.0);}
    private boolean active(AudioEffect e){try{return e!=null&&e.hasControl()&&e.getEnabled();}catch(RuntimeException ex){return false;}}
    private boolean strengthSupported(BassBoost e){try{return e!=null&&e.getStrengthSupported();}catch(RuntimeException ex){return false;}}
    private boolean strengthSupported(Virtualizer e){try{return e!=null&&e.getStrengthSupported();}catch(RuntimeException ex){return false;}}
    private void refreshStatus(){if(status==null)return;if(session<0){status.setText(external?"Waiting for a compatible player session…":"No active audio session");return;}status.setText((external?"External (experimental)":"Local player")+" · session "+session+"\nEQ: "+(active(eq)?"active":"off / unavailable")+"   Bass: "+(active(bass)?"active":"off / unavailable")+"\nSurround: "+(active(spatial)?"active":"off / unavailable"));}
    private void refresh(){refreshStatus();renderBands();bassBar.setEnabled(strengthSupported(bass));spatialBar.setEnabled(strengthSupported(spatial));play.setEnabled(ready&&player!=null);}
    private void releaseEffects(){if(eq!=null){try{eq.release();}catch(RuntimeException ignored){}eq=null;}if(bass!=null){try{bass.release();}catch(RuntimeException ignored){}bass=null;}if(spatial!=null){try{spatial.release();}catch(RuntimeException ignored){}spatial=null;}session=-1;}
    private void stopPlayer(){ready=false;releaseEffects();if(player!=null){try{player.release();}catch(RuntimeException ignored){}player=null;}}
    @Override protected void onStop(){super.onStop();if(ready&&player!=null&&player.isPlaying())player.pause();}
    @Override protected void onDestroy(){if(registered)unregisterReceiver(sessionReceiver);stopPlayer();super.onDestroy();}
}
