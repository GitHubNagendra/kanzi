/*
Copyright 2011-2017 Frederic Langlet
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
you may obtain a copy of the License at

                http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package kanzi.function;

import kanzi.ByteFunction;
import kanzi.SliceByteArray;


// Sorted Rank Transform is typically used after a BWT to reduce the variance 
// of the data prior to entropy coding.
public class SRT implements ByteFunction
{
   private static final int HEADER_SIZE = 4*256; //  freqs

   private final int[] freqs;
   private final byte[] symbols;
   private final int[] r2s;
   private final int[] s2r;
   private final int[] buckets;
   private final int[] bucketEnds;
   

   public SRT()
   {  
      this.freqs = new int[256];
      this.symbols = new byte[256];
      this.r2s = new int[256];
      this.s2r = new int[256];
      this.buckets = new int[256];
      this.bucketEnds = new int[256];
   }
   

   @Override
   public boolean forward(SliceByteArray input, SliceByteArray output) 
   {
      if (input.length == 0)
         return true;
      
      if (input.array == output.array)
         return false;
      
      final int count = input.length;
      
      if (output.length - output.index < getMaxEncodedLength(count))
         return false;
     
      final byte[] src = input.array;
      final int srcIdx = input.index;
      final int[] _freqs = this.freqs;
      final int[] _r2s = this.r2s;
      final int[] _s2r = this.s2r;
      
      for (int i=0; i<256; i++) 
         _freqs[i] = 0;

      // find first symbols and count occurrences
      for (int i=0, b=0; i<count; )
      {
         final byte val = src[srcIdx+i];
         final int c = val & 0xFF;
         int j = i + 1;

         while ((j<count) && (src[srcIdx+j]==val))
            j++;

         if (_freqs[c] == 0)
         {
            _r2s[b] = c;
            _s2r[c] = (byte) b;
            b++;
         }

         _freqs[c] += (j-i);
         i = j;
      }

      // init arrays
      final byte[] _symbols = this.symbols;
      final int[] _buckets = this.buckets;
      int nbSymbols = preprocess(_freqs, _symbols);

   	for (int i=0, bucketPos=0; i<nbSymbols; i++)
      {
         final int c = _symbols[i] & 0xFF;
         _buckets[c] = bucketPos;
         bucketPos += _freqs[c];
      }

      final int headerSize = encodeHeader(_freqs, output.array, output.index);
      output.index += headerSize;
      final int dstIdx = output.index;
      final byte[] dst = output.array;
      
      // encoding
      for (int i=0; i<count; )
      {
         final int c = src[srcIdx+i] & 0xFF;
         int r = _s2r[c] & 0xFF;
         int p = _buckets[c];
         dst[dstIdx+p] = (byte) r;
         p++;

         if (r != 0) 
         {
            do
            {
               _r2s[r] = _r2s[r-1];
               _s2r[_r2s[r]] = r;
               r--;
            }
            while (r != 0);

            _r2s[0] = c;
            _s2r[c] = 0;
         }

         int j = i + 1;

         while ((j<count) && (src[srcIdx+j]==c)) 
         {
            dst[dstIdx+p] = 0;
            p++;
            j++;
         }

         _buckets[c] = p;
         i = j;
      }
      
      input.index += count;
      output.index += count;  
      return true;
   }


   @Override
   public boolean inverse(SliceByteArray input, SliceByteArray output) 
   {
      if (input.length == 0)
         return true;
      
      if (input.array == output.array)
         return false;
   
      final int[] _freqs = this.freqs;
      final int headerSize = decodeHeader(input.array, input.index, _freqs);
      input.index += headerSize;
      final int count = input.length - headerSize;            
      final byte[] src = input.array;
      final int srcIdx = input.index;
      final byte[] _symbols = this.symbols;
      
      // init arrays
      int nbSymbols = preprocess(_freqs, _symbols);

      final int[] _buckets= this.buckets;
      final int[] _bucketEnds = this.bucketEnds;
      final int[] _r2s = this.r2s;

      for (int i=0, bucketPos=0; i<nbSymbols; i++)
      {
         final int c = _symbols[i] & 0xFF;
         _r2s[src[srcIdx+bucketPos]&0xFF] = c;
         _buckets[c] = bucketPos + 1;
         bucketPos += _freqs[c];
         _bucketEnds[c] = bucketPos;
      }

      // decoding
      int c = _r2s[0];
      final byte[] dst = output.array;
      final int dstIdx = output.index;

      for (int i=0; i<count; i++) 
      {
         dst[dstIdx+i] = (byte) c;

         if (_buckets[c] < _bucketEnds[c])
         {
            final int r = src[srcIdx+_buckets[c]] & 0xFF;
            _buckets[c]++;

            if (r == 0)
               continue;

            for (int s=0; s<r; s++)
               _r2s[s] = _r2s[s+1];

            _r2s[r] = c;
            c = _r2s[0];
         } 
         else 
         {
            if (nbSymbols == 0)
               continue;

            nbSymbols--;
          
            for (int s=0; s<nbSymbols; s++)
               _r2s[s] = _r2s[s+1];
             
            c = _r2s[0];
         }
      }

      input.index += count;
      output.index += count;
      return true;
   }
   
   
   private static int preprocess(int[] freqs, byte[] symbols)
   {
      int nbSymbols = 0;

      for (int i=0; i<256; i++) 
      {
         if (freqs[i] > 0)
         {
            symbols[nbSymbols] = (byte) i;
            nbSymbols++;
         }
      }

      int h = 4;

      while (h < nbSymbols)
         h = h*3 + 1;

      while (true)
      {
         h /= 3;

         for (int i=h; i<nbSymbols; i++)
         {
            final int t = symbols[i] & 0xFF;
            int b = i - h;

            while ((b>=0) && ((freqs[symbols[b]&0xFF]<freqs[t]) || ((freqs[t]==freqs[symbols[b]&0xFF]) && (t<(symbols[b]&0xFF)))))
            {
               symbols[b+h] = symbols[b];
               b -= h;
            }

            symbols[b+h] = (byte) t;
         }

         if (h == 1)
            break;
      }

      return nbSymbols;
   }
   
   
   private static int encodeHeader(int[] freqs, byte[] dst, int dstIdx)
   {
      for (int i=0; i<256; i++)
      {
         dst[dstIdx++] = (byte) (freqs[i]>>24);
         dst[dstIdx++] = (byte) (freqs[i]>>16);
         dst[dstIdx++] = (byte) (freqs[i]>>8);
         dst[dstIdx++] = (byte) (freqs[i]);
      }

      return HEADER_SIZE;
   }
   
   
   private static int decodeHeader(byte[] src, int srcIdx, int[] freqs)
   {
      for (int i=0; i<1024; i+=4)
      {
         final int f1 = src[srcIdx++] & 0xFF;
         final int f2 = src[srcIdx++] & 0xFF;
         final int f3 = src[srcIdx++] & 0xFF;
         final int f4 = src[srcIdx++] & 0xFF;
         freqs[i>>2] = (f1<<24) | (f2<<16) | (f3<<8) | f4; 
      }
      
      return HEADER_SIZE;
   }
   
   
   @Override
   public int getMaxEncodedLength(int srcLen)
   {
      return srcLen + HEADER_SIZE;
   }
}