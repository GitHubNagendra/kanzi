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

import kanzi.Global;


// Tangelo PAQ predictor
// Derived from a modified version of Tangelo 2.4 (by Jan Ondrus).
// PAQ8 is written by Matt Mahoney.
// See http://encode.ru/threads/1738-TANGELO-new-compressor-(derived-from-PAQ8-FP8)

public class TPAQPredictor implements Predictor
{
   private static final int MAX_LENGTH = 88;
   private static final int MIXER_SIZE = 4096;
   private static final int BUFFER_SIZE = 64*1024*1024;
   private static final int MASK_MIXER = MIXER_SIZE - 1;
   private static final int MASK_BUFFER = BUFFER_SIZE- 1;
   private static final int MASK1 = 0x80808080;
   private static final int MASK2 = 0xF0F0F0F0;
   private static final int C1 = 0xcc9e2d51;
   private static final int C2 = 0x1b873593;
   private static final int C3 = 0xe6546b64;
   private static final int C4 = 0x85ebca6b;
   private static final int C5 = 0xc2b2ae35;
   private static final int HASH1 = 200002979;
   private static final int HASH2 = 30005491;
   private static final int HASH3 = 50004239;

   ///////////////////////// state table ////////////////////////
   // STATE_TABLE[2*state+0] = next state if bit is 0, 0 <= state < 256
   // STATE_TABLE[2*state+1] = next state if bit is 1
   // States represent a bit history within some context.
   // State 0 is the starting state (no bits seen).
   // States 1-30 represent all possible sequences of 1-4 bits.
   // States 31-252 represent a pair of counts, (n0,n1), the number
   //   of 0 and 1 bits respectively.  If n0+n1 < 16 then there are
   //   two states for each pair, depending on if a 0 or 1 was the last
   //   bit seen.
   // If n0 and n1 are too large, then there is no state to represent this
   // pair, so another state with about the same ratio of n0/n1 is substituted.
   // Also, when a bit is observed and the count of the opposite bit is large,
   // then part of this count is discarded to favor newer data over old.
   private static final byte[] STATE_TABLE =
   {
      (byte)   1, (byte)   2, (byte)   3, (byte) 163, (byte) 143, (byte) 169, (byte)   4, (byte) 163, (byte)   5, (byte) 165,
      (byte)   6, (byte)  89, (byte)   7, (byte) 245, (byte)   8, (byte) 217, (byte)   9, (byte) 245, (byte)  10, (byte) 245,
      (byte)  11, (byte) 233, (byte)  12, (byte) 244, (byte)  13, (byte) 227, (byte)  14, (byte)  74, (byte)  15, (byte) 221,
      (byte)  16, (byte) 221, (byte)  17, (byte) 218, (byte)  18, (byte) 226, (byte)  19, (byte) 243, (byte)  20, (byte) 218,
      (byte)  21, (byte) 238, (byte)  22, (byte) 242, (byte)  23, (byte)  74, (byte)  24, (byte) 238, (byte)  25, (byte) 241,
      (byte)  26, (byte) 240, (byte)  27, (byte) 239, (byte)  28, (byte) 224, (byte)  29, (byte) 225, (byte)  30, (byte) 221,
      (byte)  31, (byte) 232, (byte)  32, (byte)  72, (byte)  33, (byte) 224, (byte)  34, (byte) 228, (byte)  35, (byte) 223,
      (byte)  36, (byte) 225, (byte)  37, (byte) 238, (byte)  38, (byte)  73, (byte)  39, (byte) 167, (byte)  40, (byte)  76,
      (byte)  41, (byte) 237, (byte)  42, (byte) 234, (byte)  43, (byte) 231, (byte)  44, (byte)  72, (byte)  45, (byte)  31,
      (byte)  46, (byte)  63, (byte)  47, (byte) 225, (byte)  48, (byte) 237, (byte)  49, (byte) 236, (byte)  50, (byte) 235,
      (byte)  51, (byte)  53, (byte)  52, (byte) 234, (byte)  47, (byte)  53, (byte)  54, (byte) 234, (byte)  55, (byte) 229,
      (byte)  56, (byte) 219, (byte)  57, (byte) 229, (byte)  58, (byte) 233, (byte)  59, (byte) 232, (byte)  60, (byte) 228,
      (byte)  61, (byte) 226, (byte)  62, (byte)  72, (byte)  63, (byte)  74, (byte)  64, (byte) 222, (byte)  65, (byte)  75,
      (byte)  66, (byte) 220, (byte)  67, (byte) 167, (byte)  68, (byte)  57, (byte)  69, (byte) 218, (byte)   6, (byte)  70,
      (byte)  71, (byte) 168, (byte)  71, (byte)  72, (byte)  71, (byte)  73, (byte)  61, (byte)  74, (byte)  75, (byte) 217,
      (byte)  56, (byte)  76, (byte)  77, (byte) 167, (byte)  78, (byte)  79, (byte)  77, (byte)  79, (byte)  80, (byte) 166,
      (byte)  81, (byte) 162, (byte)  82, (byte) 162, (byte)  83, (byte) 162, (byte)  84, (byte) 162, (byte)  85, (byte) 165,
      (byte)  86, (byte)  89, (byte)  87, (byte)  89, (byte)  88, (byte) 165, (byte)  77, (byte)  89, (byte)  90, (byte) 162,
      (byte)  91, (byte)  93, (byte)  92, (byte)  93, (byte)  80, (byte)  93, (byte)  94, (byte) 161, (byte)  95, (byte) 100,
      (byte)  96, (byte)  93, (byte)  97, (byte)  93, (byte)  98, (byte)  93, (byte)  99, (byte)  93, (byte)  90, (byte)  93,
      (byte) 101, (byte) 161, (byte)  94, (byte) 102, (byte) 103, (byte) 120, (byte) 101, (byte) 104, (byte) 102, (byte) 105,
      (byte) 104, (byte) 106, (byte) 107, (byte) 108, (byte) 104, (byte) 106, (byte) 105, (byte) 109, (byte) 108, (byte) 110,
      (byte) 111, (byte) 160, (byte) 112, (byte) 134, (byte) 113, (byte) 108, (byte) 114, (byte) 108, (byte) 115, (byte) 126,
      (byte) 116, (byte) 117, (byte)  92, (byte) 117, (byte) 118, (byte) 121, (byte)  94, (byte) 119, (byte) 103, (byte) 120,
      (byte) 119, (byte) 107, (byte) 122, (byte) 124, (byte) 123, (byte) 117, (byte)  94, (byte) 117, (byte) 113, (byte) 125,
      (byte) 126, (byte) 127, (byte) 113, (byte) 124, (byte) 128, (byte) 139, (byte) 129, (byte) 130, (byte) 114, (byte) 124,
      (byte) 131, (byte) 133, (byte) 132, (byte) 109, (byte) 112, (byte) 110, (byte) 134, (byte) 135, (byte) 111, (byte) 110,
      (byte) 134, (byte) 136, (byte) 110, (byte) 137, (byte) 134, (byte) 138, (byte) 134, (byte) 127, (byte) 128, (byte) 140,
      (byte) 128, (byte) 141, (byte) 142, (byte) 145, (byte) 143, (byte) 144, (byte) 115, (byte) 124, (byte) 113, (byte) 125,
      (byte) 142, (byte) 146, (byte) 128, (byte) 147, (byte) 148, (byte) 151, (byte) 149, (byte) 125, (byte)  79, (byte) 150,
      (byte) 148, (byte) 127, (byte) 142, (byte) 152, (byte) 148, (byte) 153, (byte) 150, (byte) 154, (byte) 155, (byte) 156,
      (byte) 149, (byte) 139, (byte) 157, (byte) 158, (byte) 149, (byte) 139, (byte) 159, (byte) 156, (byte) 149, (byte) 139,
      (byte) 131, (byte) 130, (byte) 101, (byte) 117, (byte)  98, (byte) 163, (byte) 115, (byte) 164, (byte) 114, (byte) 141,
      (byte)  91, (byte) 163, (byte)  79, (byte) 147, (byte)  58, (byte)   2, (byte)   1, (byte)   2, (byte) 170, (byte) 199,
      (byte) 129, (byte) 171, (byte) 128, (byte) 172, (byte) 110, (byte) 173, (byte) 174, (byte) 177, (byte) 128, (byte) 175,
      (byte) 176, (byte) 171, (byte) 129, (byte) 171, (byte) 174, (byte) 178, (byte) 179, (byte) 180, (byte) 174, (byte) 172,
      (byte) 176, (byte) 181, (byte) 141, (byte) 182, (byte) 157, (byte) 183, (byte) 179, (byte) 184, (byte) 185, (byte) 186,
      (byte) 157, (byte) 178, (byte) 187, (byte) 189, (byte) 188, (byte) 181, (byte) 168, (byte) 181, (byte) 151, (byte) 190,
      (byte) 191, (byte) 193, (byte) 192, (byte) 182, (byte) 188, (byte) 182, (byte) 187, (byte) 194, (byte) 172, (byte) 195,
      (byte) 175, (byte) 196, (byte) 170, (byte) 197, (byte) 152, (byte) 198, (byte) 185, (byte) 169, (byte) 170, (byte) 200,
      (byte) 176, (byte) 201, (byte) 170, (byte) 202, (byte) 203, (byte) 204, (byte) 148, (byte) 180, (byte) 185, (byte) 205,
      (byte) 203, (byte) 206, (byte) 185, (byte) 207, (byte) 192, (byte) 208, (byte) 209, (byte) 210, (byte) 188, (byte) 194,
      (byte) 211, (byte) 212, (byte) 192, (byte) 184, (byte) 213, (byte) 215, (byte) 214, (byte) 193, (byte) 188, (byte) 184,
      (byte) 216, (byte) 208, (byte) 168, (byte) 193, (byte)  84, (byte) 163, (byte)  54, (byte) 219, (byte)  54, (byte) 168,
      (byte) 221, (byte)  94, (byte)  54, (byte) 217, (byte)  55, (byte) 223, (byte)  85, (byte) 224, (byte)  69, (byte) 225,
      (byte)  63, (byte)  76, (byte)  56, (byte) 227, (byte)  86, (byte) 217, (byte)  58, (byte) 229, (byte) 230, (byte) 219,
      (byte) 231, (byte)  79, (byte)  57, (byte)  86, (byte) 229, (byte) 165, (byte)  56, (byte) 217, (byte) 224, (byte) 214,
      (byte)  54, (byte) 225, (byte)  54, (byte) 216, (byte)  66, (byte) 216, (byte)  58, (byte) 234, (byte)  54, (byte)  75,
      (byte)  61, (byte) 214, (byte)  57, (byte) 237, (byte) 222, (byte)  74, (byte)  78, (byte)  74, (byte)  85, (byte) 163,
      (byte)  82, (byte) 217, (byte)   0, (byte)   0, (byte)   0, (byte)   0, (byte)   0, (byte)   0, (byte)   0, (byte)   0,
      (byte)   0, (byte)   0, (byte)   0, (byte)   0, (byte)   0, (byte)   0, (byte)   0, (byte)   0, (byte)   0, (byte)   0,
      (byte)   0, (byte)   0
   };

   // State Map
   private static final int[] STATE_MAP =
   {
       -119,  -120,   169,  -476,  -484,  -386,  -737,  -881,  -874,  -712,
       -848,  -679,  -559,  -794, -1212,  -782, -1205, -1205,  -613,  -753,
      -1169, -1169, -1169,  -743, -1155,  -732,  -720, -1131, -1131, -1131,
      -1131, -1131, -1131, -1131, -1131, -1131,  -540, -1108, -1108, -1108,
      -1108, -1108, -1108, -1108, -1108, -1108, -1108, -2047, -2047, -2047,
      -2047, -2047, -2047,  -782,  -569,  -389,  -640,  -720,  -568,  -432,
       -379,  -640,  -459,  -590, -1003,  -569,  -981,  -981,  -981,  -609,
        416, -1648,  -245,  -416,  -152,  -152,   416, -1017, -1017,  -179,
       -424,  -446,  -461,  -508,  -473,  -492,  -501,  -520,  -528,   -54,
       -395,  -405,  -404,   -94,  -232,  -274,  -288,  -319,  -354,  -379,
       -105,  -141,   -63,  -113,   -18,   -39,   -94,    52,   103,   167,
        222,   130,   -78,  -135,  -253,  -321,  -343,   102,  -165,   157,
       -229,   155,  -108,  -188,   262,   283,    56,   447,     6,   -92,
        242,   172,    38,   304,   141,   285,   285,   320,   319,   462,
        497,   447,   -56,   -46,   374,   485,   510,   479,   -71,   198,
        475,   549,   559,   584,   586,  -196,   712,  -185,   673,  -161,
        237,   -63,    48,   127,   248,   -34,   -18,   416,   -99,   189,
        -50,    39,   337,   263,   660,   153,   569,   832,   220,     1,
        318,   246,   660,   660,   732,   416,   732,     1,  -660,   246,
        660,     1,  -416,   732,   262,   832,   369,   781,   781,   324,
       1104,   398,   626,  -416,   609,  1018,  1018,  1018,  1648,   732,
       1856,     1,  1856,   416,  -569,  1984,  -732,  -164,   416,   153,
       -416,  -569,  -416,     1,  -660,     1,  -660,   153,   152,  -832,
       -832,  -832,  -569,     0,   -95,  -660,     1,   569,   153,   416,
       -416,     1,     1,  -569,     1,  -318,     1,     1,     1,     1,
          1,     1,     1,     1,     1,     1,

        -10,  -436,   401,  -521,  -623,  -689,  -736,  -812,  -812,  -900,
       -865,  -891, -1006,  -965,  -981,  -916,  -946,  -976, -1072, -1014,
      -1058, -1090, -1044, -1030, -1044, -1104, -1009, -1418, -1131, -1131,
      -1269, -1332, -1191, -1169, -1108, -1378, -1367, -1126, -1297, -1085,
      -1355, -1344, -1169, -1269, -1440, -1262, -1332, -2047, -2047, -1984,
      -2047, -2047, -2047,  -225,  -402,  -556,  -502,  -746,  -609,  -647,
       -625,  -718,  -700,  -805,  -748,  -935,  -838, -1053,  -787,  -806,
       -269, -1006,  -278,  -212,   -41,  -399,   137,  -984,  -998,  -219,
       -455,  -524,  -556,  -564,  -577,  -592,  -610,  -690,  -650,  -140,
       -396,  -471,  -450,  -168,  -215,  -301,  -325,  -364,  -315,  -401,
        -96,  -174,  -102,  -146,   -61,    -9,    54,    81,   116,   140,
        192,   115,   -41,   -93,  -183,  -277,  -365,   104,  -134,    37,
        -80,   181,  -111,  -184,   194,   317,    63,   394,   105,   -92,
        299,   166,   -17,   333,   131,   386,   403,   450,   499,   480,
        493,   504,    89,  -119,   333,   558,   568,   501,    -7,  -151,
        203,   557,   595,   603,   650,   104,   960,   204,   933,   239,
        247,   -12,  -105,    94,   222,  -139,    40,   168,  -203,   566,
        -53,   243,   344,   542,    42,   208,    14,   474,   529,    82,
        513,   504,   570,   616,   644,    92,   669,    91,  -179,   677,
        720,   157,   -10,   687,   672,   750,   686,   830,   787,   683,
        723,   780,   783,     9,   842,   816,   885,   901,  1368,   188,
       1356,   178,  1419,   173,   -22,  1256,   240,   167,     1,   -31,
       -165,    70,  -493,   -45,  -354,   -25,  -142,    98,   -17,  -158,
       -355,  -448,  -142,   -67,   -76,  -310,  -324,  -225,   -96,     0,
         46,   -72,     0,  -439,    14,   -55,     1,     1,     1,     1,
          1,     1,     1,     1,     1,     1,

        -32,  -521,   485,  -627,  -724,  -752,  -815,  -886, -1017,  -962,
      -1022,  -984, -1099, -1062, -1090, -1062, -1108, -1085, -1248, -1126,
      -1233, -1104, -1233, -1212, -1285, -1184, -1162, -1309, -1240, -1309,
      -1219, -1390, -1332, -1320, -1262, -1320, -1332, -1320, -1344, -1482,
      -1367, -1355, -1504, -1390, -1482, -1482, -1525, -2047, -2047, -1984,
      -2047, -2047, -1984,  -251,  -507,  -480,  -524,  -596,  -608,  -658,
       -713,  -812,  -700,  -653,  -820,  -820,  -752,  -831,  -957,  -690,
       -402,  -689,  -189,   -28,   -13,  -312,   119,  -930,  -973,  -212,
       -459,  -523,  -513,  -584,  -545,  -593,  -628,  -631,  -688,   -33,
       -437,  -414,  -458,  -167,  -301,  -308,  -407,  -289,  -389,  -332,
        -55,  -233,  -115,  -144,  -100,   -20,   106,    59,   130,   200,
        237,    36,  -114,  -131,  -232,  -296,  -371,   140,  -168,     0,
        -16,   199,  -125,  -182,   238,   310,    29,   423,    41,  -176,
        317,    96,   -14,   377,   123,   446,   458,   510,   496,   463,
        515,   471,   -11,  -182,   268,   527,   569,   553,   -58,  -146,
        168,   580,   602,   604,   651,    87,   990,    95,   977,   185,
        315,    82,   -25,   140,   286,   -57,    85,    14,  -210,   630,
        -88,   290,   328,   422,   -20,   271,   -23,   478,   548,    64,
        480,   540,   591,   601,   583,    26,   696,   117,  -201,   740,
        717,   213,   -22,   566,   599,   716,   709,   764,   740,   707,
        790,   871,   925,     3,   969,   990,   990,  1023,  1333,   154,
       1440,    89,  1368,   125,   -78,  1403,   128,   100,   -88,   -20,
       -250,  -140,  -164,   -14,  -175,    -6,   -13,   -23,  -251,  -195,
       -422,  -419,  -107,   -89,   -24,   -69,  -244,   -51,   -27,  -250,
          0,     1,  -145,    74,    12,    11,     1,     1,     1,     1,
          1,     1,     1,     1,     1,     1,

        -25,  -605,   564,  -746,  -874,  -905,  -949, -1044, -1126, -1049,
      -1099, -1140, -1248, -1122, -1184, -1240, -1198, -1285, -1262, -1332,
      -1418, -1402, -1390, -1285, -1418, -1418, -1418, -1367, -1552, -1440,
      -1367, -1584, -1344, -1616, -1344, -1390, -1418, -1461, -1616, -1770,
      -1648, -1856, -1770, -1584, -1648, -2047, -1685, -2047, -2047, -1856,
      -2047, -2047, -1770,  -400,  -584,  -523,  -580,  -604,  -625,  -587,
       -739,  -626,  -774,  -857,  -737,  -839,  -656,  -888,  -984,  -624,
        -26,  -745,  -211,  -103,   -73,  -328,   142, -1072, -1062,  -231,
       -458,  -494,  -518,  -579,  -550,  -541,  -653,  -621,  -703,   -53,
       -382,  -444,  -417,  -199,  -288,  -367,  -273,  -450,  -268,  -477,
       -101,  -157,  -123,  -156,  -107,    -9,    71,    64,   133,   174,
        240,    25,  -138,  -127,  -233,  -272,  -383,   105,  -144,    85,
       -115,   188,  -112,  -245,   236,   305,    26,   395,    -3,  -164,
        321,    57,   -68,   346,    86,   448,   482,   541,   515,   461,
        503,   454,   -22,  -191,   262,   485,   557,   550,   -53,  -152,
        213,   565,   570,   649,   640,   122,   931,    92,   990,   172,
        317,    54,   -12,   127,   253,     8,   108,   104,  -144,   733,
        -64,   265,   370,   485,   152,   366,   -12,   507,   473,   146,
        462,   579,   549,   659,   724,    94,   679,    72,  -152,   690,
        698,   378,   -11,   592,   652,   764,   730,   851,   909,   837,
        896,   928,  1050,    74,  1095,  1077,  1206,  1059,  1403,   254,
       1552,   181,  1552,   238,   -31,  1526,   199,    47,  -214,    32,
       -219,  -153,  -323,  -198,  -319,  -108,  -107,   -90,  -177,  -210,
       -184,  -455,  -216,   -19,  -107,  -219,   -22,  -232,   -19,  -198,
       -198,  -113,  -398,     0,   -49,   -29,     1,     1,     1,     1,
          1,     1,     1,     1,     1,     1,

        -34,  -648,   644,  -793,  -889,  -981, -1053, -1108, -1108, -1117,
      -1176, -1198, -1205, -1140, -1355, -1332, -1418, -1440, -1402, -1355,
      -1367, -1418, -1402, -1525, -1504, -1402, -1390, -1378, -1525, -1440,
      -1770, -1552, -1378, -1390, -1616, -1648, -1482, -1616, -1390, -1728,
      -1770, -2047, -1685, -1616, -1648, -1685, -1584, -2047, -1856, -1856,
      -2047, -2047, -2047,   -92,  -481,  -583,  -623,  -602,  -691,  -803,
       -815,  -584,  -728,  -743,  -796,  -734,  -884,  -728, -1616,  -747,
       -416,  -510,  -265,     1,   -44,  -409,   141, -1014, -1094,  -201,
       -490,  -533,  -537,  -605,  -536,  -564,  -676,  -620,  -688,   -43,
       -439,  -361,  -455,  -178,  -309,  -315,  -396,  -273,  -367,  -341,
        -92,  -202,  -138,  -105,  -117,    -4,   107,    36,    90,   169,
        222,   -14,   -92,  -125,  -219,  -268,  -344,    70,  -137,   -49,
          4,   171,   -72,  -224,   210,   319,    15,   386,    -2,  -195,
        298,    53,   -31,   339,    95,   383,   499,   557,   491,   457,
        468,   421,   -53,  -168,   267,   485,   573,   508,   -65,  -109,
        115,   568,   576,   619,   685,   179,   878,   131,   851,   175,
        286,    19,   -21,   113,   245,   -54,   101,   210,  -121,   766,
        -47,   282,   441,   483,   129,   303,    16,   557,   460,   114,
        492,   596,   580,   557,   605,   133,   643,   154,  -115,   668,
        683,   332,   -44,   685,   735,   765,   757,   889,   890,   922,
        917,  1012,  1170,   116,  1104,  1192,  1199,  1213,  1368,   254,
       1462,   307,  1616,   359,    50,  1368,   237,    52,  -112,   -47,
       -416,  -255,  -101,    55,  -177,  -166,   -73,  -132,   -56,  -132,
       -237,  -495,  -152,   -43,    69,    46,  -121,  -191,  -102,   170,
       -137,   -45,  -364,   -57,  -212,     7,     1,     1,     1,     1,
          1,     1,     1,     1,     1,     1,

        -30,  -722,   684,  -930, -1006, -1155, -1191, -1212, -1332, -1149,
      -1276, -1297, -1320, -1285, -1344, -1648, -1402, -1482, -1552, -1255,
      -1344, -1504, -1728, -1525, -1418, -1728, -1856, -1584, -1390, -1552,
      -1552, -1984, -1482, -1525, -1856, -2047, -1525, -1770, -1648, -1770,
      -1482, -1482, -1482, -1584, -2047, -2047, -1552, -2047, -2047, -2047,
      -2047, -1984, -2047,     0,  -376,  -502,  -568,  -710,  -761,  -860,
       -838,  -750, -1058,  -897,  -787,  -865,  -646,  -844,  -979, -1000,
       -416,  -564,  -832,  -416,   -64,  -555,   304,  -954, -1081,  -219,
       -448,  -543,  -510,  -550,  -544,  -564,  -650,  -595,  -747,   -61,
       -460,  -404,  -430,  -183,  -287,  -315,  -366,  -311,  -347,  -328,
       -109,  -240,  -151,  -117,  -156,   -32,    64,    19,    78,   116,
        223,     6,  -195,  -125,  -204,  -267,  -346,    63,  -125,   -92,
        -22,   186,  -128,  -169,   182,   290,   -14,   384,   -27,  -134,
        303,     0,    -5,   328,    96,   351,   483,   459,   529,   423,
        447,   390,  -104,  -165,   214,   448,   588,   550,  -127,  -146,
         31,   552,   563,   620,   718,   -50,   832,    14,   851,    93,
        281,    60,    -5,   121,   257,   -16,   103,   138,  -184,   842,
        -21,   319,   386,   411,   107,   258,    66,   475,   542,   178,
        501,   506,   568,   685,   640,    78,   694,   122,   -96,   634,
        826,   165,   220,   794,   736,   960,   746,   823,   833,   939,
       1045,  1004,  1248,    22,  1118,  1077,  1213,  1127,  1552,   241,
       1440,   282,  1483,   315,  -102,  1391,   352,   124,  -188,    19,
          1,  -268,  -782,     0,  -322,   116,    46,  -129,    95,  -102,
       -238,  -459,  -262,  -100,   122,  -152,  -455,  -269,  -238,     0,
       -152,  -416,  -369,  -219,  -175,   -41,     1,     1,     1,     1,
          1,     1,     1,     1,     1,     1,

        -11,  -533,   477,  -632,  -731,  -815,  -808,  -910,  -940,  -995,
      -1094, -1040,  -946, -1044, -1198, -1099, -1104, -1090, -1162, -1122,
      -1145, -1205, -1248, -1269, -1255, -1285, -1140, -1219, -1269, -1285,
      -1269, -1367, -1344, -1390, -1482, -1332, -1378, -1461, -1332, -1461,
      -1525, -1584, -1418, -1504, -1648, -1648, -1648, -1856, -1856, -1616,
      -1984, -1525, -2047,  -330,  -456,  -533,  -524,  -541,  -577,  -631,
       -715,  -670,  -710,  -729,  -743,  -738,  -759,  -775,  -850,  -690,
       -193,  -870,  -102,    21,   -45,  -282,    96, -1000,  -984,  -177,
       -475,  -506,  -514,  -582,  -597,  -602,  -622,  -633,  -695,   -22,
       -422,  -381,  -435,  -107,  -290,  -327,  -360,  -316,  -366,  -374,
        -62,  -212,  -111,  -162,   -83,    -8,   127,    52,   101,   193,
        237,   -16,  -117,  -150,  -246,  -275,  -361,   122,  -134,   -21,
         28,   220,  -132,  -215,   231,   330,    40,   406,   -11,  -196,
        329,    68,   -42,   391,   101,   396,   483,   519,   480,   464,
        516,   484,   -34,  -200,   269,   487,   525,   510,   -79,  -142,
        150,   517,   555,   594,   718,    86,   861,   102,   840,   134,
        291,    74,    10,   166,   245,    16,   117,   -21,  -126,   652,
        -71,   291,   355,   491,    10,   251,   -21,   527,   525,    43,
        532,   531,   573,   631,   640,    31,   629,    87,  -164,   680,
        755,   145,    14,   621,   647,   723,   748,   687,   821,   745,
        794,   785,   859,    23,   887,   969,   996,  1007,  1286,   104,
       1321,   138,  1321,   169,   -24,  1227,   123,   116,    13,    45,
       -198,   -38,  -214,   -22,  -241,    13,  -161,   -54,  -108,  -120,
       -345,  -484,  -119,   -80,   -58,  -189,  -253,  -223,  -106,   -73,
        -57,   -64,  -268,  -208,    -4,    12,     1,     1,     1,     1,
          1,     1,     1,     1,     1,     1,

        -38,  -419,   362,  -548,  -577,  -699,  -725,  -838,  -860,  -869,
       -891,  -970,  -989, -1030, -1014, -1030, -1169, -1067, -1113, -1155,
      -1212, -1176, -1269, -1205, -1320, -1378, -1169, -1285, -1418, -1240,
      -1320, -1332, -1402, -1390, -1285, -1402, -1262, -1240, -1616, -1320,
      -1552, -1440, -1320, -1685, -1482, -1685, -1320, -1616, -1856, -1616,
      -1856, -2047, -1728,  -302,  -466,  -608,  -475,  -502,  -550,  -598,
       -623,  -584,  -716,  -679,  -759,  -767,  -579,  -713,  -686,  -652,
       -294,  -791,  -240,   -55,  -177,  -377,  -108,  -789,  -858,  -226,
       -370,  -423,  -449,  -474,  -481,  -503,  -541,  -551,  -561,   -93,
       -353,  -345,  -358,   -93,  -215,  -246,  -295,  -304,  -304,  -349,
        -48,  -200,   -90,  -150,   -52,   -14,    92,    19,   105,   177,
        217,    28,   -44,   -83,  -155,  -199,  -273,    53,  -133,    -7,
         26,   135,   -90,  -137,   177,   250,    32,   355,    55,   -89,
        254,    67,   -21,   318,   152,   373,   387,   413,   427,   385,
        436,   355,    41,  -121,   261,   406,   470,   452,    40,   -58,
        223,   474,   546,   572,   534,   184,   682,   205,   757,   263,
        276,     6,   -51,    78,   186,   -65,    48,   -46,   -18,   483,
          3,   251,   334,   444,   115,   254,    80,   480,   480,   207,
        476,   511,   570,   603,   561,   170,   583,   145,    -7,   662,
        647,   287,    88,   608,   618,   713,   728,   725,   718,   520,
        599,   621,   664,   135,   703,   701,   771,   807,   903,   324,
        885,   240,   880,   296,   109,   920,   305,   -24,  -314,   -44,
       -202,  -145,  -481,  -379,  -341,  -128,  -187,  -179,  -342,  -201,
       -419,  -405,  -214,  -150,  -119,  -493,  -447,  -133,  -331,  -224,
       -513,  -156,  -247,  -108,  -177,   -95,     1,     1,     1,     1,
          1,     1,     1,     1,     1,     1,
   };


   static int hash(int x, int y)
   {
      final int h = x*HASH1 ^ y*HASH2;
      return (h>>1) ^ (h>>9) ^ (x>>2) ^ (y>>3) ^ HASH3;
   }



   private int pr;                     // next predicted value (0-4095)
   private int c0;                     // bitwise context: last 0-7 bits with a leading 1 (1-255)
   private int c4;                     // last 4 whole bytes, last is in low 8 bits
   private int c8;                     // last 8 to 4 whole bytes, last is in low 8 bits
   private int bpos;                   // number of bits in c0 (0-7)
   private int pos;
   private int shift4;
   private int matchLen;
   private int matchPos;
   private int hash;
   private final int hashMask;
   private final int statesMask;
   private final AdaptiveProbMap apm;
   private final Mixer mixer;
   private final byte[] buffer;
   private final int[] hashes;         // hash table(context, buffer position)
   private final byte[] states;        // hash table(context, prediction)
   private final int[] cp;             // context pointers
   private final int[] ctx;            // contexts


   public TPAQPredictor()
   {
      this(23);
   }
   
   
   public TPAQPredictor(int logHash)
   {
      if ((logHash < 10) || (logHash > 24))
         throw new IllegalArgumentException("The hash table size log must be in [10..24]");
      
     this.pr = 2048;
     this.c0 = 1;
     this.states = new byte[32<<logHash];
     this.statesMask = this.states.length - 1;
     this.hashes = new int[1<<logHash];
     this.hashMask = this.hashes.length - 1;
     this.buffer = new byte[BUFFER_SIZE];
     this.cp = new int[8];
     this.ctx = new int[8];
     this.apm = new AdaptiveProbMap(65536, 7);
     this.mixer = new Mixer(MIXER_SIZE);
   }


   // Update the probability model
   @Override
   public void update(int bit)
   {
     this.mixer.update(bit);
     this.bpos++;
     this.c0 = (this.c0 << 1) | bit;

     if (this.c0 > 255)
     {
        this.buffer[this.pos&MASK_BUFFER] = (byte) this.c0;
        this.pos++;
        this.c8 = (this.c8<<8) | (this.c4>>>24);
        this.c4 = (this.c4<<8) | (this.c0&0xFF);
        this.hash = (((this.hash*43707) << 4) + this.c4) & this.hashMask;
        this.c0 = 1;
        this.bpos = 0;

        // Shift by 16 if binary data else 0
        this.shift4 = ((this.c4&MASK1) == 0) ? 0 : 16;
        final int shift8 = ((this.c8&MASK1) == 0) ? 0 : 16;

        // Select Neural Net
        this.mixer.setContext(this.c4&MASK_MIXER);

        // Add contexts to NN
        this.addContext(0, this.c4 ^ (this.c4&0xFFFF));
        this.addContext(1, hash(C1, this.c4<<24)); // hash with random primes
        this.addContext(2, hash(C2, this.c4<<16));
        this.addContext(3, hash(C3, this.c4<<8));
        this.addContext(4, hash(C4, this.c4&MASK2));
        this.addContext(5, hash(C5, this.c4));
        this.addContext(6, hash(this.c4>>this.shift4, this.c8>>shift8));
        
        // Find match
        this.findMatch();

        // Keep track of new match position
        this.hashes[this.hash] = this.pos;
      }

      // Add inputs to NN
      for (int i=0; i<7; i++)
      {
         this.states[this.cp[i]] = STATE_TABLE[((this.states[this.cp[i]]&0xFF)<<1)|bit];
         this.cp[i] = (this.ctx[i] + this.c0) & this.statesMask;
         this.mixer.addInput(STATE_MAP[(i<<8)|(this.states[this.cp[i]]&0xFF)]);
      }

      if (this.matchLen > 0)
         this.addMatchContext();

      // Get prediction from NN
      int p = this.mixer.get();

      // SSE (Secondary Symbol Estimation)
      p = this.apm.get(bit, p, this.c0 | (this.c4&0xFF00));
      this.pr = p + ((p-2048) >>> 31);
   }


   private void findMatch()
   {
      // Update ongoing sequence match or detect match in the buffer (LZ like)
      if (this.matchLen > 0)
      {
         this.matchLen += ((this.matchLen - MAX_LENGTH) >>> 31);
         this.matchPos++;
      }
      else
      {
         // Retrieve match position
         this.matchPos = this.hashes[this.hash];

         // Detect match
         if ((this.matchPos != 0) && (this.pos - this.matchPos <= MASK_BUFFER))
         {
            int r = this.matchLen + 1;

            while ((r <= MAX_LENGTH) && (this.buffer[(this.pos-r)&MASK_BUFFER] == this.buffer[(this.matchPos-r)&MASK_BUFFER]))
               r++;

            this.matchLen = r - 1;
         }
      }
   }


   private void addMatchContext()
   {
      if (this.c0 == ((this.buffer[this.matchPos&MASK_BUFFER]&0xFF) | 256) >> (8-this.bpos))
      {
         // Add match length to NN inputs. Compute input based on run length
         int p = (this.matchLen<=24) ? this.matchLen : 24+((this.matchLen-24)>>2);

         if (((this.buffer[this.matchPos&MASK_BUFFER] >> (7-this.bpos)) & 1) == 0)
            p = -p;

         this.mixer.addInput(p<<6);
      }
      else
         this.matchLen = 0;
   }


   private void addContext(int ctxId, int cx)
   {
      cx = cx*987654323 + ctxId;
      cx = (cx << 16) | (cx >>> 16);
      this.ctx[ctxId] = cx*123456791 + ctxId;
   }


   // Return the split value representing the probability of 1 in the [0..4095] range.
   @Override
   public int get()
   {
      return this.pr;
   }


   //////////////////////////// Mixer /////////////////////////////

   // Mixer combines models using 4096 neural networks with 8 inputs.
   // It is used as follows:
   // m.update(y) trains the network where the expected output is the last bit.
   // m.addInput(stretch(p)) inputs prediction from one of N models.  The
   //     prediction should be positive to predict a 1 bit, negative for 0,
   //     nominally -2K to 2K.
   // m.setContext(cxt) selects cxt (0..4095) as one of M neural networks to use.
   // m.get() returns the (squashed) output prediction that the next bit is 1.
   //  The normal sequence per prediction is:
   //
   // - m.addInput(x) called N times with input x=(-2047..2047)
   // - m.setContext(cxt) called once with cxt=(0..M-1)
   // - m.get() called once to predict the next bit, returns 0..4095
   // - m.update(y) called once for actual bit y=(0..1).
   static class Mixer
   {
      private final int[] buffer;   // packed buffer: 8 inputs + 8 weights per ctx
      private int ctx;              // context
      private int idx;              // input index
      private int pr;               // squashed prediction
      
      
      Mixer(int size)
      {
         this.buffer = new int[size*16]; // context index << 4
        
         for (int i=0; i<this.buffer.length; i++)
            this.buffer[i] = 2048;
         
         this.pr = 2048;
      }

      // Adjust weights to minimize coding cost of last prediction
      void update(int bit)
      {
         this.idx = 0;
         int err = (bit<<12) - this.pr;

         if (err == 0)
            return;

         err = (err << 4) - err;

         // Train Neural Network: update weights
         this.buffer[this.ctx+8]  += ((this.buffer[this.ctx]  *err + 0) >> 15);
         this.buffer[this.ctx+9]  += ((this.buffer[this.ctx+1]*err + 0) >> 15);
         this.buffer[this.ctx+10] += ((this.buffer[this.ctx+2]*err + 0) >> 15);
         this.buffer[this.ctx+11] += ((this.buffer[this.ctx+3]*err + 0) >> 15);
         this.buffer[this.ctx+12] += ((this.buffer[this.ctx+4]*err + 0) >> 15);
         this.buffer[this.ctx+13] += ((this.buffer[this.ctx+5]*err + 0) >> 15);
         this.buffer[this.ctx+14] += ((this.buffer[this.ctx+6]*err + 0) >> 15);
         this.buffer[this.ctx+15] += ((this.buffer[this.ctx+7]*err + 0) >> 15);
      }

      void setContext(int ctx)
      {
         this.ctx = ctx << 4; 
      }

      public int get()
      {
         while ((this.idx & 7) != 0)
         {
            this.buffer[this.ctx+this.idx] = 64;
            this.idx++;
         }
         
         // Neural Network dot product (sum weights*inputs)
         int p =    (this.buffer[this.ctx]  *this.buffer[this.ctx+8])
                  + (this.buffer[this.ctx+1]*this.buffer[this.ctx+9])
                  + (this.buffer[this.ctx+2]*this.buffer[this.ctx+10])
                  + (this.buffer[this.ctx+3]*this.buffer[this.ctx+11])
                  + (this.buffer[this.ctx+4]*this.buffer[this.ctx+12])
                  + (this.buffer[this.ctx+5]*this.buffer[this.ctx+13])
                  + (this.buffer[this.ctx+6]*this.buffer[this.ctx+14])
                  + (this.buffer[this.ctx+7]*this.buffer[this.ctx+15]);

         this.pr = Global.squash((p+65536)>>17);
         return this.pr;
      }

      public void addInput(int input)
      {
         this.buffer[this.ctx+this.idx] = input;
         this.idx++;
      }
   }

}
