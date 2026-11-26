package com.grk.spatialaudio;
/** Offline endurance test; no Android or sound hardware. */
public final class LongStreamTest {
    public static void main(String[] args){
        final int rate=48000, frames=512, channels=8, blocks=30*60*rate/frames;
        Dsp51 dsp=new Dsp51(rate);short[] in=new short[frames*channels],out=new short[frames*2];
        for(int i=0;i<in.length;i++)in[i]=(short)(Math.sin(i*.024)*28000);
        Dsp51.Config a=new Dsp51.Config(new double[]{0,0,2,3,1,1},90,20,1.1,-9,true,true,true,.65,0,0);
        Dsp51.Config b=new Dsp51.Config(new double[]{0,0,3,-3,-3,-3},100,8,.65,-6,true,true,true,.3,-3,0);
        int peak=0;long samples=0,nonzero=0;long start=System.nanoTime();
        for(int i=0;i<blocks;i++){
            dsp.process(in,frames,channels,out,(i/200)%2==0?a:b);
            for(short x:out){int m=Math.abs((int)x);if(m>peak)peak=m;if(x!=0)nonzero++;samples++;}
        }
        if(peak>31130||nonzero==0||samples!=2L*frames*blocks)throw new AssertionError("PCM endurance failure");
        double seconds=(System.nanoTime()-start)/1e9;
        System.out.printf(java.util.Locale.US,"PASS: 30-minute simulated 5.1.2 stream, %,d frames, %,d output samples, peak %d/32768, %.2f s host compute (%.1fx real-time). Offline only.%n",(long)blocks*frames,samples,peak,seconds,1800/seconds);
    }
}
