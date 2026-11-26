package com.grk.spatialaudio;
import java.io.*;
import java.util.Arrays;
import static com.grk.spatialaudio.Dsp51Test.*;
public class ImmersiveTest {
    static Dsp51.Config immersive(double amount,double l,double r,boolean on,boolean enabled){return new Dsp51.Config(ZERO,90,20,1,-6,enabled,false,on,amount,l,r);}
    static byte[] mapped(int mask,short[] samples)throws Exception{
        ByteArrayOutputStream b=new ByteArrayOutputStream();tag(b,"RIFF");le(b,60+samples.length*2,4);tag(b,"WAVE");tag(b,"fmt ");le(b,40,4);le(b,65534,2);le(b,8,2);le(b,R,4);le(b,R*16,4);le(b,16,2);le(b,16,2);le(b,22,2);le(b,16,2);le(b,mask,4);b.write(new byte[]{1,0,0,0,0,0,16,0,(byte)128,0,0,(byte)170,0,56,(byte)155,113});tag(b,"data");le(b,samples.length*2,4);for(short s:samples)le(b,s,2);return b.toByteArray();
    }
    public static void main(String[] args)throws Exception{
        Dsp51.Config on=immersive(.8,0,0,true,true),off=immersive(0,0,0,false,true);
        short[] stereo=signal(2,0,1500,24000),l=signal(8,6,1500,24000),r=signal(8,7,1500,24000);
        short[] a=run(l,8,on),b=run(r,8,on);
        check("Height left dominates left output",rms(a,0)>2.5*rms(a,1)&&rms(a,1)>100);
        check("Height right dominates right output",rms(b,1)>2.5*rms(b,0)&&rms(b,0)>100);
        check("Height gain zero silences height rendering",rms(run(l,8,immersive(0,0,0,true,true)),0)==0);
        double ratio=rms(run(l,8,immersive(.8,-12,0,true,true)),0)/rms(a,0);
        check("Independent -12 dB left height trim",Math.abs(ratio-Math.pow(10,-.6))<.005);
        check("Height trim does not change opposite source",Arrays.equals(b,run(r,8,immersive(.8,-12,0,true,true))));
        check("Stereo virtual height changes real PCM",!Arrays.equals(run(stereo,2,on),run(stereo,2,off)));
        check("Zero intensity preserves stereo DSP",Arrays.equals(run(stereo,2,immersive(0,0,0,true,true)),run(stereo,2,off)));
        check("Stereo bypass remains exact",Arrays.equals(stereo,run(stereo,2,immersive(1,6,6,true,false))));
        check("Discrete height retained when immersive off",rms(run(l,8,off),0)>100);
        check("Height silence stays silent",Arrays.equals(run(new short[8*2000],8,on),new short[4000]));
        short[] impulse=new short[8*500];impulse[6]=12000;short[] out=run(impulse,8,on);boolean quiet=true;for(int i=0;i<144*2;i++)quiet&=out[i]==0;
        check("Height path has 3 ms near-ear delay",quiet&&out[144*2]!=0);
        short[] chunked=new short[a.length];Dsp51 dsp=new Dsp51(R);for(int f=0;f<24000;f+=400){short[] block=new short[800];dsp.process(Arrays.copyOfRange(l,f*8,(f+400)*8),400,8,block,on);System.arraycopy(block,0,chunked,f*2,800);}check("Height streaming block continuity",Arrays.equals(a,chunked));
        dsp.process(l,24000,8,chunked,immersive(0,0,0,true,true));check("Live height intensity ramps to silence",rms(Arrays.copyOfRange(chunked,20000,chunked.length),0)<1);
        short[] loud=new short[8*10000];Arrays.fill(loud,Short.MAX_VALUE);short[] limited=run(loud,8,new Dsp51.Config(new double[]{6,6,6,6,6,6},90,0,1.5,0,true,false,true,1,6,6));int peak=0;for(short s:limited)peak=Math.max(peak,Math.abs((int)s));check("Eight-channel limiter bounds peak",peak<=31130&&peak>0);
        try(PcmWav w=new PcmWav(new ByteArrayInputStream(mapped(0x503f,l)))){short[] copy=new short[l.length];check("Mapped 5.1.2 PCM reader",w.channels==8&&w.read(copy)==24000&&Arrays.equals(l,copy));}
        boolean bad=false;try(PcmWav w=new PcmWav(new ByteArrayInputStream(mapped(0x63f,l)))){}catch(IOException e){bad=true;}check("Reject 7.1 layout mislabeled as height",bad);
        bad=false;try(PcmWav w=new PcmWav(new ByteArrayInputStream(wav(8,l)))){}catch(IOException e){bad=true;}check("Reject ambiguous eight-channel WAV",bad);
        System.out.printf("Height left near-ear RMS %.2f, far-ear RMS %.2f; -12 dB gain ratio %.4f; limited peak %d%n",rms(a,0),rms(a,1),ratio,peak);
        System.out.println("IMMERSIVE TOTAL: "+passed+" passed, 0 failed");
    }
}
