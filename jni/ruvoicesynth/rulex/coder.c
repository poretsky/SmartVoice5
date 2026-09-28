/* This file is part of the rulexdb library.
 *
 * Copyright (C) 2006 Igor B. Poretsky <poretsky@mlbox.ru>
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

/*
 * Lexical data coding routines implementation.
 *
 * These routines provide transition between external and internal
 * data representation in the Rulex database.
 *
 * In the database key fields are packed by the arithmetic coding algorithm
 * based on the static statistical model. Value fields are represented
 * by differences relative to the corresponding keys.
 */


#include <stdlib.h>
#include <string.h>
#include "coder.h"

#define turnout(bit) \
{ \
  if (bit) \
    t[l] |= mask; \
  mask >>= 1; \
  if (!mask) \
    { \
      mask = 0x80; \
      t[++l] = 0; \
    } \
}

/* List of valid letters (koi8-r) */
static const char alphabet[] =
  {
    0xC1, 0xC2, 0xD7, /* а, б, в, */
    0xC7, 0xC4, 0xC5, /* г, д, е, */
    0xA3, 0xD6, 0xDA, /* ё, ж, з, */
    0xC9, 0xCA, 0xCB, /* и, й, к, */
    0xCC, 0xCD, 0xCE, /* л, м, н, */
    0xCF, 0xD0, 0xD2, /* о, п, р, */
    0xD3, 0xD4, 0xD5, /* с, т, у, */
    0xC6, 0xC8, 0xC3, /* ф, х, ц, */
    0xDE, 0xDB, 0xDD, /* ч, ш, щ, */
    0xDF, 0xD9, 0xD8, /* ъ, ы, ь, */
    0xDC, 0xC0, 0xD1, /* э, ю, я */
    0
  };

/* Special groups */
static const char group1[] = { 0xD8, 0xDF, 0 }; /* ъ, ь */
static const char group2[] =
  {
    '+', '-', '=',
    0xC1, 0xC5, 0xA3, /* а, е, ё, */
    0xC9, 0xCA, /* и, й, */
    0xCF, 0xD5, /* о, у, */
    0xDF, 0xD9, 0xD8, /* ъ, ы, ь, */
    0xDC, 0xC0, 0xD1, /* э, ю, я */
    0
  };
static const char group3[] = { 0xD8, 0xD9, 0xDF, 0 }; /* ь, ы, ъ */

/* Statistical model for keys packing */
static const SYMBOL letter[] =
  {
    { 0, 185 }, /* а */
    { 185, 219 }, /* б */
    { 219, 320 }, /* в */
    { 320, 354 }, /* г */
    { 354, 404 }, /* д */
    { 404, 580 }, /* е */
    { 580, 582 }, /* ё */
    { 582, 598 }, /* ж */
    { 598, 637 }, /* з */
    { 637, 797 }, /* и */
    { 797, 828 }, /* й */
    { 828, 900 }, /* к */
    { 900, 995 }, /* л */
    { 995, 1068 }, /* м */
    { 1068, 1214 }, /* н */
    { 1214, 1419 }, /* о */
    { 1419, 1488 }, /* п */
    { 1488, 1609 }, /* р */
    { 1609, 1724 }, /* с */
    { 1724, 1838 }, /* т */
    { 1838, 1900 }, /* у */
    { 1900, 1907 }, /* ф */
    { 1907, 1929 }, /* х */
    { 1929, 1939 }, /* ц */
    { 1939, 1965 }, /* ч */
    { 1965, 1991 }, /* ш */
    { 1991, 2005 }, /* щ */
    { 2005, 2006 }, /* ъ */
    { 2006, 2053 }, /* ы */
    { 2053, 2089 }, /* ь */
    { 2089, 2091 }, /* э */
    { 2091, 2114 }, /* ю */
    { 2114, 2162 }, /* я */
    { 2162, 2390 } /* EOS */
  };
static const unsigned short int scale = 2390;

static int validate_pair(char prev, char next)
     /*
      * This routine checks validity of the letter pairs in words,
      * Returns 0 on valid pair or -1 otherwise.
      */
{
  if (next && strchr(group1, next))
    if (strchr(group2, prev))
      return -1;
  return 0;
}


int rulexdb_pack_key(const char *s, char *t)
     /*
      * This routine packs string pointed by s using arithmetic coding
      * and places result to the string pointed by t.
      * Returns packed data length on success or -1 if source string
      * contains invalid characters.
      */
{
  long int range, underflow_bits = 0;
  unsigned short int low = 0, high = 0xffff, mask = 0x80;
  char table[256];
  int i, j, l = 0;

  /* Initialize symbol table */
  for (i = 0; i < 256; i++)
    table[i] = -1;
  for (i = 0; i < strlen(alphabet) + 1; i++)
    table[(unsigned char)alphabet[i]] = (char)i;

  /* Packing data */
  t[0] = 0;
  for (i = 0; i <= strlen(s); i++)
    {
      /* Get the next symbol and check its validity */
      if (i)
	{
	  if (validate_pair(s[i - 1], s[i]))
	    return -1;
	}
      else if (strchr(group3, s[i]))
	return -1;
      j = (int)table[s[i] & 0xff];
      if (j < 0)
	return -1;

      /* Rescale high and low for the new symbol */
      range = (long int)(high - low) + 1;
      high = low + (unsigned short int)(range * letter[j].high / scale - 1);
      low += (unsigned short int)(range * letter[j].low / scale);

      /* Turn out new bits for packed data */
      while (1)
	{
	  if ((high & 0x8000) == (low & 0x8000))
	    {
	      turnout(high & 0x8000);
	      while (underflow_bits > 0)
		{
		  turnout(~high & 0x8000);
		  underflow_bits--;
		}
	    }
	  else if ((low & 0x4000) && !(high & 0x4000))
	    {
	      underflow_bits++;
	      low &= 0x3fff;
	      high |= 0x4000;
	    }
	  else break;
	  low <<= 1;
	  high <<= 1;
	  high |= 1;
	}
    }

  /* Flush packed data */
  turnout(low & 0x4000);
  underflow_bits++;
  while (underflow_bits-- > 0)
    turnout(~low & 0x4000);
  if (mask != 0x80)
    l++;
  return l;
}

void rulexdb_unpack_data(char *s, const char *diffs, int diffs_size)
     /*
      * This routine unpacks data field for corresponding key.
      * It takes the original key string pointer as its first argument
      * and transforms it according to given diffs.
      */
{
  int i, k, l;

  if (!diffs_size) return;

  /*
   * The transition description may consist of two parts
   * and we have to apply it in the reverse order.
   */

  /* At first let's locate the second part */
  l = diffs_size;
  for (i = 0; i < diffs_size; i++)
    if (!(diffs[i] & ACTION_MASK))
      {
	l = i;
	break;
      }

  /* Applying the second part if any */
  k = 0;
  for (i = l; i < diffs_size; i++)
    switch (diffs[i] & ACTION_MASK)
      { /* letters replacing, inserting and removing */
	case REPLACE_CHAR:
	  s[k++] = alphabet[(unsigned char)diffs[i] & ~ACTION_MASK];
	  break;
	case INSERT_CHAR:
	  (void)memmove(&s[k + 1], &s[k], strlen(&s[k]) + 1);
	  s[k++] = alphabet[(unsigned char)diffs[i] & ~ACTION_MASK];
	  break;
	case REMOVE_CHAR:
	  (void)memmove(&s[k], &s[k + ((unsigned char)diffs[i] & ~ACTION_MASK)],
			strlen(&s[k + ((unsigned char)diffs[i] & ~ACTION_MASK)]) + 1);
	  break;
	default:
	  k += (unsigned char)diffs[i];
	  break;
      }

  /* Now applying the first part of diffs if any */
  k = 0;
  for (i = 0; i < l; i++)
    {
      /* Stress marking */
      k += (unsigned char)diffs[i] & ~ACTION_MASK;
      (void)memmove(&s[k + 1], &s[k], strlen(&s[k]) + 1);
      switch (diffs[i] & ACTION_MASK)
	{
	  case MAJOR_STRESS:
	    s[k++] = '+';
	    break;
	  case MINOR_STRESS:
	    s[k++] = '=';
	    break;
	  case SPACE_BAR:
	    s[k++] = '-';
	    break;
	  default:
	    s[k++] = ' ';
	    break;
	}
    }

  /* That's all */
  return;
}
