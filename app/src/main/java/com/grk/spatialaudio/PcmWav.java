package com.grk.spatialaudio;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Bounded streaming reader: PCM16 stereo or standard-order 5.1 WAV. */
public final class PcmWav implements Closeable {
    private final DataInputStream in;
    public final int rate,channels;
    private long remaining;
    private byte[] bytes=new byte[0];
    private static int u16(DataInputStream s)throws IOException{return s.readUnsignedByte()|s.readUnsignedByte()<<8;}
    private static long u32(DataInputStream s)throws IOException{return (long)u16(s)|((long)u16(s)<<16);}
    private static String id(DataInputStream s)throws IOException{byte[] b=new byte[4];s.readFully(b);return new String(b,StandardCharsets.US_ASCII);}
    private static void skip(DataInputStream s,long n)throws IOException{while(n>0){long k=s.skip(n);if(k<=0){s.readUnsignedByte();k=1;}n-=k;}}
    public PcmWav(InputStream stream)throws IOException{
        in=new DataInputStream(new BufferedInputStream(stream));
        try{
            if(!id(in).equals("RIFF"))throw new IOException("RIFF WAV required");long budget=u32(in);if(budget<4||!id(in).equals("WAVE"))throw new IOException("WAVE required");budget-=4;
            int ch=0,sr=0;boolean fmt=false;
            while(budget>=8){String name=id(in);long n=u32(in);budget-=8;long padded=n+(n&1);if(padded>budget)throw new IOException("Invalid chunk length");
                if(name.equals("fmt ")){if(n<16||n>65536)throw new IOException("Invalid format chunk");byte[] b=new byte[(int)n];in.readFully(b);DataInputStream d=new DataInputStream(new ByteArrayInputStream(b));int tag=u16(d);ch=u16(d);long hz=u32(d);long byteRate=u32(d);int block=u16(d),bits=u16(d);
                    if((ch!=2&&ch!=6&&ch!=8)||hz<8000||hz>96000||bits!=16||block!=ch*2||byteRate!=hz*block)throw new IOException("Use 16-bit PCM stereo, 5.1 or explicitly mapped 5.1.2 WAV, 8–96 kHz");
                    if(tag==65534){if(n<40||u16(d)<22||u16(d)!=16)throw new IOException("Invalid extensible PCM format");long mask=u32(d);byte[] guid=new byte[16];d.readFully(guid);byte[] pcm={1,0,0,0,0,0,16,0,(byte)128,0,0,(byte)170,0,56,(byte)155,113};if(!java.util.Arrays.equals(guid,pcm)||(ch==2&&mask!=3)||(ch==6&&mask!=63&&mask!=1551)||(ch==8&&mask!=0x503f&&mask!=0x560f))throw new IOException("Unsupported PCM channel layout");}
                    else if(tag!=1||ch==8)throw new IOException("Uncompressed PCM16 WAV required");
                    sr=(int)hz;fmt=true;if((n&1)!=0)skip(in,1);
                }else if(name.equals("data")){if(!fmt||n%(ch*2)!=0)throw new IOException("Missing format or incomplete frames");rate=sr;channels=ch;remaining=n;return;}
                else skip(in,padded);budget-=padded;
            }throw new IOException("No PCM data found");
        }catch(IOException|RuntimeException e){try{in.close();}catch(IOException ignored){}throw e;}
    }
    public int read(short[] dest)throws IOException{int len=(int)Math.min(remaining,(long)(dest.length/channels)*channels*2);if(len==0)return 0;if(bytes.length<len)bytes=new byte[len];in.readFully(bytes,0,len);for(int i=0;i<len/2;i++)dest[i]=(short)((bytes[2*i]&255)|(bytes[2*i+1]<<8));remaining-=len;return len/(channels*2);}
    public void close()throws IOException{in.close();}
}
