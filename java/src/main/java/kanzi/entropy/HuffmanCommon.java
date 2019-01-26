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

package kanzi.entropy;

public final class HuffmanCommon
{
   public static final int MAX_SYMBOL_SIZE = 20;
   public static final int MAX_CHUNK_SIZE = 1<<16;
   private static final int BUFFER_SIZE = (MAX_SYMBOL_SIZE<<8) + 256;

   
   // Return the number of codes generated
   public static int generateCanonicalCodes(short[] sizes, int[] codes, int[] symbols, int count)
   {
      // Sort by increasing size (first key) and increasing value (second key)
      if (count > 1)
      {
         byte[] buf = new byte[BUFFER_SIZE];
         
         for (int i=0; i<count; i++)
            buf[((sizes[symbols[i]]-1)<<8)|symbols[i]] = 1;
         
         int n = 0;
         
         for (int i=0; i<BUFFER_SIZE; i++)
         {
            if (buf[i] > 0)
            {
               symbols[n++] = i & 0xFF;
               
               if (n == count)
                  break;
            }
         }
      }

      int code = 0;
      int curLen = sizes[symbols[0]];

      for (int i=0; i<count; i++)
      {
         final int r = symbols[i];

         if (sizes[r] > curLen)
         {
            code <<= (sizes[r] - curLen);
            curLen = sizes[r];

            // Max length reached
            if (curLen > MAX_SYMBOL_SIZE)
               return -1; 
         }

         codes[r] = code;
         code++;
      }

      return count;
   }     
}