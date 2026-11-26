package com.grk.spatialaudio;

/** Streaming PCM DSP. Channel order FL FR C LFE SL SR. Stereo output only. */
public final class Dsp51 {
    public static final class Config {
        final double[] gain = new double[6];
        final double crossover, delayMs, width, preamp;
        final boolean enabled, headphones, immersive;
        final double heightL,heightR;
        public Config(double[] db, double hz, double ms, double width, double preampDb, boolean enabled, boolean headphones) {
            this(db,hz,ms,width,preampDb,enabled,headphones,false,0,0,0);
        }
        public Config(double[] db,double hz,double ms,double width,double preampDb,boolean enabled,boolean headphones,boolean immersive,double amount,double leftDb,double rightDb){
            this.immersive=immersive;double level=clamp(amount,0,1);
            heightL=level*Math.pow(10,clamp(leftDb,-60,6)/20);heightR=level*Math.pow(10,clamp(rightDb,-60,6)/20);
            if(db.length!=6)throw new IllegalArgumentException("Six channel gains required");
            for(int i=0;i<6;i++)gain[i]=Math.pow(10,clamp(db[i],-60,6)/20);
            crossover=clamp(hz,40,200);delayMs=clamp(ms,0,40);this.width=clamp(width,0,1.5);
            preamp=Math.pow(10,clamp(preampDb,-18,0)/20);this.enabled=enabled;this.headphones=headphones;
        }
    }
    private final int rate;
    private final HeightRenderer height;
    private final double[][] low = new double[6][2];
    private final double[] rearL,rearR;
    private int pos;
    private double limiter=1;
    private Config last;
    private double alpha,release;
    private int delay;
    private final double[] channels=new double[6];
    public Dsp51(int sampleRate){if(sampleRate<8000||sampleRate>96000)throw new IllegalArgumentException("Sample rate");rate=sampleRate;height=new HeightRenderer(rate);rearL=new double[rate/25+1];rearR=new double[rearL.length];release=1-Math.exp(-1.0/(rate*.12));}
    private static double clamp(double x,double lo,double hi){return Double.isFinite(x)?Math.max(lo,Math.min(hi,x)):lo;}
    private double lp(int ch,double value){low[ch][0]+=alpha*(value-low[ch][0]);low[ch][1]+=alpha*(low[ch][0]-low[ch][1]);return low[ch][1];}
    private static short pcm(double x){return (short)Math.round(clamp(x,-1,32767.0/32768)*32768);}
    public void process(short[] input,int frames,int count,short[] output,Config cfg){
        if((count!=2&&count!=6&&count!=8)||frames<0||frames>input.length/count||frames>output.length/2)throw new IllegalArgumentException("PCM shape");
        if(last!=cfg){alpha=1-Math.exp(-2*Math.PI*cfg.crossover/rate);delay=(int)Math.round(rate*cfg.delayMs/1000);last=cfg;}
        for(int f=0;f<frames;f++){
            int p=f*count;double l=input[p]/32768.0,r=input[p+1]/32768.0;
            if(count==2){channels[0]=l;channels[1]=r;channels[2]=(l+r)*.5;channels[3]=0;channels[4]=(l-r)*.5;channels[5]=-channels[4];}
            else for(int ch=0;ch<6;ch++)channels[ch]=input[p+ch]/32768.0;
            double dryL=l,dryR=r;
            if(count>=6){dryL=(l+.707*channels[2]+.5*channels[3]+.707*channels[4])*.34;dryR=(r+.707*channels[2]+.5*channels[3]+.707*channels[5])*.34;}
            double hL=count==8?input[p+6]/32768.0:(count==2?.45*(l-r):.5*channels[4]);
            double hR=count==8?input[p+7]/32768.0:(count==2?.45*(r-l):.5*channels[5]);
            if(count==8){dryL=dryL*.8+.14*hL;dryR=dryR*.8+.14*hR;}
            double bass=0;
            for(int ch=0;ch<6;ch++){
                double x=channels[ch]*cfg.gain[ch];double lo=lp(ch,x);
                if(ch==3){bass+=lo;channels[ch]=0;}
                else {double send=count==2?(ch<2?.5:0):.2;bass+=lo*send;channels[ch]=x-lo;}
            }
            // Crossover bass from main channels follows the LFE trim; discrete LFE already has its own trim.
            if(count==2)bass*=cfg.gain[3];
            rearL[pos]=channels[4];rearR[pos]=channels[5];int read=(pos-delay+rearL.length)%rearL.length;
            double sl=rearL[read]*cfg.width,sr=rearR[read]*cfg.width;pos=(pos+1)%rearL.length;
            double center=channels[2]*(count==2?.35:.707);
            double mixL=channels[0]+center+bass+(count==2?.5:.707)*sl;
            double mixR=channels[1]+center+bass+(count==2?.5:.707)*sr;
            // Retain discrete height content in ordinary 5.1 downmix when immersive is off.
            if(count==8&&!cfg.immersive){mixL+=.5*hL;mixR+=.5*hR;}
            height.render(hL,hR,cfg.immersive&&cfg.enabled?cfg.heightL:0,cfg.immersive&&cfg.enabled?cfg.heightR:0);
            if(cfg.immersive){mixL+=height.outL;mixR+=height.outR;}
            if(cfg.headphones){double a=mixL,b=mixR;mixL=.9*a+.1*b;mixR=.9*b+.1*a;}
            double outL=cfg.enabled?mixL*cfg.preamp:dryL,outR=cfg.enabled?mixR*cfg.preamp:dryR;
            // Linked limiter, instant peak reduction and gradual release. Stereo bypass stays bit exact.
            if(!cfg.enabled){limiter=1;output[2*f]=pcm(outL);output[2*f+1]=pcm(outR);continue;}
            double peak=Math.max(Math.abs(outL),Math.abs(outR));double target=peak>.95?.95/peak:1;
            limiter=target<limiter?target:Math.min(target,limiter+(1-limiter)*release);
            output[2*f]=pcm(outL*limiter);output[2*f+1]=pcm(outR*limiter);
        }
    }
}
