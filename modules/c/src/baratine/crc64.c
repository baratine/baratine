/*
 * Copyright (c) 1998-2014 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

#include <jni.h>
#include "baratine.h"

#if 0

/* needs to be in same file because of an issue with jlong passing */

#define POLY64REV 0xd800000000000000LL
#define MASK      0x00ffffffffffffffLL

static jlong CRC_TABLE[256];
static int g_crc64_is_init;

static void
crc64_init()
{
  int i;
  
  if (g_crc64_is_init) {
    return;
  }

  for (i = 0; i < 256; i++) {
    jlong v = i;
    int j;

    for (j = 0; j < 8; j++) {
      int flag = (v & 1) != 0;

      jlong newV = (v >> 1) & ~(1LL << 63);

      if ((v & 0x100000000LL) != 0)
        newV |= 0x100000000LL;

      if ((v & 1) != 0)
        newV ^= POLY64REV;

      v = newV;
    }

    CRC_TABLE[i] = v;
  }

  fprintf(stderr, "T0 %lx\n", CRC_TABLE[0]);
  fprintf(stderr, "T16 %lx\n", CRC_TABLE[16]);

  g_crc64_is_init = 1;
}


/**
 * Calculates the next crc value.
 */
static jlong 
crc64_next(jlong crc, int ch)
{
  return ((crc >> 8) & MASK) ^ CRC_TABLE[((int) crc ^ ch) & 0xff];
}

/**
 * Calculates CRC from a string.
 */
jlong
crc64_generate(jlong crc, char *value, int max_len)
{
  int ch;
  int i;

  if (! g_crc64_is_init) {
    crc64_init();
  }

  for (i = 0; i < max_len && (ch = value[i]); i++) {
    crc = crc64_next(crc, ch);
  }

  return crc;
}

jlong
crc64_generate_bytes(jlong crc, char *buffer, int length)
{
  int ch;
  int i;

  if (! g_crc64_is_init) {
    crc64_init();
  }
  
  for (i = 0; i < length; i++) {
    crc = crc64_next(crc, buffer[i]);
  }

  return crc;
}
#endif
