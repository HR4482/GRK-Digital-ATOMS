package com.grk.spatialaudio;
import java.io.*;
import java.util.Arrays;
public class Dsp51Test {
    static int passed=0; static final int R=48000;
    static void check(String n,boolean ok){if(!ok)throw new AssertionError(n);System.out.println("PASS: "+n);passed++;}
    static Dsp51.Config cfg(double[] db,double hz,double delay,double width,double pre,boolean enabled,boolean hp){return new Dsp51.Config(db,hz,delay,width,pre,enabled,hp);}
    static final double[] ZERO=new double[6];
    static short[] signal(int ch,int which,int hz,int frames){short[] s=new short[frames*ch];for(int i=0;i<frames;i++)s[i*ch+which]=(short)(12000*Math.sin(2*Math.PI*hz*i/R));return s;}
    static short[] run(short[] in,int ch,Dsp51.Config c){short[] o=new short[in.length/ch*2];new Dsp51(R).process(in,in.length/ch,ch,o,c);return o;}
    static double rms(short[] s,int ch){double sum=0;int n=0;for(int i=2000+ch;i<s.length;i+=2){sum+=(double)s[i]*s[i];n++;}return Math.sqrt(sum/n);}
    static void le(ByteArrayOutputStream b,long n,int size){for(int i=0;i<size;i++)b.write((int)(n>>(8*i))&255);}
    static void tag(ByteArrayOutputStream b,String s)throws Exception{b.write(s.getBytes("US-ASCII"));}
    static byte[] wav(int ch,short[] samples)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();tag(b,"RIFF");le(b,36+samples.length*2,4);tag(b,"WAVE");tag(b,"fmt ");le(b,16,4);le(b,1,2);le(b,ch,2);le(b,R,4);le(b,R*ch*2,4);le(b,ch*2,2);le(b,16,2);tag(b,"data");le(b,samples.length*2,4);for(short s:samples)le(b,s,2);return b.toByteArray();}
    public static void main(String[] args)throws Exception{
        Dsp51.Config base=cfg(ZERO,100,0,1,-6,true,false);
        short[] stereo=signal(2,0,1000,10000); stereo[1]=Short.MIN_VALUE;
        check("Stereo bypass is bit-exact",Arrays.equals(stereo,run(stereo,2,cfg(ZERO,100,0,1,0,false,true))));
        short[] center=run(signal(6,2,1000,10000),6,base);check("Center routed equally to left and right",Math.abs(rms(center,0)-rms(center,1))<.01&&rms(center,0)>1000);
        short[] front=run(signal(6,0,1000,10000),6,base);check("Front left dominates left output",rms(front,0)>20*rms(front,1));
        short[] right=run(signal(6,1,1000,10000),6,base);check("Front right dominates right output",rms(right,1)>20*rms(right,0));
        short[] rear=run(signal(6,4,1000,10000),6,base);check("Surround left dominates left output",rms(rear,0)>20*rms(rear,1));
        short[] rearR=run(signal(6,5,1000,10000),6,base);check("Surround right dominates right output",rms(rearR,1)>20*rms(rearR,0));
        double bass50=rms(run(signal(6,3,50,20000),6,base),0),bass1000=rms(run(signal(6,3,1000,20000),6,base),0);
        check("LFE low-pass attenuates 1 kHz versus 50 Hz by > 30 dB",bass50/bass1000>31.6);System.out.printf("LFE measured 50 Hz RMS %.2f; 1 kHz RMS %.2f; ratio %.2f dB%n",bass50,bass1000,20*Math.log10(bass50/bass1000));
        double[] trim=ZERO.clone();trim[2]=-12;double ratio=rms(run(signal(6,2,1000,10000),6,cfg(trim,100,0,1,-6,true,false)),0)/rms(center,0);check("Center -12 dB trim is applied",Math.abs(ratio-Math.pow(10,-.6))<.002);
        double[] bassTrim=ZERO.clone();bassTrim[3]=6;double b2=rms(run(signal(6,3,50,20000),6,cfg(bassTrim,100,0,1,-6,true,false)),0);check("Discrete LFE +6 dB gain",Math.abs(b2/bass50-Math.pow(10,.3))<.002);
        short[] impulse=new short[6*2000];impulse[4]=12000;short[] a=run(impulse,6,cfg(ZERO,100,20,1,-6,true,false));check("20 ms rear delay at 48 kHz",Math.abs(a[960*2])>2000&&Math.abs(a[0])<100);
        short[] loud=new short[6*20000];Arrays.fill(loud,(short)32767);double[] max={6,6,6,6,6,6};short[] limited=run(loud,6,cfg(max,100,0,1.5,0,true,false));int peak=0;for(short s:limited)peak=Math.max(peak,Math.abs((int)s));check("Peak limiter <= 0.95 full scale",peak<=31130&&peak>0);System.out.println("Peak PCM amplitude: "+peak+" / 32768");
        short[] silence=run(new short[6*1000],6,base);check("Silence remains silent",Arrays.equals(silence,new short[2000]));
        short[] input=signal(6,5,1000,10000);short[] all=run(input,6,base);Dsp51 d=new Dsp51(R);short[] chunked=new short[all.length];for(int i=0;i<10000;i+=500){short[] block=new short[1000];d.process(Arrays.copyOfRange(input,i*6,(i+500)*6),500,6,block,base);System.arraycopy(block,0,chunked,i*2,1000);}check("Streaming block continuity",Arrays.equals(all,chunked));
        short[] changed=new short[20000];d.process(input,10000,6,changed,cfg(ZERO,100,0,0,-6,true,false));check("Live surround width zero removes dominant rear contribution",rms(changed,1)<rms(all,1)/20);
        short[] cross=run(signal(6,0,1000,10000),6,cfg(ZERO,100,0,1,-6,true,true));check("Headphone crossfeed reaches opposite side",rms(cross,1)>rms(front,1)*5);
        byte[] bytes=wav(6,input);try(PcmWav reader=new PcmWav(new ByteArrayInputStream(bytes))){short[] read=new short[input.length];check("5.1 WAV parser and PCM samples",reader.channels==6&&reader.rate==R&&reader.read(read)==10000&&Arrays.equals(read,input)&&reader.read(read)==0);}
        try(PcmWav reader=new PcmWav(new ByteArrayInputStream(wav(2,stereo)))){short[] read=new short[stereo.length];check("Stereo WAV parser",reader.channels==2&&reader.read(read)==10000&&Arrays.equals(read,stereo));}
        boolean bad=false;try(PcmWav r=new PcmWav(new ByteArrayInputStream(wav(1,new short[10])))){}catch(IOException e){bad=true;}check("Reject unsupported mono WAV",bad);
        bad=false;try(PcmWav r=new PcmWav(new ByteArrayInputStream(Arrays.copyOf(bytes,bytes.length-1)))){r.read(new short[input.length]);}catch(IOException e){bad=true;}check("Reject truncated PCM stream",bad);
        System.out.println("DSP TOTAL: "+passed+" passed, 0 failed");
    }
}
