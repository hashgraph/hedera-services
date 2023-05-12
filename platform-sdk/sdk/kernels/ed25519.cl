/* #include "verifysignature.h" */

/*
 * See verifysignature.h for explanation of what this is.
 *
 * This implementation has been extracted from libsodium.
 * https://github.com/jedisct1/libsodium
 *
 * Only the absolute minimum code has been extracted, in
 * order to keep the program small. The C is simple and portable
 * so should run on most processors.
 *
 * The only external functions this relies on are:
 *   strlen()
 *   memcmp()
 *   memset()
 */

/* ---------- libsodium license ----------------------------------------------
 * Copyright (c) 2013-2015
 * Frank Denis <j at pureftpd dot org>
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 * OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 * ---------------------------------------------------------------------------
 * This is the ISC License: https://en.wikipedia.org/wiki/ISC_license
 */

typedef int int32_t ;
typedef long int64_t ;
typedef ulong uint64_t ;
typedef uchar uint8_t ;

// 1<<25
#define POW2_25 0x0000000002000000L
// 1<<24
#define POW2_24 0x0000000001000000L

#define crypto_sign_PUBLICKEYBYTES 32U
#define crypto_sign_BYTES 64U

typedef struct hash_sha512_state {
    uint64_t      state[8];
    uint64_t      count[2];
    unsigned char buf[128];
} hash_sha512_state;

typedef int32_t fe[10];

typedef struct {
    fe X;
    fe Y;
    fe Z;
} ge_p2;

typedef struct {
    fe X;
    fe Y;
    fe Z;
    fe T;
} ge_p3;

typedef struct {
    fe X;
    fe Y;
    fe Z;
    fe T;
} ge_p1p1;

typedef struct {
    fe yplusx;
    fe yminusx;
    fe xy2d;
} ge_precomp;

typedef struct {
    fe YplusX;
    fe YminusX;
    fe Z;
    fe T2d;
} ge_cached;

void hash_sha512_init(hash_sha512_state *state);

void hash_sha512_update(hash_sha512_state *state,
                        const unsigned char *in,
                        ulong inlen);    /* Ajay */

void hash_sha512_final(hash_sha512_state *state, unsigned char *out);

void sha512_transform(uint64_t *state, const unsigned char* block);

void sha512_pad(hash_sha512_state *state);

void sc_reduce(unsigned char *s);

int verify_32(const unsigned char *x,const unsigned char *y);

void be64enc(void *pp, uint64_t x);

uint64_t be64dec(const void *pp);

void be64enc_vect(unsigned char *dst, const uint64_t *src, ulong len);    /* Ajay */

void be64dec_vect(uint64_t *dst, const unsigned char* src, ulong len);    /* Ajay */

int ge_frombytes_negate_vartime(ge_p3 *h, const unsigned char *s);

void ge_double_scalarmult_vartime(ge_p2 *r, const unsigned char *a,
                                  const ge_p3 *A, const unsigned char *b);

void ge_tobytes(unsigned char *s, const ge_p2 *h);

void slide(signed char *r, const unsigned char *a);

void fe_add(fe h, const fe f, const fe g);

void fe_frombytes(fe h, const unsigned char *s);

void fe_tobytes(unsigned char *s, const fe h);

void fe_copy(fe h, const fe f);

void fe_0(fe h);

void fe_1(fe h);

void fe_sq(fe h, const fe f);

void fe_sq2(fe h, const fe f);

void fe_mul(fe h, const fe f, const fe g);

void fe_sub(fe h, const fe f, const fe g);

void fe_pow22523(fe out, const fe z);

void fe_invert(fe out, const fe z);

int fe_isnonzero(const fe f);

int fe_isnegative(const fe f);

void fe_neg(fe h, const fe f);

void ge_p3_to_cached(ge_cached *r, const ge_p3 *p);

void ge_p3_dbl(ge_p1p1 *r, const ge_p3 *p);

void ge_p1p1_to_p3(ge_p3 *r,const ge_p1p1 *p);

void ge_add(ge_p1p1 *r, const ge_p3 *p, const ge_cached *q);

void ge_p2_0(ge_p2 *h);

void ge_p2_dbl(ge_p1p1 *r, const ge_p2 *p);

void ge_sub(ge_p1p1 *r, const ge_p3 *p, const ge_cached *q);

void ge_madd(ge_p1p1 *r, const ge_p3 *p, const ge_precomp *q);

void ge_msub(ge_p1p1 *r, const ge_p3 *p, const ge_precomp *q);

void ge_p1p1_to_p2(ge_p2 *r, const ge_p1p1 *p);

void ge_p3_to_p2(ge_p2 *r, const ge_p3 *p);

/* Ajay */
void mymemcpy_char(unsigned char *dest, const unsigned char *src, int len)
{
   #pragma unroll
   for (int i=0; i<len; i++) {
      dest[i] = src[i] ;
   }

}


void hash_sha512_init(hash_sha512_state *state) {
    state->count[0] = state->count[1] = 0;
    state->state[0] = 0x6a09e667f3bcc908UL;
    state->state[1] = 0xbb67ae8584caa73bUL;
    state->state[2] = 0x3c6ef372fe94f82bUL;
    state->state[3] = 0xa54ff53a5f1d36f1UL;
    state->state[4] = 0x510e527fade682d1UL;
    state->state[5] = 0x9b05688c2b3e6c1fUL;
    state->state[6] = 0x1f83d9abfb41bd6bUL;
    state->state[7] = 0x5be0cd19137e2179UL;
}

void hash_sha512_update(hash_sha512_state *state,
                        const unsigned char *in,
                        ulong inlen) {          /* Ajay */
    uint64_t bitlen[2];
    uint64_t r;
    const unsigned char *src = in;

    r = (state->count[1] >> 3) & 0x7f;

    bitlen[1] = ((uint64_t)inlen) << 3;
    bitlen[0] = ((uint64_t)inlen) >> 61;

    /* LCOV_EXCL_START */
    if ((state->count[1] += bitlen[1]) < bitlen[1]) {
        state->count[0]++;
    }
    /* LCOV_EXCL_STOP */
    state->count[0] += bitlen[0];

    if (inlen < 128 - r) {
        /* memcpy(&state->buf[r], src, inlen); */ /* Ajay */

        mymemcpy_char(&state->buf[r], src, inlen);  /* Ajay */
        return;
    }
    /* memcpy(&state->buf[r], src, 128 - r); */ /* Ajay */
    mymemcpy_char(&state->buf[r], src, 128 - r); /* Ajay */
    sha512_transform(state->state, state->buf);
    src += 128 - r;
    inlen -= 128 - r;

    while (inlen >= 128) {
        sha512_transform(state->state, src);
        src += 128;
        inlen -= 128;
    }
    /* memcpy(state->buf, src, inlen); */ /* Ajay */
    mymemcpy_char(state->buf, src, inlen);  /* Ajay */
}

void hash_sha512_final(hash_sha512_state *state, unsigned char *out) {
    sha512_pad(state);
    be64enc_vect(out, state->state, 64);
    /* memset((void *) state, 0, sizeof *state); */ /* Ajay */
    state->count[0] = state->count[1] = 0;
    state->state[0] = 0;
    state->state[1] = 0;
    state->state[2] = 0;
    state->state[3] = 0;
    state->state[4] = 0;
    state->state[5] = 0;
    state->state[6] = 0;
    state->state[7] = 0;

    #pragma unroll
    for(int i=0; i<128; i++)
      state->buf[i] = 0 ;



}

#define Ch(x, y, z)     ((x & (y ^ z)) ^ z)
#define Maj(x, y, z)    ((x & (y | z)) | (y & z))
#define SHR(x, n)       (x >> n)
#define ROTR(x, n)      ((x >> n) | (x << (64 - n)))
#define S0(x)           (ROTR(x, 28) ^ ROTR(x, 34) ^ ROTR(x, 39))
#define S1(x)           (ROTR(x, 14) ^ ROTR(x, 18) ^ ROTR(x, 41))
#define s0(x)           (ROTR(x, 1) ^ ROTR(x, 8) ^ SHR(x, 7))
#define s1(x)           (ROTR(x, 19) ^ ROTR(x, 61) ^ SHR(x, 6))

#define RND(a, b, c, d, e, f, g, h, k)              \
    t0 = h + S1(e) + Ch(e, f, g) + k;               \
    t1 = S0(a) + Maj(a, b, c);                      \
    d += t0;                                        \
    h  = t0 + t1;

#define RNDr(S, W, i, k)                    \
    RND(S[(80 - i) % 8], S[(81 - i) % 8],   \
        S[(82 - i) % 8], S[(83 - i) % 8],   \
        S[(84 - i) % 8], S[(85 - i) % 8],   \
        S[(86 - i) % 8], S[(87 - i) % 8],   \
        W[i] + k)

void sha512_transform(uint64_t *state, const unsigned char* block) {
    uint64_t W[80];
	union {
		uint64_t S[8];
		ulong8 v;
	}Sv;
	uint64_t *S;
    uint64_t t0, t1;
    int i;

    be64dec_vect(W, block, 128);
    for (i = 16; i < 80; i++) {
        W[i] = s1(W[i - 2]) + W[i - 7] + s0(W[i - 15]) + W[i - 16];
    }

	Sv.v = vload8(0, state);
	S = Sv.S;
    


    RNDr(S, W, 0, 0x428a2f98d728ae22UL);
    RNDr(S, W, 1, 0x7137449123ef65cdUL);
    RNDr(S, W, 2, 0xb5c0fbcfec4d3b2fUL);
    RNDr(S, W, 3, 0xe9b5dba58189dbbcUL);
    RNDr(S, W, 4, 0x3956c25bf348b538UL);
    RNDr(S, W, 5, 0x59f111f1b605d019UL);
    RNDr(S, W, 6, 0x923f82a4af194f9bUL);
    RNDr(S, W, 7, 0xab1c5ed5da6d8118UL);
    RNDr(S, W, 8, 0xd807aa98a3030242UL);
    RNDr(S, W, 9, 0x12835b0145706fbeUL);
    RNDr(S, W, 10, 0x243185be4ee4b28cUL);
    RNDr(S, W, 11, 0x550c7dc3d5ffb4e2UL);
    RNDr(S, W, 12, 0x72be5d74f27b896fUL);
    RNDr(S, W, 13, 0x80deb1fe3b1696b1UL);
    RNDr(S, W, 14, 0x9bdc06a725c71235UL);
    RNDr(S, W, 15, 0xc19bf174cf692694UL);
    RNDr(S, W, 16, 0xe49b69c19ef14ad2UL);
    RNDr(S, W, 17, 0xefbe4786384f25e3UL);
    RNDr(S, W, 18, 0x0fc19dc68b8cd5b5UL);
    RNDr(S, W, 19, 0x240ca1cc77ac9c65UL);
    RNDr(S, W, 20, 0x2de92c6f592b0275UL);
    RNDr(S, W, 21, 0x4a7484aa6ea6e483UL);
    RNDr(S, W, 22, 0x5cb0a9dcbd41fbd4UL);
    RNDr(S, W, 23, 0x76f988da831153b5UL);
    RNDr(S, W, 24, 0x983e5152ee66dfabUL);
    RNDr(S, W, 25, 0xa831c66d2db43210UL);
    RNDr(S, W, 26, 0xb00327c898fb213fUL);
    RNDr(S, W, 27, 0xbf597fc7beef0ee4UL);
    RNDr(S, W, 28, 0xc6e00bf33da88fc2UL);
    RNDr(S, W, 29, 0xd5a79147930aa725UL);
    RNDr(S, W, 30, 0x06ca6351e003826fUL);
    RNDr(S, W, 31, 0x142929670a0e6e70UL);
    RNDr(S, W, 32, 0x27b70a8546d22ffcUL);
    RNDr(S, W, 33, 0x2e1b21385c26c926UL);
    RNDr(S, W, 34, 0x4d2c6dfc5ac42aedUL);
    RNDr(S, W, 35, 0x53380d139d95b3dfUL);
    RNDr(S, W, 36, 0x650a73548baf63deUL);
    RNDr(S, W, 37, 0x766a0abb3c77b2a8UL);
    RNDr(S, W, 38, 0x81c2c92e47edaee6UL);
    RNDr(S, W, 39, 0x92722c851482353bUL);
    RNDr(S, W, 40, 0xa2bfe8a14cf10364UL);
    RNDr(S, W, 41, 0xa81a664bbc423001UL);
    RNDr(S, W, 42, 0xc24b8b70d0f89791UL);
    RNDr(S, W, 43, 0xc76c51a30654be30UL);
    RNDr(S, W, 44, 0xd192e819d6ef5218UL);
    RNDr(S, W, 45, 0xd69906245565a910UL);
    RNDr(S, W, 46, 0xf40e35855771202aUL);
    RNDr(S, W, 47, 0x106aa07032bbd1b8UL);
    RNDr(S, W, 48, 0x19a4c116b8d2d0c8UL);
    RNDr(S, W, 49, 0x1e376c085141ab53UL);
    RNDr(S, W, 50, 0x2748774cdf8eeb99UL);
    RNDr(S, W, 51, 0x34b0bcb5e19b48a8UL);
    RNDr(S, W, 52, 0x391c0cb3c5c95a63UL);
    RNDr(S, W, 53, 0x4ed8aa4ae3418acbUL);
    RNDr(S, W, 54, 0x5b9cca4f7763e373UL);
    RNDr(S, W, 55, 0x682e6ff3d6b2b8a3UL);
    RNDr(S, W, 56, 0x748f82ee5defb2fcUL);
    RNDr(S, W, 57, 0x78a5636f43172f60UL);
    RNDr(S, W, 58, 0x84c87814a1f0ab72UL);
    RNDr(S, W, 59, 0x8cc702081a6439ecUL);
    RNDr(S, W, 60, 0x90befffa23631e28UL);
    RNDr(S, W, 61, 0xa4506cebde82bde9UL);
    RNDr(S, W, 62, 0xbef9a3f7b2c67915UL);
    RNDr(S, W, 63, 0xc67178f2e372532bUL);
    RNDr(S, W, 64, 0xca273eceea26619cUL);
    RNDr(S, W, 65, 0xd186b8c721c0c207UL);
    RNDr(S, W, 66, 0xeada7dd6cde0eb1eUL);
    RNDr(S, W, 67, 0xf57d4f7fee6ed178UL);
    RNDr(S, W, 68, 0x06f067aa72176fbaUL);
    RNDr(S, W, 69, 0x0a637dc5a2c898a6UL);
    RNDr(S, W, 70, 0x113f9804bef90daeUL);
    RNDr(S, W, 71, 0x1b710b35131c471bUL);
    RNDr(S, W, 72, 0x28db77f523047d84UL);
    RNDr(S, W, 73, 0x32caab7b40c72493UL);
    RNDr(S, W, 74, 0x3c9ebe0a15c9bebcUL);
    RNDr(S, W, 75, 0x431d67c49c100d4cUL);
    RNDr(S, W, 76, 0x4cc5d4becb3e42b6UL);
    RNDr(S, W, 77, 0x597f299cfc657e2aUL);
    RNDr(S, W, 78, 0x5fcb6fab3ad6faecUL);
    RNDr(S, W, 79, 0x6c44198c4a475817UL);

    for (i = 0; i < 8; i++) {
        state[i] += S[i];
    }
}


void sha512_pad(hash_sha512_state *state) {
    unsigned char len[16];
    uint64_t r, plen;
	const uchar PAD[128] = {
		0x80, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
		0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
		0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
		0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
		0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
		0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
		0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
		0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
	};

	be64enc_vect(len, state->count, 16);

    r = (state->count[1] >> 3) & 0x7f;
    plen = (r < 112) ? (112 - r) : (240 - r);
    hash_sha512_update(state, PAD, (ulong) plen);   /* Ajay */

    hash_sha512_update(state, len, 16);
}

uint64_t load_3(const unsigned char *in) {
    uint64_t result;
    result = (uint64_t) in[0];
    result |= ((uint64_t) in[1]) << 8;
    result |= ((uint64_t) in[2]) << 16;
    return result;
}

uint64_t load_4(const unsigned char *in) {
	//return (ulong)(*(uint *)in);
    uint64_t result;
    result = (uint64_t) in[0];
    result |= ((uint64_t) in[1]) << 8;
    result |= ((uint64_t) in[2]) << 16;
    result |= ((uint64_t) in[3]) << 24;
    return result;
}

void sc_reduce(unsigned char *s) {
    int64_t s0 = 2097151 & load_3(s);
    int64_t s1 = 2097151 & (load_4(s + 2) >> 5);
    int64_t s2 = 2097151 & (load_3(s + 5) >> 2);
    int64_t s3 = 2097151 & (load_4(s + 7) >> 7);
    int64_t s4 = 2097151 & (load_4(s + 10) >> 4);
    int64_t s5 = 2097151 & (load_3(s + 13) >> 1);
    int64_t s6 = 2097151 & (load_4(s + 15) >> 6);
    int64_t s7 = 2097151 & (load_3(s + 18) >> 3);
    int64_t s8 = 2097151 & load_3(s + 21);
    int64_t s9 = 2097151 & (load_4(s + 23) >> 5);
    int64_t s10 = 2097151 & (load_3(s + 26) >> 2);
    int64_t s11 = 2097151 & (load_4(s + 28) >> 7);
    int64_t s12 = 2097151 & (load_4(s + 31) >> 4);
    int64_t s13 = 2097151 & (load_3(s + 34) >> 1);
    int64_t s14 = 2097151 & (load_4(s + 36) >> 6);
    int64_t s15 = 2097151 & (load_3(s + 39) >> 3);
    int64_t s16 = 2097151 & load_3(s + 42);
    int64_t s17 = 2097151 & (load_4(s + 44) >> 5);
    int64_t s18 = 2097151 & (load_3(s + 47) >> 2);
    int64_t s19 = 2097151 & (load_4(s + 49) >> 7);
    int64_t s20 = 2097151 & (load_4(s + 52) >> 4);
    int64_t s21 = 2097151 & (load_3(s + 55) >> 1);
    int64_t s22 = 2097151 & (load_4(s + 57) >> 6);
    int64_t s23 = (load_4(s + 60) >> 3);
    int64_t carry0;
    int64_t carry1;
    int64_t carry2;
    int64_t carry3;
    int64_t carry4;
    int64_t carry5;
    int64_t carry6;
    int64_t carry7;
    int64_t carry8;
    int64_t carry9;
    int64_t carry10;
    int64_t carry11;
    int64_t carry12;
    int64_t carry13;
    int64_t carry14;
    int64_t carry15;
    int64_t carry16;

    s11 += s23 * 666643;
    s12 += s23 * 470296;
    s13 += s23 * 654183;
    s14 -= s23 * 997805;
    s15 += s23 * 136657;
    s16 -= s23 * 683901;

    s10 += s22 * 666643;
    s11 += s22 * 470296;
    s12 += s22 * 654183;
    s13 -= s22 * 997805;
    s14 += s22 * 136657;
    s15 -= s22 * 683901;

    s9 += s21 * 666643;
    s10 += s21 * 470296;
    s11 += s21 * 654183;
    s12 -= s21 * 997805;
    s13 += s21 * 136657;
    s14 -= s21 * 683901;

    s8 += s20 * 666643;
    s9 += s20 * 470296;
    s10 += s20 * 654183;
    s11 -= s20 * 997805;
    s12 += s20 * 136657;
    s13 -= s20 * 683901;

    s7 += s19 * 666643;
    s8 += s19 * 470296;
    s9 += s19 * 654183;
    s10 -= s19 * 997805;
    s11 += s19 * 136657;
    s12 -= s19 * 683901;

    s6 += s18 * 666643;
    s7 += s18 * 470296;
    s8 += s18 * 654183;
    s9 -= s18 * 997805;
    s10 += s18 * 136657;
    s11 -= s18 * 683901;

    carry6 = (s6 + (1<<20)) >> 21; s7 += carry6; s6 -= carry6 << 21;
    carry8 = (s8 + (1<<20)) >> 21; s9 += carry8; s8 -= carry8 << 21;
    carry10 = (s10 + (1<<20)) >> 21; s11 += carry10; s10 -= carry10 << 21;
    carry12 = (s12 + (1<<20)) >> 21; s13 += carry12; s12 -= carry12 << 21;
    carry14 = (s14 + (1<<20)) >> 21; s15 += carry14; s14 -= carry14 << 21;
    carry16 = (s16 + (1<<20)) >> 21; s17 += carry16; s16 -= carry16 << 21;

    carry7 = (s7 + (1<<20)) >> 21; s8 += carry7; s7 -= carry7 << 21;
    carry9 = (s9 + (1<<20)) >> 21; s10 += carry9; s9 -= carry9 << 21;
    carry11 = (s11 + (1<<20)) >> 21; s12 += carry11; s11 -= carry11 << 21;
    carry13 = (s13 + (1<<20)) >> 21; s14 += carry13; s13 -= carry13 << 21;
    carry15 = (s15 + (1<<20)) >> 21; s16 += carry15; s15 -= carry15 << 21;

    s5 += s17 * 666643;
    s6 += s17 * 470296;
    s7 += s17 * 654183;
    s8 -= s17 * 997805;
    s9 += s17 * 136657;
    s10 -= s17 * 683901;

    s4 += s16 * 666643;
    s5 += s16 * 470296;
    s6 += s16 * 654183;
    s7 -= s16 * 997805;
    s8 += s16 * 136657;
    s9 -= s16 * 683901;

    s3 += s15 * 666643;
    s4 += s15 * 470296;
    s5 += s15 * 654183;
    s6 -= s15 * 997805;
    s7 += s15 * 136657;
    s8 -= s15 * 683901;

    s2 += s14 * 666643;
    s3 += s14 * 470296;
    s4 += s14 * 654183;
    s5 -= s14 * 997805;
    s6 += s14 * 136657;
    s7 -= s14 * 683901;

    s1 += s13 * 666643;
    s2 += s13 * 470296;
    s3 += s13 * 654183;
    s4 -= s13 * 997805;
    s5 += s13 * 136657;
    s6 -= s13 * 683901;

    s0 += s12 * 666643;
    s1 += s12 * 470296;
    s2 += s12 * 654183;
    s3 -= s12 * 997805;
    s4 += s12 * 136657;
    s5 -= s12 * 683901;
    s12 = 0;

    carry0 = (s0 + (1<<20)) >> 21; s1 += carry0; s0 -= carry0 << 21;
    carry2 = (s2 + (1<<20)) >> 21; s3 += carry2; s2 -= carry2 << 21;
    carry4 = (s4 + (1<<20)) >> 21; s5 += carry4; s4 -= carry4 << 21;
    carry6 = (s6 + (1<<20)) >> 21; s7 += carry6; s6 -= carry6 << 21;
    carry8 = (s8 + (1<<20)) >> 21; s9 += carry8; s8 -= carry8 << 21;
    carry10 = (s10 + (1<<20)) >> 21; s11 += carry10; s10 -= carry10 << 21;

    carry1 = (s1 + (1<<20)) >> 21; s2 += carry1; s1 -= carry1 << 21;
    carry3 = (s3 + (1<<20)) >> 21; s4 += carry3; s3 -= carry3 << 21;
    carry5 = (s5 + (1<<20)) >> 21; s6 += carry5; s5 -= carry5 << 21;
    carry7 = (s7 + (1<<20)) >> 21; s8 += carry7; s7 -= carry7 << 21;
    carry9 = (s9 + (1<<20)) >> 21; s10 += carry9; s9 -= carry9 << 21;
    carry11 = (s11 + (1<<20)) >> 21; s12 += carry11; s11 -= carry11 << 21;

    s0 += s12 * 666643;
    s1 += s12 * 470296;
    s2 += s12 * 654183;
    s3 -= s12 * 997805;
    s4 += s12 * 136657;
    s5 -= s12 * 683901;
    s12 = 0;

    carry0 = s0 >> 21; s1 += carry0; s0 -= carry0 << 21;
    carry1 = s1 >> 21; s2 += carry1; s1 -= carry1 << 21;
    carry2 = s2 >> 21; s3 += carry2; s2 -= carry2 << 21;
    carry3 = s3 >> 21; s4 += carry3; s3 -= carry3 << 21;
    carry4 = s4 >> 21; s5 += carry4; s4 -= carry4 << 21;
    carry5 = s5 >> 21; s6 += carry5; s5 -= carry5 << 21;
    carry6 = s6 >> 21; s7 += carry6; s6 -= carry6 << 21;
    carry7 = s7 >> 21; s8 += carry7; s7 -= carry7 << 21;
    carry8 = s8 >> 21; s9 += carry8; s8 -= carry8 << 21;
    carry9 = s9 >> 21; s10 += carry9; s9 -= carry9 << 21;
    carry10 = s10 >> 21; s11 += carry10; s10 -= carry10 << 21;
    carry11 = s11 >> 21; s12 += carry11; s11 -= carry11 << 21;

    s0 += s12 * 666643;
    s1 += s12 * 470296;
    s2 += s12 * 654183;
    s3 -= s12 * 997805;
    s4 += s12 * 136657;
    s5 -= s12 * 683901;

    carry0 = s0 >> 21; s1 += carry0; s0 -= carry0 << 21;
    carry1 = s1 >> 21; s2 += carry1; s1 -= carry1 << 21;
    carry2 = s2 >> 21; s3 += carry2; s2 -= carry2 << 21;
    carry3 = s3 >> 21; s4 += carry3; s3 -= carry3 << 21;
    carry4 = s4 >> 21; s5 += carry4; s4 -= carry4 << 21;
    carry5 = s5 >> 21; s6 += carry5; s5 -= carry5 << 21;
    carry6 = s6 >> 21; s7 += carry6; s6 -= carry6 << 21;
    carry7 = s7 >> 21; s8 += carry7; s7 -= carry7 << 21;
    carry8 = s8 >> 21; s9 += carry8; s8 -= carry8 << 21;
    carry9 = s9 >> 21; s10 += carry9; s9 -= carry9 << 21;
    carry10 = s10 >> 21; s11 += carry10; s10 -= carry10 << 21;

    s[0] = s0 >> 0;
    s[1] = s0 >> 8;
    s[2] = (s0 >> 16) | (s1 << 5);
    s[3] = s1 >> 3;
    s[4] = s1 >> 11;
    s[5] = (s1 >> 19) | (s2 << 2);
    s[6] = s2 >> 6;
    s[7] = (s2 >> 14) | (s3 << 7);
    s[8] = s3 >> 1;
    s[9] = s3 >> 9;
    s[10] = (s3 >> 17) | (s4 << 4);
    s[11] = s4 >> 4;
    s[12] = s4 >> 12;
    s[13] = (s4 >> 20) | (s5 << 1);
    s[14] = s5 >> 7;
    s[15] = (s5 >> 15) | (s6 << 6);
    s[16] = s6 >> 2;
    s[17] = s6 >> 10;
    s[18] = (s6 >> 18) | (s7 << 3);
    s[19] = s7 >> 5;
    s[20] = s7 >> 13;
    s[21] = s8 >> 0;
    s[22] = s8 >> 8;
    s[23] = (s8 >> 16) | (s9 << 5);
    s[24] = s9 >> 3;
    s[25] = s9 >> 11;
    s[26] = (s9 >> 19) | (s10 << 2);
    s[27] = s10 >> 6;
    s[28] = (s10 >> 14) | (s11 << 7);
    s[29] = s11 >> 1;
    s[30] = s11 >> 9;
    s[31] = s11 >> 17;
}

uint64_t be64dec(const void *pp) {
    const uint8_t *p = (uint8_t const *)pp;

    return ((uint64_t)(p[7]) + ((uint64_t)(p[6]) << 8) +
            ((uint64_t)(p[5]) << 16) + ((uint64_t)(p[4]) << 24) +
            ((uint64_t)(p[3]) << 32) + ((uint64_t)(p[2]) << 40) +
            ((uint64_t)(p[1]) << 48) + ((uint64_t)(p[0]) << 56));
}

void be64enc(void *pp, uint64_t x) {
    uint8_t *p = (uint8_t *)pp;

    p[7] = x & 0xff;
    p[6] = (x >> 8) & 0xff;
    p[5] = (x >> 16) & 0xff;
    p[4] = (x >> 24) & 0xff;
    p[3] = (x >> 32) & 0xff;
    p[2] = (x >> 40) & 0xff;
    p[1] = (x >> 48) & 0xff;
    p[0] = (x >> 56) & 0xff;
}

void be64enc_vect(unsigned char *dst, const uint64_t *src, ulong len) {  /* Ajay */
    ulong i;     /* Ajay */

    for (i = 0; i < len / 8; i++) {
        be64enc(dst + i * 8, src[i]);
    }
}

void be64dec_vect(uint64_t *dst, const unsigned char* src, ulong len) {   /* Ajay */
    ulong i;                              /* Ajay */

    for (i = 0; i < len / 8; i++) {
        dst[i] = be64dec(src + i * 8);
    }
}

int verify_32(const unsigned char *x,const unsigned char *y) {
    unsigned int differentbits = 0;

#define F(i) differentbits |= x[i] ^ y[i];

    F(0)
    F(1)
    F(2)
    F(3)
    F(4)
    F(5)
    F(6)
    F(7)
    F(8)
    F(9)
    F(10)
    F(11)
    F(12)
    F(13)
    F(14)
    F(15)
    F(16)
    F(17)
    F(18)
    F(19)
    F(20)
    F(21)
    F(22)
    F(23)
    F(24)
    F(25)
    F(26)
    F(27)
    F(28)
    F(29)
    F(30)
    F(31)
    return (1 & ((differentbits - 1) >> 8)) - 1;
}

/* Ajay  */
/*
static const fe d = {
    -10913610,13857413,-15372611,6949391,114729,
    -8787816,-6275908,-3247719,-18696448,-12055116
};

static const fe sqrtm1 = {
    -32595792,-7943725,9377950,3500415,12389472,
    -272473,-25146209,-2005654,326686,11406482
};
*/

int ge_frombytes_negate_vartime(ge_p3 *h, const unsigned char *s) {
    fe u;
    fe v;
    fe v3;
    fe vxx;
    fe check;

    /* Ajay */
    const fe d = {
    -10913610,13857413,-15372611,6949391,114729,
    -8787816,-6275908,-3247719,-18696448,-12055116
   };

    /* Ajay */
    const fe sqrtm1 = {
    -32595792,-7943725,9377950,3500415,12389472,
    -272473,-25146209,-2005654,326686,11406482
    };

    fe_frombytes(h->Y,s);
    fe_1(h->Z);
    fe_sq(u,h->Y);
    fe_mul(v,u,d);
    fe_sub(u,u,h->Z);       /* u = y^2-1 */
    fe_add(v,v,h->Z);       /* v = dy^2+1 */

    fe_sq(v3,v);
    fe_mul(v3,v3,v);        /* v3 = v^3 */
    fe_sq(h->X,v3);
    fe_mul(h->X,h->X,v);
    fe_mul(h->X,h->X,u);    /* x = uv^7 */

    fe_pow22523(h->X,h->X); /* x = (uv^7)^((q-5)/8) */
    fe_mul(h->X,h->X,v3);
    fe_mul(h->X,h->X,u);    /* x = uv^3(uv^7)^((q-5)/8) */

    fe_sq(vxx,h->X);
    fe_mul(vxx,vxx,v);
    fe_sub(check,vxx,u);    /* vx^2-u */
    if (fe_isnonzero(check)) {
        fe_add(check,vxx,u);  /* vx^2+u */
        if (fe_isnonzero(check)) { 
        	//printf("Error ge mult\n"); 
        	return -1;
        }
        fe_mul(h->X,h->X,sqrtm1);
    }

    if (fe_isnegative(h->X) == (s[31] >> 7))
        fe_neg(h->X,h->X);

    fe_mul(h->T,h->X,h->Y);
    return 0;
}

/* Ajay */
/*
static ge_precomp Bi[8] = {
    {
        { 25967493,-14356035,29566456,3660896,-12694345,4014787,27544626,-11754271,-6079156,2047605 },
        { -12545711,934262,-2722910,3049990,-727428,9406986,12720692,5043384,19500929,-15469378 },
        { -8738181,4489570,9688441,-14785194,10184609,-12363380,29287919,11864899,-24514362,-4438546 },
    },
    {
        { 15636291,-9688557,24204773,-7912398,616977,-16685262,27787600,-14772189,28944400,-1550024 },
        { 16568933,4717097,-11556148,-1102322,15682896,-11807043,16354577,-11775962,7689662,11199574 },
        { 30464156,-5976125,-11779434,-15670865,23220365,15915852,7512774,10017326,-17749093,-9920357 },
    },
    {
        { 10861363,11473154,27284546,1981175,-30064349,12577861,32867885,14515107,-15438304,10819380 },
        { 4708026,6336745,20377586,9066809,-11272109,6594696,-25653668,12483688,-12668491,5581306 },
        { 19563160,16186464,-29386857,4097519,10237984,-4348115,28542350,13850243,-23678021,-15815942 },
    },
    {
        { 5153746,9909285,1723747,-2777874,30523605,5516873,19480852,5230134,-23952439,-15175766 },
        { -30269007,-3463509,7665486,10083793,28475525,1649722,20654025,16520125,30598449,7715701 },
        { 28881845,14381568,9657904,3680757,-20181635,7843316,-31400660,1370708,29794553,-1409300 },
    },
    {
        { -22518993,-6692182,14201702,-8745502,-23510406,8844726,18474211,-1361450,-13062696,13821877 },
        { -6455177,-7839871,3374702,-4740862,-27098617,-10571707,31655028,-7212327,18853322,-14220951 },
        { 4566830,-12963868,-28974889,-12240689,-7602672,-2830569,-8514358,-10431137,2207753,-3209784 },
    },
    {
        { -25154831,-4185821,29681144,7868801,-6854661,-9423865,-12437364,-663000,-31111463,-16132436 },
        { 25576264,-2703214,7349804,-11814844,16472782,9300885,3844789,15725684,171356,6466918 },
        { 23103977,13316479,9739013,-16149481,817875,-15038942,8965339,-14088058,-30714912,16193877 },
    },
    {
        { -33521811,3180713,-2394130,14003687,-16903474,-16270840,17238398,4729455,-18074513,9256800 },
        { -25182317,-4174131,32336398,5036987,-21236817,11360617,22616405,9761698,-19827198,630305 },
        { -13720693,2639453,-24237460,-7406481,9494427,-5774029,-6554551,-15960994,-2449256,-14291300 },
    },
    {
        { -3151181,-5046075,9282714,6866145,-31907062,-863023,-18940575,15033784,25105118,-7894876 },
        { -24326370,15950226,-31801215,-14592823,-11662737,-5090925,1573892,-2625887,2198790,-15804619 },
        { -3099351,10324967,-2241613,7453183,-5446979,-2735503,-13812022,-16236442,-32461234,-12290683 },
    },
};
*/

void ge_double_scalarmult_vartime(ge_p2 *r, const unsigned char *a,
                                  const ge_p3 *A, const unsigned char *b) {
    signed char aslide[256];
    signed char bslide[256];
    ge_cached Ai[8]; /* A,3A,5A,7A,9A,11A,13A,15A */
    ge_p1p1 t;
    ge_p3 u;
    ge_p3 A2;
    int i;
    /* Ajay */
    const ge_precomp Bi[8] = {
    {
        { 25967493,-14356035,29566456,3660896,-12694345,4014787,27544626,-11754271,-6079156,2047605 },
        { -12545711,934262,-2722910,3049990,-727428,9406986,12720692,5043384,19500929,-15469378 },
        { -8738181,4489570,9688441,-14785194,10184609,-12363380,29287919,11864899,-24514362,-4438546 },
    },
    {
        { 15636291,-9688557,24204773,-7912398,616977,-16685262,27787600,-14772189,28944400,-1550024 },
        { 16568933,4717097,-11556148,-1102322,15682896,-11807043,16354577,-11775962,7689662,11199574 },
        { 30464156,-5976125,-11779434,-15670865,23220365,15915852,7512774,10017326,-17749093,-9920357 },
    },
    {
        { 10861363,11473154,27284546,1981175,-30064349,12577861,32867885,14515107,-15438304,10819380 },
        { 4708026,6336745,20377586,9066809,-11272109,6594696,-25653668,12483688,-12668491,5581306 },
        { 19563160,16186464,-29386857,4097519,10237984,-4348115,28542350,13850243,-23678021,-15815942 },
    },
    {
        { 5153746,9909285,1723747,-2777874,30523605,5516873,19480852,5230134,-23952439,-15175766 },
        { -30269007,-3463509,7665486,10083793,28475525,1649722,20654025,16520125,30598449,7715701 },
        { 28881845,14381568,9657904,3680757,-20181635,7843316,-31400660,1370708,29794553,-1409300 },
    },
    {
        { -22518993,-6692182,14201702,-8745502,-23510406,8844726,18474211,-1361450,-13062696,13821877 },
        { -6455177,-7839871,3374702,-4740862,-27098617,-10571707,31655028,-7212327,18853322,-14220951 },
        { 4566830,-12963868,-28974889,-12240689,-7602672,-2830569,-8514358,-10431137,2207753,-3209784 },
    },
    {
        { -25154831,-4185821,29681144,7868801,-6854661,-9423865,-12437364,-663000,-31111463,-16132436 },
        { 25576264,-2703214,7349804,-11814844,16472782,9300885,3844789,15725684,171356,6466918 },
        { 23103977,13316479,9739013,-16149481,817875,-15038942,8965339,-14088058,-30714912,16193877 },
    },
    {
        { -33521811,3180713,-2394130,14003687,-16903474,-16270840,17238398,4729455,-18074513,9256800 },
        { -25182317,-4174131,32336398,5036987,-21236817,11360617,22616405,9761698,-19827198,630305 },
        { -13720693,2639453,-24237460,-7406481,9494427,-5774029,-6554551,-15960994,-2449256,-14291300 },
    },
    {
        { -3151181,-5046075,9282714,6866145,-31907062,-863023,-18940575,15033784,25105118,-7894876 },
        { -24326370,15950226,-31801215,-14592823,-11662737,-5090925,1573892,-2625887,2198790,-15804619 },
        { -3099351,10324967,-2241613,7453183,-5446979,-2735503,-13812022,-16236442,-32461234,-12290683 },
    },
};

    slide(aslide,a);
    slide(bslide,b);

    ge_p3_to_cached(&Ai[0],A);
    ge_p3_dbl(&t,A); ge_p1p1_to_p3(&A2,&t);
    ge_add(&t,&A2,&Ai[0]); ge_p1p1_to_p3(&u,&t); ge_p3_to_cached(&Ai[1],&u);
    ge_add(&t,&A2,&Ai[1]); ge_p1p1_to_p3(&u,&t); ge_p3_to_cached(&Ai[2],&u);
    ge_add(&t,&A2,&Ai[2]); ge_p1p1_to_p3(&u,&t); ge_p3_to_cached(&Ai[3],&u);
    ge_add(&t,&A2,&Ai[3]); ge_p1p1_to_p3(&u,&t); ge_p3_to_cached(&Ai[4],&u);
    ge_add(&t,&A2,&Ai[4]); ge_p1p1_to_p3(&u,&t); ge_p3_to_cached(&Ai[5],&u);
    ge_add(&t,&A2,&Ai[5]); ge_p1p1_to_p3(&u,&t); ge_p3_to_cached(&Ai[6],&u);
    ge_add(&t,&A2,&Ai[6]); ge_p1p1_to_p3(&u,&t); ge_p3_to_cached(&Ai[7],&u);

    ge_p2_0(r);

    for (i = 255;i >= 0;--i) {
        if (aslide[i] || bslide[i]) break;
    }

    for (;i >= 0;--i) {
        ge_p2_dbl(&t,r);

        if (aslide[i] > 0) {
            ge_p1p1_to_p3(&u,&t);
            ge_add(&t,&u,&Ai[aslide[i]/2]);
        } else if (aslide[i] < 0) {
            ge_p1p1_to_p3(&u,&t);
            ge_sub(&t,&u,&Ai[(-aslide[i])/2]);
        }

        if (bslide[i] > 0) {
            ge_p1p1_to_p3(&u,&t);
            ge_madd(&t,&u,&Bi[bslide[i]/2]);
        } else if (bslide[i] < 0) {
            ge_p1p1_to_p3(&u,&t);
            ge_msub(&t,&u,&Bi[(-bslide[i])/2]);
        }

        ge_p1p1_to_p2(r,&t);
    }
}

void ge_tobytes(unsigned char *s, const ge_p2 *h) {
    fe recip;
    fe x;
    fe y;

    fe_invert(recip,h->Z);
    fe_mul(x,h->X,recip);
    fe_mul(y,h->Y,recip);
    fe_tobytes(s,y);
    s[31] ^= fe_isnegative(x) << 7;
}

void slide(signed char *r, const unsigned char *a) {
    int i;
    int b;
    int k;

    for (i = 0;i < 256;++i)
        r[i] = 1 & (a[i >> 3] >> (i & 7));

    for (i = 0;i < 256;++i) {
        if (r[i]) {
            for (b = 1;b <= 6 && i + b < 256;++b) {
                if (r[i + b]) {
                    if (r[i] + (r[i + b] << b) <= 15) {
                        r[i] += r[i + b] << b; r[i + b] = 0;
                    } else if (r[i] - (r[i + b] << b) >= -15) {
                        r[i] -= r[i + b] << b;
                        for (k = i + b;k < 256;++k) {
                            if (!r[k]) {
                                r[k] = 1;
                                break;
                            }
                            r[k] = 0;
                        }
                    } else {
                        break;
                    }
                }
            }
        }
    }
}

void fe_add(fe h, const fe f, const fe g) {
#pragma unroll
	for (int i = 0; i < 10; i++)
		h[i] = f[i] + g[i];
/*
    int32_t f0 = f[0];
    int32_t f1 = f[1];
    int32_t f2 = f[2];
    int32_t f3 = f[3];
    int32_t f4 = f[4];
    int32_t f5 = f[5];
    int32_t f6 = f[6];
    int32_t f7 = f[7];
    int32_t f8 = f[8];
    int32_t f9 = f[9];
    int32_t g0 = g[0];
    int32_t g1 = g[1];
    int32_t g2 = g[2];
    int32_t g3 = g[3];
    int32_t g4 = g[4];
    int32_t g5 = g[5];
    int32_t g6 = g[6];
    int32_t g7 = g[7];
    int32_t g8 = g[8];
    int32_t g9 = g[9];
    int32_t h0 = f0 + g0;
    int32_t h1 = f1 + g1;
    int32_t h2 = f2 + g2;
    int32_t h3 = f3 + g3;
    int32_t h4 = f4 + g4;
    int32_t h5 = f5 + g5;
    int32_t h6 = f6 + g6;
    int32_t h7 = f7 + g7;
    int32_t h8 = f8 + g8;
    int32_t h9 = f9 + g9;
    h[0] = h0;
    h[1] = h1;
    h[2] = h2;
    h[3] = h3;
    h[4] = h4;
    h[5] = h5;
    h[6] = h6;
    h[7] = h7;
    h[8] = h8;
    h[9] = h9;*/
}

void fe_frombytes(fe h, const unsigned char *s) {
    int64_t h0 = load_4(s);
    int64_t h1 = load_3(s + 4) << 6;
    int64_t h2 = load_3(s + 7) << 5;
    int64_t h3 = load_3(s + 10) << 3;
    int64_t h4 = load_3(s + 13) << 2;
    int64_t h5 = load_4(s + 16);
    int64_t h6 = load_3(s + 20) << 7;
    int64_t h7 = load_3(s + 23) << 5;
    int64_t h8 = load_3(s + 26) << 4;
    int64_t h9 = (load_3(s + 29) & 8388607) << 2;
    int64_t carry0;
    int64_t carry1;
    int64_t carry2;
    int64_t carry3;
    int64_t carry4;
    int64_t carry5;
    int64_t carry6;
    int64_t carry7;
    int64_t carry8;
    int64_t carry9;

    carry9 = (h9 + (int64_t) (1<<24)) >> 25; h0 += carry9 * 19; h9 -= carry9 << 25;
    carry1 = (h1 + (int64_t) (1<<24)) >> 25; h2 += carry1; h1 -= carry1 << 25;
    carry3 = (h3 + (int64_t) (1<<24)) >> 25; h4 += carry3; h3 -= carry3 << 25;
    carry5 = (h5 + (int64_t) (1<<24)) >> 25; h6 += carry5; h5 -= carry5 << 25;
    carry7 = (h7 + (int64_t) (1<<24)) >> 25; h8 += carry7; h7 -= carry7 << 25;

    carry0 = (h0 + (int64_t) (1<<25)) >> 26; h1 += carry0; h0 -= carry0 << 26;
    carry2 = (h2 + (int64_t) (1<<25)) >> 26; h3 += carry2; h2 -= carry2 << 26;
    carry4 = (h4 + (int64_t) (1<<25)) >> 26; h5 += carry4; h4 -= carry4 << 26;
    carry6 = (h6 + (int64_t) (1<<25)) >> 26; h7 += carry6; h6 -= carry6 << 26;
    carry8 = (h8 + (int64_t) (1<<25)) >> 26; h9 += carry8; h8 -= carry8 << 26;

    h[0] = h0;
    h[1] = h1;
    h[2] = h2;
    h[3] = h3;
    h[4] = h4;
    h[5] = h5;
    h[6] = h6;
    h[7] = h7;
    h[8] = h8;
    h[9] = h9;
}

void fe_tobytes(unsigned char *s,const fe h) {
    int32_t h0 = h[0];
    int32_t h1 = h[1];
    int32_t h2 = h[2];
    int32_t h3 = h[3];
    int32_t h4 = h[4];
    int32_t h5 = h[5];
    int32_t h6 = h[6];
    int32_t h7 = h[7];
    int32_t h8 = h[8];
    int32_t h9 = h[9];
    int32_t q;
    int32_t carry0;
    int32_t carry1;
    int32_t carry2;
    int32_t carry3;
    int32_t carry4;
    int32_t carry5;
    int32_t carry6;
    int32_t carry7;
    int32_t carry8;
    int32_t carry9;

    q = (19 * h9 + (((int32_t) 1) << 24)) >> 25;
    q = (h0 + q) >> 26;
    q = (h1 + q) >> 25;
    q = (h2 + q) >> 26;
    q = (h3 + q) >> 25;
    q = (h4 + q) >> 26;
    q = (h5 + q) >> 25;
    q = (h6 + q) >> 26;
    q = (h7 + q) >> 25;
    q = (h8 + q) >> 26;
    q = (h9 + q) >> 25;

    /* Goal: Output h-(2^255-19)q, which is between 0 and 2^255-20. */
    h0 += 19 * q;
    /* Goal: Output h-2^255 q, which is between 0 and 2^255-20. */

    carry0 = h0 >> 26; h1 += carry0; h0 -= carry0 << 26;
    carry1 = h1 >> 25; h2 += carry1; h1 -= carry1 << 25;
    carry2 = h2 >> 26; h3 += carry2; h2 -= carry2 << 26;
    carry3 = h3 >> 25; h4 += carry3; h3 -= carry3 << 25;
    carry4 = h4 >> 26; h5 += carry4; h4 -= carry4 << 26;
    carry5 = h5 >> 25; h6 += carry5; h5 -= carry5 << 25;
    carry6 = h6 >> 26; h7 += carry6; h6 -= carry6 << 26;
    carry7 = h7 >> 25; h8 += carry7; h7 -= carry7 << 25;
    carry8 = h8 >> 26; h9 += carry8; h8 -= carry8 << 26;
    carry9 = h9 >> 25;               h9 -= carry9 << 25;
                    /* h10 = carry9 */

    s[0] = h0 >> 0;
    s[1] = h0 >> 8;
    s[2] = h0 >> 16;
    s[3] = (h0 >> 24) | (h1 << 2);
    s[4] = h1 >> 6;
    s[5] = h1 >> 14;
    s[6] = (h1 >> 22) | (h2 << 3);
    s[7] = h2 >> 5;
    s[8] = h2 >> 13;
    s[9] = (h2 >> 21) | (h3 << 5);
    s[10] = h3 >> 3;
    s[11] = h3 >> 11;
    s[12] = (h3 >> 19) | (h4 << 6);
    s[13] = h4 >> 2;
    s[14] = h4 >> 10;
    s[15] = h4 >> 18;
    s[16] = h5 >> 0;
    s[17] = h5 >> 8;
    s[18] = h5 >> 16;
    s[19] = (h5 >> 24) | (h6 << 1);
    s[20] = h6 >> 7;
    s[21] = h6 >> 15;
    s[22] = (h6 >> 23) | (h7 << 3);
    s[23] = h7 >> 5;
    s[24] = h7 >> 13;
    s[25] = (h7 >> 21) | (h8 << 4);
    s[26] = h8 >> 4;
    s[27] = h8 >> 12;
    s[28] = (h8 >> 20) | (h9 << 6);
    s[29] = h9 >> 2;
    s[30] = h9 >> 10;
    s[31] = h9 >> 18;
}

void fe_copy(fe h, const fe f) {
#pragma unroll
	for (int i = 0; i < 10; i++)
		h[i] = f[i];
}

void fe_0(fe h) {
    h[0] = 0;
    h[1] = 0;
    h[2] = 0;
    h[3] = 0;
    h[4] = 0;
    h[5] = 0;
    h[6] = 0;
    h[7] = 0;
    h[8] = 0;
    h[9] = 0;
}

void fe_1(fe h) {
    h[0] = 1;
    h[1] = 0;
    h[2] = 0;
    h[3] = 0;
    h[4] = 0;
    h[5] = 0;
    h[6] = 0;
    h[7] = 0;
    h[8] = 0;
    h[9] = 0;
}
#if 0
void fe_sq(fe h, const fe f) {
    int32_t f0 = f[0];
    int32_t f1 = f[1];
    int32_t f2 = f[2];
    int32_t f3 = f[3];
    int32_t f4 = f[4];
    int32_t f5 = f[5];
    int32_t f6 = f[6];
    int32_t f7 = f[7];
    int32_t f8 = f[8];
    int32_t f9 = f[9];
    int32_t f0_2 = f0 <<1;
    int32_t f1_2 = f1 <<1;
    int32_t f2_2 = f2 <<1;
    int32_t f3_2 = f3 <<1;
    int32_t f4_2 = f4 <<1;
    int32_t f5_2 = f5 <<1;
    int32_t f6_2 = f6 <<1;
    int32_t f7_2 = f7 <<1;
    int32_t f5_38 = 38 * f5; /* 1.31*2^30 */
    int32_t f6_19 = 19 * f6; /* 1.31*2^30 */
    int32_t f7_38 = 38 * f7; /* 1.31*2^30 */
    int32_t f8_19 = 19 * f8; /* 1.31*2^30 */
    int32_t f9_38 = 38 * f9; /* 1.31*2^30 */
    int64_t f0f0    = f0   * (int64_t) f0;
    int64_t f0f1_2  = f0_2 * (int64_t) f1;
    int64_t f0f2_2  = f0_2 * (int64_t) f2;
    int64_t f0f3_2  = f0_2 * (int64_t) f3;
    int64_t f0f4_2  = f0_2 * (int64_t) f4;
    int64_t f0f5_2  = f0_2 * (int64_t) f5;
    int64_t f0f6_2  = f0_2 * (int64_t) f6;
    int64_t f0f7_2  = f0_2 * (int64_t) f7;
    int64_t f0f8_2  = f0_2 * (int64_t) f8;
    int64_t f0f9_2  = f0_2 * (int64_t) f9;
    int64_t f1f1_2  = f1_2 * (int64_t) f1;
    int64_t f1f2_2  = f1_2 * (int64_t) f2;
    int64_t f1f3_4  = f1_2 * (int64_t) f3_2;
    int64_t f1f4_2  = f1_2 * (int64_t) f4;
    int64_t f1f5_4  = f1_2 * (int64_t) f5_2;
    int64_t f1f6_2  = f1_2 * (int64_t) f6;
    int64_t f1f7_4  = f1_2 * (int64_t) f7_2;
    int64_t f1f8_2  = f1_2 * (int64_t) f8;
    int64_t f1f9_76 = f1_2 * (int64_t) f9_38;
    int64_t f2f2    = f2   * (int64_t) f2;
    int64_t f2f3_2  = f2_2 * (int64_t) f3;
    int64_t f2f4_2  = f2_2 * (int64_t) f4;
    int64_t f2f5_2  = f2_2 * (int64_t) f5;
    int64_t f2f6_2  = f2_2 * (int64_t) f6;
    int64_t f2f7_2  = f2_2 * (int64_t) f7;
    int64_t f2f8_38 = f2_2 * (int64_t) f8_19;
    int64_t f2f9_38 = f2   * (int64_t) f9_38;
    int64_t f3f3_2  = f3_2 * (int64_t) f3;
    int64_t f3f4_2  = f3_2 * (int64_t) f4;
    int64_t f3f5_4  = f3_2 * (int64_t) f5_2;
    int64_t f3f6_2  = f3_2 * (int64_t) f6;
    int64_t f3f7_76 = f3_2 * (int64_t) f7_38;
    int64_t f3f8_38 = f3_2 * (int64_t) f8_19;
    int64_t f3f9_76 = f3_2 * (int64_t) f9_38;
    int64_t f4f4    = f4   * (int64_t) f4;
    int64_t f4f5_2  = f4_2 * (int64_t) f5;
    int64_t f4f6_38 = f4_2 * (int64_t) f6_19;
    int64_t f4f7_38 = f4   * (int64_t) f7_38;
    int64_t f4f8_38 = f4_2 * (int64_t) f8_19;
    int64_t f4f9_38 = f4   * (int64_t) f9_38;
    int64_t f5f5_38 = f5   * (int64_t) f5_38;
    int64_t f5f6_38 = f5_2 * (int64_t) f6_19;
    int64_t f5f7_76 = f5_2 * (int64_t) f7_38;
    int64_t f5f8_38 = f5_2 * (int64_t) f8_19;
    int64_t f5f9_76 = f5_2 * (int64_t) f9_38;
    int64_t f6f6_19 = f6   * (int64_t) f6_19;
    int64_t f6f7_38 = f6   * (int64_t) f7_38;
    int64_t f6f8_38 = f6_2 * (int64_t) f8_19;
    int64_t f6f9_38 = f6   * (int64_t) f9_38;
    int64_t f7f7_38 = f7   * (int64_t) f7_38;
    int64_t f7f8_38 = f7_2 * (int64_t) f8_19;
    int64_t f7f9_76 = f7_2 * (int64_t) f9_38;
    int64_t f8f8_19 = f8   * (int64_t) f8_19;
    int64_t f8f9_38 = f8   * (int64_t) f9_38;
    int64_t f9f9_38 = f9   * (int64_t) f9_38;
    int64_t h0 = f0f0  +f1f9_76+f2f8_38+f3f7_76+f4f6_38+f5f5_38;
    int64_t h1 = f0f1_2+f2f9_38+f3f8_38+f4f7_38+f5f6_38;
    int64_t h2 = f0f2_2+f1f1_2 +f3f9_76+f4f8_38+f5f7_76+f6f6_19;
    int64_t h3 = f0f3_2+f1f2_2 +f4f9_38+f5f8_38+f6f7_38;
    int64_t h4 = f0f4_2+f1f3_4 +f2f2   +f5f9_76+f6f8_38+f7f7_38;
    int64_t h5 = f0f5_2+f1f4_2 +f2f3_2 +f6f9_38+f7f8_38;
    int64_t h6 = f0f6_2+f1f5_4 +f2f4_2 +f3f3_2 +f7f9_76+f8f8_19;
    int64_t h7 = f0f7_2+f1f6_2 +f2f5_2 +f3f4_2 +f8f9_38;
    int64_t h8 = f0f8_2+f1f7_4 +f2f6_2 +f3f5_4 +f4f4   +f9f9_38;
    int64_t h9 = f0f9_2+f1f8_2 +f2f7_2 +f3f6_2 +f4f5_2;
    int64_t carry0;
    int64_t carry1;
    int64_t carry2;
    int64_t carry3;
    int64_t carry4;
    int64_t carry5;
    int64_t carry6;
    int64_t carry7;
    int64_t carry8;
    int64_t carry9;

    carry0 = (h0 + (int64_t) (1<<25)) >> 26; h1 += carry0; h0 -= carry0 << 26;
    carry4 = (h4 + (int64_t) (1<<25)) >> 26; h5 += carry4; h4 -= carry4 << 26;

    carry1 = (h1 + (int64_t) (1<<24)) >> 25; h2 += carry1; h1 -= carry1 << 25;
    carry5 = (h5 + (int64_t) (1<<24)) >> 25; h6 += carry5; h5 -= carry5 << 25;

    carry2 = (h2 + (int64_t) (1<<25)) >> 26; h3 += carry2; h2 -= carry2 << 26;
    carry6 = (h6 + (int64_t) (1<<25)) >> 26; h7 += carry6; h6 -= carry6 << 26;

    carry3 = (h3 + (int64_t) (1<<24)) >> 25; h4 += carry3; h3 -= carry3 << 25;
    carry7 = (h7 + (int64_t) (1<<24)) >> 25; h8 += carry7; h7 -= carry7 << 25;

    carry4 = (h4 + (int64_t) (1<<25)) >> 26; h5 += carry4; h4 -= carry4 << 26;
    carry8 = (h8 + (int64_t) (1<<25)) >> 26; h9 += carry8; h8 -= carry8 << 26;

    carry9 = (h9 + (int64_t) (1<<24)) >> 25; h0 += carry9 * 19; h9 -= carry9 << 25;

    carry0 = (h0 + (int64_t) (1<<25)) >> 26; h1 += carry0; h0 -= carry0 << 26;

    h[0] = h0;
    h[1] = h1;
    h[2] = h2;
    h[3] = h3;
    h[4] = h4;
    h[5] = h5;
    h[6] = h6;
    h[7] = h7;
    h[8] = h8;
    h[9] = h9;
}
#else 
void fe_sq(fe h, const fe f) {

	int8 v1=vload8(0, (int *)f);

	int32_t f8 = f[8];
	int32_t f9 = f[9];
	
	//double first 8
	int8 v2 = v1 << 1;

	int32_t f5_38 = 38 * v1.s5; /* 1.31*2^30 */
	int32_t f6_19 = 19 * v1.s6; /* 1.31*2^30 */
	int32_t f7_38 = 38 * v1.s7; /* 1.31*2^30 */
	int32_t f8_19 = 19 * f8; /* 1.31*2^30 */
	int32_t f9_38 = 38 * f9; /* 1.31*2^30 */

	long16 a1 = (long16)(v1.s0,v1.s1,	 v1.s2,  v1.s3,  v1.s4,  v1.s5,  v1.s6,  v1.s7,  f8,  f9,  v1.s1,  v1.s2,  v2.s3,v1.s4,  v2.s5,v1.s6);
	long16 a2 = (long16)(v1.s0,v2.s0,v2.s0,v2.s0,v2.s0,v2.s0,v2.s0,v2.s0,v2.s0,v2.s0,v2.s1,v2.s1,v2.s1,v2.s1,v2.s1,v2.s1);
	long16 r1 = a1*a2;

	a1.s0 = f9_38;
	a1.s1 = v2.s7;
	a1.sa = f8_19;
	
	a2 = (long16)(v2.s1, v2.s1,v1.s2, v2.s2, v2.s2, v2.s2, v2.s2, v2.s2, v2.s1, f9_38, v2.s2, f9_38, v1.s3, v2.s3, f6_19, v2.s3);
	long16 r2 = a1*a2;

	a1.s0 = 0;
	a1.s1 = f9_38;
	a1.s2 = f9_38;
	a1.s3 = f5_38;
	a1.s9 = f6_19;
	a1.sa = f8_19;
	a1.sb = f7_38;
	a2 = (long16)(0,v2.s3, v2.s7, v1.s5, v1.s4, v2.s4, f6_19, f7_38, f8_19, v2.s4, v2.s7, v2.s3, v2.s5, f7_38, f7_38, f7_38);
	long16 r3 = a1*a2;

	long8 a3 = (long8)(v2.s3, v2.s4, v2.s5, v2.s6, v1.s4, v2.s5, v1.s6, f8);
	long8 a4 = (long8)(f8_19, f8_19, f8_19, f8_19, f9_38, f9_38, f9_38, f9_38);
	long8 r4 = a3*a4;

	int64_t h0 = r1.s0 + r2.s0 + r2.sa + r3.sb + r3.s9 + r3.s3;
	int64_t h1 = r1.s1 + r2.sb + r4.s0 + r3.sd + r2.se;
	int64_t h2 = r1.s2 + r1.sa + r3.s1 + r4.s1 + r3.se + r3.s6;
	int64_t h3 = r1.s3 + r1.sb + r4.s4 + r4.s2 + r3.sf;
	int64_t h4 = r1.s4 + r1.sc + r2.s2 + r4.s5 + r4.s3 + r3.s7;
	int64_t h5 = r1.s5 + r1.sd + r2.s3 + r4.s6 + r3.sa;
	int64_t h6 = r1.s6 + r1.se + r2.s4 + r2.sc + r3.s2 + r3.s8;
	int64_t h7 = r1.s7 + r1.sf + r2.s5 + r2.sd + r4.s7;
	int64_t h8 = r1.s8 + r2.s1 + r2.s6 + r3.sc + r3.s4 + r2.s9;
	int64_t h9 = r1.s9 + r2.s8 + r2.s7 + r2.sf + r3.s5;

	int64_t carry0 = (h0 + POW2_25) >> 26; h1 += carry0; h0 -= carry0 << 26;
	int64_t carry4 = (h4 + POW2_25) >> 26; h5 += carry4; h4 -= carry4 << 26;

	int64_t carry1 = (h1 + POW2_24) >> 25; h2 += carry1; h1 -= carry1 << 25;
	int64_t carry5 = (h5 + POW2_24) >> 25; h6 += carry5; h5 -= carry5 << 25;

	int64_t carry2 = (h2 + POW2_25) >> 26; h3 += carry2; h[2] =  h2 - (carry2 << 26);
	int64_t carry6 = (h6 + POW2_25) >> 26; h7 += carry6; h[6] =  h6 - (carry6 << 26);

	int64_t carry3 = (h3 + POW2_24) >> 25; h4 += carry3; h[3] =  h3 - (carry3 << 25);
	int64_t carry7 = (h7 + POW2_24) >> 25; h8 += carry7; h[7] =  h7 - (carry7 << 25);

	carry4 = (h4 + POW2_25) >> 26; h[5] = h5 + carry4; h[4] =  h4 - (carry4 << 26);
	int64_t carry8 = (h8 + POW2_25) >> 26; h9 += carry8; h[8] =	h8 - (carry8 << 26);

	int64_t carry9 = (h9 + POW2_24) >> 25; h0 += carry9 * 19; h[9] =	h9 - (carry9 << 25);
	carry0 = (h0 + POW2_25) >> 26; h[1]= h1 + carry0; h[0] =	h0 - (carry0 << 26);
}

#endif
void fe_sq2(fe h, const fe f) {
    int32_t f0 = f[0];
    int32_t f1 = f[1];
    int32_t f2 = f[2];
    int32_t f3 = f[3];
    int32_t f4 = f[4];
    int32_t f5 = f[5];
    int32_t f6 = f[6];
    int32_t f7 = f[7];
    int32_t f8 = f[8];
    int32_t f9 = f[9];
    int32_t f0_2 = f0 <<1;
    int32_t f1_2 = f1 <<1;
    int32_t f2_2 = f2 <<1;
    int32_t f3_2 = f3 <<1;
    int32_t f4_2 = f4 <<1;
    int32_t f5_2 = f5 <<1;
    int32_t f6_2 = f6 <<1;
    int32_t f7_2 = f7 <<1;
    int32_t f5_38 = 38 * f5; /* 1.959375*2^30 */
    int32_t f6_19 = 19 * f6; /* 1.959375*2^30 */
    int32_t f7_38 = 38 * f7; /* 1.959375*2^30 */
    int32_t f8_19 = 19 * f8; /* 1.959375*2^30 */
    int32_t f9_38 = 38 * f9; /* 1.959375*2^30 */
    int64_t f0f0    = f0   * (int64_t) f0;
    int64_t f0f1_2  = f0_2 * (int64_t) f1;
    int64_t f0f2_2  = f0_2 * (int64_t) f2;
    int64_t f0f3_2  = f0_2 * (int64_t) f3;
    int64_t f0f4_2  = f0_2 * (int64_t) f4;
    int64_t f0f5_2  = f0_2 * (int64_t) f5;
    int64_t f0f6_2  = f0_2 * (int64_t) f6;
    int64_t f0f7_2  = f0_2 * (int64_t) f7;
    int64_t f0f8_2  = f0_2 * (int64_t) f8;
    int64_t f0f9_2  = f0_2 * (int64_t) f9;
    int64_t f1f1_2  = f1_2 * (int64_t) f1;
    int64_t f1f2_2  = f1_2 * (int64_t) f2;
    int64_t f1f3_4  = f1_2 * (int64_t) f3_2;
    int64_t f1f4_2  = f1_2 * (int64_t) f4;
    int64_t f1f5_4  = f1_2 * (int64_t) f5_2;
    int64_t f1f6_2  = f1_2 * (int64_t) f6;
    int64_t f1f7_4  = f1_2 * (int64_t) f7_2;
    int64_t f1f8_2  = f1_2 * (int64_t) f8;
    int64_t f1f9_76 = f1_2 * (int64_t) f9_38;
    int64_t f2f2    = f2   * (int64_t) f2;
    int64_t f2f3_2  = f2_2 * (int64_t) f3;
    int64_t f2f4_2  = f2_2 * (int64_t) f4;
    int64_t f2f5_2  = f2_2 * (int64_t) f5;
    int64_t f2f6_2  = f2_2 * (int64_t) f6;
    int64_t f2f7_2  = f2_2 * (int64_t) f7;
    int64_t f2f8_38 = f2_2 * (int64_t) f8_19;
    int64_t f2f9_38 = f2   * (int64_t) f9_38;
    int64_t f3f3_2  = f3_2 * (int64_t) f3;
    int64_t f3f4_2  = f3_2 * (int64_t) f4;
    int64_t f3f5_4  = f3_2 * (int64_t) f5_2;
    int64_t f3f6_2  = f3_2 * (int64_t) f6;
    int64_t f3f7_76 = f3_2 * (int64_t) f7_38;
    int64_t f3f8_38 = f3_2 * (int64_t) f8_19;
    int64_t f3f9_76 = f3_2 * (int64_t) f9_38;
    int64_t f4f4    = f4   * (int64_t) f4;
    int64_t f4f5_2  = f4_2 * (int64_t) f5;
    int64_t f4f6_38 = f4_2 * (int64_t) f6_19;
    int64_t f4f7_38 = f4   * (int64_t) f7_38;
    int64_t f4f8_38 = f4_2 * (int64_t) f8_19;
    int64_t f4f9_38 = f4   * (int64_t) f9_38;
    int64_t f5f5_38 = f5   * (int64_t) f5_38;
    int64_t f5f6_38 = f5_2 * (int64_t) f6_19;
    int64_t f5f7_76 = f5_2 * (int64_t) f7_38;
    int64_t f5f8_38 = f5_2 * (int64_t) f8_19;
    int64_t f5f9_76 = f5_2 * (int64_t) f9_38;
    int64_t f6f6_19 = f6   * (int64_t) f6_19;
    int64_t f6f7_38 = f6   * (int64_t) f7_38;
    int64_t f6f8_38 = f6_2 * (int64_t) f8_19;
    int64_t f6f9_38 = f6   * (int64_t) f9_38;
    int64_t f7f7_38 = f7   * (int64_t) f7_38;
    int64_t f7f8_38 = f7_2 * (int64_t) f8_19;
    int64_t f7f9_76 = f7_2 * (int64_t) f9_38;
    int64_t f8f8_19 = f8   * (int64_t) f8_19;
    int64_t f8f9_38 = f8   * (int64_t) f9_38;
    int64_t f9f9_38 = f9   * (int64_t) f9_38;
    int64_t h0 = f0f0  +f1f9_76+f2f8_38+f3f7_76+f4f6_38+f5f5_38;
    int64_t h1 = f0f1_2+f2f9_38+f3f8_38+f4f7_38+f5f6_38;
    int64_t h2 = f0f2_2+f1f1_2 +f3f9_76+f4f8_38+f5f7_76+f6f6_19;
    int64_t h3 = f0f3_2+f1f2_2 +f4f9_38+f5f8_38+f6f7_38;
    int64_t h4 = f0f4_2+f1f3_4 +f2f2   +f5f9_76+f6f8_38+f7f7_38;
    int64_t h5 = f0f5_2+f1f4_2 +f2f3_2 +f6f9_38+f7f8_38;
    int64_t h6 = f0f6_2+f1f5_4 +f2f4_2 +f3f3_2 +f7f9_76+f8f8_19;
    int64_t h7 = f0f7_2+f1f6_2 +f2f5_2 +f3f4_2 +f8f9_38;
    int64_t h8 = f0f8_2+f1f7_4 +f2f6_2 +f3f5_4 +f4f4   +f9f9_38;
    int64_t h9 = f0f9_2+f1f8_2 +f2f7_2 +f3f6_2 +f4f5_2;
    int64_t carry0;
    int64_t carry1;
    int64_t carry2;
    int64_t carry3;
    int64_t carry4;
    int64_t carry5;
    int64_t carry6;
    int64_t carry7;
    int64_t carry8;
    int64_t carry9;

    h0 <<= 1;
    h1 <<= 1;
    h2 <<= 1;
    h3 <<= 1;
    h4 <<= 1;
    h5 <<= 1;
    h6 <<= 1;
    h7 <<= 1;
    h8 <<= 1;
    h9 <<= 1;

    carry0 = (h0 + (int64_t) (1<<25)) >> 26; h1 += carry0; h0 -= carry0 << 26;
    carry4 = (h4 + (int64_t) (1<<25)) >> 26; h5 += carry4; h4 -= carry4 << 26;

    carry1 = (h1 + (int64_t) (1<<24)) >> 25; h2 += carry1; h1 -= carry1 << 25;
    carry5 = (h5 + (int64_t) (1<<24)) >> 25; h6 += carry5; h5 -= carry5 << 25;

    carry2 = (h2 + (int64_t) (1<<25)) >> 26; h3 += carry2; h2 -= carry2 << 26;
    carry6 = (h6 + (int64_t) (1<<25)) >> 26; h7 += carry6; h6 -= carry6 << 26;

    carry3 = (h3 + (int64_t) (1<<24)) >> 25; h4 += carry3; h3 -= carry3 << 25;
    carry7 = (h7 + (int64_t) (1<<24)) >> 25; h8 += carry7; h7 -= carry7 << 25;

    carry4 = (h4 + (int64_t) (1<<25)) >> 26; h5 += carry4; h4 -= carry4 << 26;
    carry8 = (h8 + (int64_t) (1<<25)) >> 26; h9 += carry8; h8 -= carry8 << 26;

    carry9 = (h9 + (int64_t) (1<<24)) >> 25; h0 += carry9 * 19; h9 -= carry9 << 25;

    carry0 = (h0 + (int64_t) (1<<25)) >> 26; h1 += carry0; h0 -= carry0 << 26;

    h[0] = (int32_t) h0;
    h[1] = (int32_t) h1;
    h[2] = (int32_t) h2;
    h[3] = (int32_t) h3;
    h[4] = (int32_t) h4;
    h[5] = (int32_t) h5;
    h[6] = (int32_t) h6;
    h[7] = (int32_t) h7;
    h[8] = (int32_t) h8;
    h[9] = (int32_t) h9;
}

void fe_mul(fe h, const fe f, const fe g) {
    int32_t f0 = f[0];
    int32_t f1 = f[1];
    int32_t f2 = f[2];
    int32_t f3 = f[3];
    int32_t f4 = f[4];
    int32_t f5 = f[5];
    int32_t f6 = f[6];
    int32_t f7 = f[7];
    int32_t f8 = f[8];
    int32_t f9 = f[9];
    int32_t g0 = g[0];
    int32_t g1 = g[1];
    int32_t g2 = g[2];
    int32_t g3 = g[3];
    int32_t g4 = g[4];
    int32_t g5 = g[5];
    int32_t g6 = g[6];
    int32_t g7 = g[7];
    int32_t g8 = g[8];
    int32_t g9 = g[9];
    int32_t g1_19 = 19 * g1; /* 1.959375*2^29 */
    int32_t g2_19 = 19 * g2; /* 1.959375*2^30; still ok */
    int32_t g3_19 = 19 * g3;
    int32_t g4_19 = 19 * g4;
    int32_t g5_19 = 19 * g5;
    int32_t g6_19 = 19 * g6;
    int32_t g7_19 = 19 * g7;
    int32_t g8_19 = 19 * g8;
    int32_t g9_19 = 19 * g9;
    int32_t f1_2 = f1<<1;
    int32_t f3_2 = f3<<1;
    int32_t f5_2 = f5<<1;
    int32_t f7_2 = f7<<1;
    int32_t f9_2 = f9<<1;
    int64_t f0g0    = f0   * (int64_t) g0;
    int64_t f0g1    = f0   * (int64_t) g1;
    int64_t f0g2    = f0   * (int64_t) g2;
    int64_t f0g3    = f0   * (int64_t) g3;
    int64_t f0g4    = f0   * (int64_t) g4;
    int64_t f0g5    = f0   * (int64_t) g5;
    int64_t f0g6    = f0   * (int64_t) g6;
    int64_t f0g7    = f0   * (int64_t) g7;
    int64_t f0g8    = f0   * (int64_t) g8;
    int64_t f0g9    = f0   * (int64_t) g9;
    int64_t f1g0    = f1   * (int64_t) g0;
    int64_t f1g1_2  = f1_2 * (int64_t) g1;
    int64_t f1g2    = f1   * (int64_t) g2;
    int64_t f1g3_2  = f1_2 * (int64_t) g3;
    int64_t f1g4    = f1   * (int64_t) g4;
    int64_t f1g5_2  = f1_2 * (int64_t) g5;
    int64_t f1g6    = f1   * (int64_t) g6;
    int64_t f1g7_2  = f1_2 * (int64_t) g7;
    int64_t f1g8    = f1   * (int64_t) g8;
    int64_t f1g9_38 = f1_2 * (int64_t) g9_19;
    int64_t f2g0    = f2   * (int64_t) g0;
    int64_t f2g1    = f2   * (int64_t) g1;
    int64_t f2g2    = f2   * (int64_t) g2;
    int64_t f2g3    = f2   * (int64_t) g3;
    int64_t f2g4    = f2   * (int64_t) g4;
    int64_t f2g5    = f2   * (int64_t) g5;
    int64_t f2g6    = f2   * (int64_t) g6;
    int64_t f2g7    = f2   * (int64_t) g7;
    int64_t f2g8_19 = f2   * (int64_t) g8_19;
    int64_t f2g9_19 = f2   * (int64_t) g9_19;
    int64_t f3g0    = f3   * (int64_t) g0;
    int64_t f3g1_2  = f3_2 * (int64_t) g1;
    int64_t f3g2    = f3   * (int64_t) g2;
    int64_t f3g3_2  = f3_2 * (int64_t) g3;
    int64_t f3g4    = f3   * (int64_t) g4;
    int64_t f3g5_2  = f3_2 * (int64_t) g5;
    int64_t f3g6    = f3   * (int64_t) g6;
    int64_t f3g7_38 = f3_2 * (int64_t) g7_19;
    int64_t f3g8_19 = f3   * (int64_t) g8_19;
    int64_t f3g9_38 = f3_2 * (int64_t) g9_19;
    int64_t f4g0    = f4   * (int64_t) g0;
    int64_t f4g1    = f4   * (int64_t) g1;
    int64_t f4g2    = f4   * (int64_t) g2;
    int64_t f4g3    = f4   * (int64_t) g3;
    int64_t f4g4    = f4   * (int64_t) g4;
    int64_t f4g5    = f4   * (int64_t) g5;
    int64_t f4g6_19 = f4   * (int64_t) g6_19;
    int64_t f4g7_19 = f4   * (int64_t) g7_19;
    int64_t f4g8_19 = f4   * (int64_t) g8_19;
    int64_t f4g9_19 = f4   * (int64_t) g9_19;
    int64_t f5g0    = f5   * (int64_t) g0;
    int64_t f5g1_2  = f5_2 * (int64_t) g1;
    int64_t f5g2    = f5   * (int64_t) g2;
    int64_t f5g3_2  = f5_2 * (int64_t) g3;
    int64_t f5g4    = f5   * (int64_t) g4;
    int64_t f5g5_38 = f5_2 * (int64_t) g5_19;
    int64_t f5g6_19 = f5   * (int64_t) g6_19;
    int64_t f5g7_38 = f5_2 * (int64_t) g7_19;
    int64_t f5g8_19 = f5   * (int64_t) g8_19;
    int64_t f5g9_38 = f5_2 * (int64_t) g9_19;
    int64_t f6g0    = f6   * (int64_t) g0;
    int64_t f6g1    = f6   * (int64_t) g1;
    int64_t f6g2    = f6   * (int64_t) g2;
    int64_t f6g3    = f6   * (int64_t) g3;
    int64_t f6g4_19 = f6   * (int64_t) g4_19;
    int64_t f6g5_19 = f6   * (int64_t) g5_19;
    int64_t f6g6_19 = f6   * (int64_t) g6_19;
    int64_t f6g7_19 = f6   * (int64_t) g7_19;
    int64_t f6g8_19 = f6   * (int64_t) g8_19;
    int64_t f6g9_19 = f6   * (int64_t) g9_19;
    int64_t f7g0    = f7   * (int64_t) g0;
    int64_t f7g1_2  = f7_2 * (int64_t) g1;
    int64_t f7g2    = f7   * (int64_t) g2;
    int64_t f7g3_38 = f7_2 * (int64_t) g3_19;
    int64_t f7g4_19 = f7   * (int64_t) g4_19;
    int64_t f7g5_38 = f7_2 * (int64_t) g5_19;
    int64_t f7g6_19 = f7   * (int64_t) g6_19;
    int64_t f7g7_38 = f7_2 * (int64_t) g7_19;
    int64_t f7g8_19 = f7   * (int64_t) g8_19;
    int64_t f7g9_38 = f7_2 * (int64_t) g9_19;
    int64_t f8g0    = f8   * (int64_t) g0;
    int64_t f8g1    = f8   * (int64_t) g1;
    int64_t f8g2_19 = f8   * (int64_t) g2_19;
    int64_t f8g3_19 = f8   * (int64_t) g3_19;
    int64_t f8g4_19 = f8   * (int64_t) g4_19;
    int64_t f8g5_19 = f8   * (int64_t) g5_19;
    int64_t f8g6_19 = f8   * (int64_t) g6_19;
    int64_t f8g7_19 = f8   * (int64_t) g7_19;
    int64_t f8g8_19 = f8   * (int64_t) g8_19;
    int64_t f8g9_19 = f8   * (int64_t) g9_19;
    int64_t f9g0    = f9   * (int64_t) g0;
    int64_t f9g1_38 = f9_2 * (int64_t) g1_19;
    int64_t f9g2_19 = f9   * (int64_t) g2_19;
    int64_t f9g3_38 = f9_2 * (int64_t) g3_19;
    int64_t f9g4_19 = f9   * (int64_t) g4_19;
    int64_t f9g5_38 = f9_2 * (int64_t) g5_19;
    int64_t f9g6_19 = f9   * (int64_t) g6_19;
    int64_t f9g7_38 = f9_2 * (int64_t) g7_19;
    int64_t f9g8_19 = f9   * (int64_t) g8_19;
    int64_t f9g9_38 = f9_2 * (int64_t) g9_19;
    int64_t h0 = f0g0+f1g9_38+f2g8_19+f3g7_38+f4g6_19+f5g5_38+f6g4_19+f7g3_38+f8g2_19+f9g1_38;
    int64_t h1 = f0g1+f1g0   +f2g9_19+f3g8_19+f4g7_19+f5g6_19+f6g5_19+f7g4_19+f8g3_19+f9g2_19;
    int64_t h2 = f0g2+f1g1_2 +f2g0   +f3g9_38+f4g8_19+f5g7_38+f6g6_19+f7g5_38+f8g4_19+f9g3_38;
    int64_t h3 = f0g3+f1g2   +f2g1   +f3g0   +f4g9_19+f5g8_19+f6g7_19+f7g6_19+f8g5_19+f9g4_19;
    int64_t h4 = f0g4+f1g3_2 +f2g2   +f3g1_2 +f4g0   +f5g9_38+f6g8_19+f7g7_38+f8g6_19+f9g5_38;
    int64_t h5 = f0g5+f1g4   +f2g3   +f3g2   +f4g1   +f5g0   +f6g9_19+f7g8_19+f8g7_19+f9g6_19;
    int64_t h6 = f0g6+f1g5_2 +f2g4   +f3g3_2 +f4g2   +f5g1_2 +f6g0   +f7g9_38+f8g8_19+f9g7_38;
    int64_t h7 = f0g7+f1g6   +f2g5   +f3g4   +f4g3   +f5g2   +f6g1   +f7g0   +f8g9_19+f9g8_19;
    int64_t h8 = f0g8+f1g7_2 +f2g6   +f3g5_2 +f4g4   +f5g3_2 +f6g2   +f7g1_2 +f8g0   +f9g9_38;
    int64_t h9 = f0g9+f1g8   +f2g7   +f3g6   +f4g5   +f5g4   +f6g3   +f7g2   +f8g1   +f9g0   ;
    int64_t carry0;
    int64_t carry1;
    int64_t carry2;
    int64_t carry3;
    int64_t carry4;
    int64_t carry5;
    int64_t carry6;
    int64_t carry7;
    int64_t carry8;
    int64_t carry9;

    /*
    |h0| <= (1.65*1.65*2^52*(1+19+19+19+19)+1.65*1.65*2^50*(38+38+38+38+38))
      i.e. |h0| <= 1.4*2^60; narrower ranges for h2, h4, h6, h8
    |h1| <= (1.65*1.65*2^51*(1+1+19+19+19+19+19+19+19+19))
      i.e. |h1| <= 1.7*2^59; narrower ranges for h3, h5, h7, h9
    */

    carry0 = (h0 + (int64_t) (1<<25)) >> 26; h1 += carry0; h0 -= carry0 << 26;
    carry4 = (h4 + (int64_t) (1<<25)) >> 26; h5 += carry4; h4 -= carry4 << 26;
    /* |h0| <= 2^25 */
    /* |h4| <= 2^25 */
    /* |h1| <= 1.71*2^59 */
    /* |h5| <= 1.71*2^59 */

    carry1 = (h1 + (int64_t) (1<<24)) >> 25; h2 += carry1; h1 -= carry1 << 25;
    carry5 = (h5 + (int64_t) (1<<24)) >> 25; h6 += carry5; h5 -= carry5 << 25;
    /* |h1| <= 2^24; from now on fits into int32 */
    /* |h5| <= 2^24; from now on fits into int32 */
    /* |h2| <= 1.41*2^60 */
    /* |h6| <= 1.41*2^60 */

    carry2 = (h2 + (int64_t) (1<<25)) >> 26; h3 += carry2; h2 -= carry2 << 26;
    carry6 = (h6 + (int64_t) (1<<25)) >> 26; h7 += carry6; h6 -= carry6 << 26;
    /* |h2| <= 2^25; from now on fits into int32 unchanged */
    /* |h6| <= 2^25; from now on fits into int32 unchanged */
    /* |h3| <= 1.71*2^59 */
    /* |h7| <= 1.71*2^59 */

    carry3 = (h3 + (int64_t) (1<<24)) >> 25; h4 += carry3; h3 -= carry3 << 25;
    carry7 = (h7 + (int64_t) (1<<24)) >> 25; h8 += carry7; h7 -= carry7 << 25;
    /* |h3| <= 2^24; from now on fits into int32 unchanged */
    /* |h7| <= 2^24; from now on fits into int32 unchanged */
    /* |h4| <= 1.72*2^34 */
    /* |h8| <= 1.41*2^60 */

    carry4 = (h4 + (int64_t) (1<<25)) >> 26; h5 += carry4; h4 -= carry4 << 26;
    carry8 = (h8 + (int64_t) (1<<25)) >> 26; h9 += carry8; h8 -= carry8 << 26;
    /* |h4| <= 2^25; from now on fits into int32 unchanged */
    /* |h8| <= 2^25; from now on fits into int32 unchanged */
    /* |h5| <= 1.01*2^24 */
    /* |h9| <= 1.71*2^59 */

    carry9 = (h9 + (int64_t) (1<<24)) >> 25; h0 += carry9 * 19; h9 -= carry9 << 25;
    /* |h9| <= 2^24; from now on fits into int32 unchanged */
    /* |h0| <= 1.1*2^39 */

    carry0 = (h0 + (int64_t) (1<<25)) >> 26; h1 += carry0; h0 -= carry0 << 26;
    /* |h0| <= 2^25; from now on fits into int32 unchanged */
    /* |h1| <= 1.01*2^24 */

    h[0] = (int32_t) h0;
    h[1] = (int32_t) h1;
    h[2] = (int32_t) h2;
    h[3] = (int32_t) h3;
    h[4] = (int32_t) h4;
    h[5] = (int32_t) h5;
    h[6] = (int32_t) h6;
    h[7] = (int32_t) h7;
    h[8] = (int32_t) h8;
    h[9] = (int32_t) h9;
}

void fe_sub(fe h, const fe f, const fe g) {
#pragma unroll
	for (int i = 0; i < 10; i++)
		h[i] = f[i] - g[i];
}

void fe_pow22523(fe out, const fe z) {
    fe t0;
    fe t1;
    fe t2;
    int i;

    fe_sq(t0,z);
    fe_sq(t1,t0); for (i = 1;i < 2;++i) fe_sq(t1,t1);
    fe_mul(t1,z,t1);
    fe_mul(t0,t0,t1);
    fe_sq(t0,t0);
    fe_mul(t0,t1,t0);
    fe_sq(t1,t0); for (i = 1;i < 5;++i) fe_sq(t1,t1);
    fe_mul(t0,t1,t0);
    fe_sq(t1,t0); for (i = 1;i < 10;++i) fe_sq(t1,t1);
    fe_mul(t1,t1,t0);
    fe_sq(t2,t1); for (i = 1;i < 20;++i) fe_sq(t2,t2);
    fe_mul(t1,t2,t1);
    fe_sq(t1,t1); for (i = 1;i < 10;++i) fe_sq(t1,t1);
    fe_mul(t0,t1,t0);
    fe_sq(t1,t0); for (i = 1;i < 50;++i) fe_sq(t1,t1);
    fe_mul(t1,t1,t0);
    fe_sq(t2,t1); for (i = 1;i < 100;++i) fe_sq(t2,t2);
    fe_mul(t1,t2,t1);
    fe_sq(t1,t1); for (i = 1;i < 50;++i) fe_sq(t1,t1);
    fe_mul(t0,t1,t0);
    fe_sq(t0,t0); for (i = 1;i < 2;++i) fe_sq(t0,t0);
    fe_mul(out,t0,z);
}

void fe_invert(fe out,const fe z) {
    fe t0;
    fe t1;
    fe t2;
    fe t3;
    int i;

    fe_sq(t0,z);
    fe_sq(t1,t0); for (i = 1;i < 2;++i) fe_sq(t1,t1);
    fe_mul(t1,z,t1);
    fe_mul(t0,t0,t1);
    fe_sq(t2,t0);
    fe_mul(t1,t1,t2);
    fe_sq(t2,t1); for (i = 1;i < 5;++i) fe_sq(t2,t2);
    fe_mul(t1,t2,t1);
    fe_sq(t2,t1); for (i = 1;i < 10;++i) fe_sq(t2,t2);
    fe_mul(t2,t2,t1);
    fe_sq(t3,t2); for (i = 1;i < 20;++i) fe_sq(t3,t3);
    fe_mul(t2,t3,t2);
    fe_sq(t2,t2); for (i = 1;i < 10;++i) fe_sq(t2,t2);
    fe_mul(t1,t2,t1);
    fe_sq(t2,t1); for (i = 1;i < 50;++i) fe_sq(t2,t2);
    fe_mul(t2,t2,t1);
    fe_sq(t3,t2); for (i = 1;i < 100;++i) fe_sq(t3,t3);
    fe_mul(t2,t3,t2);
    fe_sq(t2,t2); for (i = 1;i < 50;++i) fe_sq(t2,t2);
    fe_mul(t1,t2,t1);
    fe_sq(t1,t1); for (i = 1;i < 5;++i) fe_sq(t1,t1);
    fe_mul(out,t1,t0);
}

/* static unsigned char zero[32]; */

int fe_isnonzero(const fe f) {
    unsigned char s[32] = {0};
    unsigned char zero[32] = { 0};   /* Ajay */
    fe_tobytes(s,f);
    return verify_32(s,zero);
}

int fe_isnegative(const fe f) {
    unsigned char s[32];
    fe_tobytes(s,f);
    return s[0] & 1;
}

void fe_neg(fe h, const fe f) {
#pragma unroll
	for (int i = 0;i<10;i++)
	{ 
		h[i] = -f[i];
	}
}

/* Ajay */
/*
static const fe d2 = {
    -21827239,-5839606,-30745221,13898782,229458,15978800,-12551817,-6495438,29715968,9444199
};
*/

void ge_p3_to_cached(ge_cached *r, const ge_p3 *p) {
    /* Ajay */
    const fe d2 = {
    -21827239,-5839606,-30745221,13898782,229458,15978800,-12551817,-6495438,29715968,9444199
};
    fe_add(r->YplusX,p->Y,p->X);
    fe_sub(r->YminusX,p->Y,p->X);
    fe_copy(r->Z,p->Z);
    fe_mul(r->T2d,p->T,d2);
}

void ge_p3_dbl(ge_p1p1 *r, const ge_p3 *p) {
    ge_p2 q;
    ge_p3_to_p2(&q,p);
    ge_p2_dbl(r,&q);
}

void ge_p1p1_to_p3(ge_p3 *r,const ge_p1p1 *p) {
    fe_mul(r->X,p->X,p->T);
    fe_mul(r->Y,p->Y,p->Z);
    fe_mul(r->Z,p->Z,p->T);
    fe_mul(r->T,p->X,p->Y);
}

void ge_add(ge_p1p1 *r, const ge_p3 *p, const ge_cached *q) {
    fe t0;
    fe_add(r->X,p->Y,p->X);
    fe_sub(r->Y,p->Y,p->X);
    fe_mul(r->Z,r->X,q->YplusX);
    fe_mul(r->Y,r->Y,q->YminusX);
    fe_mul(r->T,q->T2d,p->T);
    fe_mul(r->X,p->Z,q->Z);
    fe_add(t0,r->X,r->X);
    fe_sub(r->X,r->Z,r->Y);
    fe_add(r->Y,r->Z,r->Y);
    fe_add(r->Z,t0,r->T);
    fe_sub(r->T,t0,r->T);
}

void ge_p2_0(ge_p2 *h) {
    fe_0(h->X);
    fe_1(h->Y);
    fe_1(h->Z);
}

void ge_p2_dbl(ge_p1p1 *r, const ge_p2 *p) {
    fe t0;
    fe_sq(r->X,p->X);
    fe_sq(r->Z,p->Y);
    fe_sq2(r->T,p->Z);
    fe_add(r->Y,p->X,p->Y);
    fe_sq(t0,r->Y);
    fe_add(r->Y,r->Z,r->X);
    fe_sub(r->Z,r->Z,r->X);
    fe_sub(r->X,t0,r->Y);
    fe_sub(r->T,r->T,r->Z);
}

void ge_sub(ge_p1p1 *r,const ge_p3 *p,const ge_cached *q) {
    fe t0;
    fe_add(r->X,p->Y,p->X);
    fe_sub(r->Y,p->Y,p->X);
    fe_mul(r->Z,r->X,q->YminusX);
    fe_mul(r->Y,r->Y,q->YplusX);
    fe_mul(r->T,q->T2d,p->T);
    fe_mul(r->X,p->Z,q->Z);
    fe_add(t0,r->X,r->X);
    fe_sub(r->X,r->Z,r->Y);
    fe_add(r->Y,r->Z,r->Y);
    fe_sub(r->Z,t0,r->T);
    fe_add(r->T,t0,r->T);
}

void ge_madd(ge_p1p1 *r, const ge_p3 *p, const ge_precomp *q) {
    fe t0;
    fe_add(r->X,p->Y,p->X);
    fe_sub(r->Y,p->Y,p->X);
    fe_mul(r->Z,r->X,q->yplusx);
    fe_mul(r->Y,r->Y,q->yminusx);
    fe_mul(r->T,q->xy2d,p->T);
    fe_add(t0,p->Z,p->Z);
    fe_sub(r->X,r->Z,r->Y);
    fe_add(r->Y,r->Z,r->Y);
    fe_add(r->Z,t0,r->T);
    fe_sub(r->T,t0,r->T);
}

void ge_msub(ge_p1p1 *r, const ge_p3 *p, const ge_precomp *q) {
    fe t0;
    fe_add(r->X,p->Y,p->X);
    fe_sub(r->Y,p->Y,p->X);
    fe_mul(r->Z,r->X,q->yminusx);
    fe_mul(r->Y,r->Y,q->yplusx);
    fe_mul(r->T,q->xy2d,p->T);
    fe_add(t0,p->Z,p->Z);
    fe_sub(r->X,r->Z,r->Y);
    fe_add(r->Y,r->Z,r->Y);
    fe_sub(r->Z,t0,r->T);
    fe_add(r->T,t0,r->T);
}

void ge_p1p1_to_p2(ge_p2 *r, const ge_p1p1 *p) {
    fe_mul(r->X,p->X,p->T);
    fe_mul(r->Y,p->Y,p->Z);
    fe_mul(r->Z,p->Z,p->T);
}

void ge_p3_to_p2(ge_p2 *r, const ge_p3 *p) {
    fe_copy(r->X,p->X);
    fe_copy(r->Y,p->Y);
    fe_copy(r->Z,p->Z);
}

/*structure of task buffer 1
L0...L31,S32...S63
L0...L31 carries the message length
S32...S63 is last 32 bytes of the signature

*/
/* structure of task buffer2
S0...S31,P0...P31,M0...M127...

S0-31 is first 31 bytes of sig
P0-31 is the PK
M0...M127... is message 
The task buffer is up rounded to 128 bytes. 

*/

#define B1(x) x x
#define B2(x) B1(x) B1(x)
#define B3(x) B2(x) B2(x)
#define B4(x) B3(x) B3(x)
#define B5(x) B4(x) B4(x)
#define B6(x) B5(x) B5(x)
#define B7(x) B6(x) B6(x)

#define CAT2(A,B) A B
#define CAT3(A,B,C) A B C
#define CAT4(A,B,C,D) A B C D

#define V1(x,n) x[n-2] , x[n-1]
#define V2(x,n) V1(x,n-2) , V1(x,n)
#define V3(x,n) V2(x,n-4) , V2(x,n)
#define V4(x,n) V3(x,n-8) , V3(x,n)
#define V5(x,n) V4(x,n-16) , V4(x,n)
#define V6(x,n) V5(x,n-32) , V5(x,n)

#define ZS(x,n,l) (n<l?(x[n]):0)
#define VZ1(x,n,l) ZS(x,n-2,l)   , ZS(x,n-1,l)
#define VZ2(x,n,l) VZ1(x,n-2,l)  , VZ1(x,n,l)
#define VZ3(x,n,l) VZ2(x,n-4,l)  , VZ2(x,n,l)
#define VZ4(x,n,l) VZ3(x,n-8,l)  , VZ3(x,n,l)
#define VZ5(x,n,l) VZ4(x,n-16,l) , VZ4(x,n,l)
#define VZ6(x,n,l) VZ5(x,n-32,l) , VZ5(x,n,l)


#define P2(x)   V1(x,2)
#define P4(x)   V2(x,4)
#define P8(x)   V3(x,8)
#define P16(x)  V4(x,16)
#define P32(x)  V5(x,32)
#define PZ16(x,l)  VZ4(x,16,l)
#define PZ32(x,l)  VZ5(x,32,l)


void print_r(const ge_p2 *r) {
    printf("R Structure: \n");
    printf("r->X: ");

    #pragma unroll
    for (int i = 0; i < 10; i++) {
        printf("%08x", r->X[i]);
    }

    printf("\nr->Y: ");

    #pragma unroll
    for (int i = 0; i < 10; i++) {
        printf("%08x", r->Y[i]);
    }

    printf("\nr->Z: ");

    #pragma unroll
    for (int i = 0; i < 10; i++) {
        printf("%08x", r->Z[i]);
    }

    printf("\n\n");
}

#define MAX_MSG_LEN 5120
__kernel void verify(__global unsigned char *input0,__global unsigned char *input, __global unsigned char *output) {
	uint buf_sz = MAX_MSG_LEN;
	const size_t x = get_global_id(0);
	const size_t y = get_global_id(1);
	const size_t z = get_global_id(2);
	const size_t width = get_global_size(0);
	const size_t height = get_global_size(1);
	const size_t id = z * width * height + y * width + x;
		
	union {
		unsigned char bytes[64];
		ulong8 v;
	} header;

	//read 64 bytes into memory
	header.v = vload8(id, (__global ulong *)input0);
	ulong mlen = header.v.s0;
	
/*
	printf("id=%d mlen = %#lu\n",id,mlen);	
*/
	if(mlen>=0xFFFF){
		//This is to support the case of odd sizes in dim2 and dim3, this is a dummy
		//printf("Skipped\n");
		*(output + id) = -4;
		return;
	}
	unsigned char *sig2 = header.bytes+32; //second half of the signature
	if (sig2[31] & 224) { //checking the last byte of sig
//		printf("id=%d Error 1\n",id);
		*(output + id) = -1;
		return;
	}

	//mlen tell us how long to read
	uint msg_words = (uint)((mlen+64) >> 7);

	union {
		unsigned char bytes[MAX_MSG_LEN];
		ulong16 v[MAX_MSG_LEN>>7];
	}task;

	//read 128 byte chunks
	unsigned int  i;
	for (i = 0; i <= msg_words; i++) //copy as many words as required
	{
		task.v[i] = vload16(i + ((id*buf_sz) >> 7), (__global ulong *)input);
	}
	
	//  sig1=>task.bytes[0 - 31]
	//  pk=>task.bytes[32 -63]
	//  msg=>task.bytes[64-]

	ge_p3 A;
	if (ge_frombytes_negate_vartime(&A, task.bytes+32) != 0) { //test on PK
//		printf("id=%d Error 2 Bad PK: ",id);
//		for (i = 32; i<64; i++)
//		{
//			printf("%02x", task.bytes[i]);
//		}
//		printf("\n");
		*(output + id) = -2;
		return;
	}

	unsigned char d = 0;
	for (i = 32; i < 64; ++i) {
		d |= *(task.bytes+i); //32 bytes of PK ored here
	}
	if (d == 0) {
//		printf("id=%d Error 3\n",id);
		*(output + id) = -3;
		return;
	}

	hash_sha512_state hs;
	unsigned char h[64];
	hash_sha512_init(&hs);
	hash_sha512_update(&hs, task.bytes, 64+mlen);
	hash_sha512_final(&hs, h);

//    if (id == 0) {
//        printf("SHA Hash: ");
//
//        #pragma unroll
//        for (int i = 0; i < 64; i++) {
//            printf("%02x", h[i]);
//        }
//        printf("\n");
//
//        printf("Original Signature: ");
//
//        #pragma unroll
//        for (int i = 0; i < 32; i++) {
//            printf("%02x", task.bytes[i]);
//        }
//
//        #pragma unroll
//        for (int i = 32; i < 64; i++) {
//            printf("%02x", header.bytes[i]);
//        }
//        printf("\n");
//
//
//        printf("Message Blob: ");
//
//        #pragma unroll
//        for (int i = 64; i < mlen + 64; i++) {
//            printf("%02x", task.bytes[i]);
//        }
//        printf("\n");
//
//
//        printf("SHA Vector Blob: ");
//
//        #pragma unroll
//        for (int i = 0; i < mlen + 64; i++) {
//            printf("%02x", task.bytes[i]);
//        }
//        printf("\n");
//	}

	sc_reduce(h);

	ge_p2 R;
	ge_double_scalarmult_vartime(&R, h, &A, sig2);
	
	unsigned char rcheck[32];
	
	ge_tobytes(rcheck, &R);

//	if (id == 0) {
//	    printf("R Check Array: ");
//
//        #pragma unroll
//        for (int i = 0; i < 32; i++) {
//            printf("%02x", rcheck[i]);
//        }
//        printf("\n");
//
//        print_r(&R);
//	}

	int res = verify_32(rcheck, task.bytes);

//    if (id == 0) {
//        printf("Final Result: %d", res);
//    }

	if (res != 0)
	{
//		uchar *tb=task.bytes;
//		printf( CAT3("\nfailed id=%hu"," msglen=%hu", "\n"),(ushort)id,(ushort)mlen);
//		printf( CAT3( "id=%hu sig0=" , B4("%02x") , "\n" ) , (ushort)id , P16(tb));
//		tb+=16;
//		printf( CAT3( "id=%hu sig1=" , B4("%02x") , "\n" ) , (ushort)id , P16(tb));
//		tb=sig2;
//		printf( CAT3( "id=%hu sig2=" , B4("%02x") , "\n" ) , (ushort)id , P16(tb));
//		tb+=16;
//		printf( CAT3( "id=%hu sig3=" , B4("%02x") , "\n" ) , (ushort)id , P16(tb));
//		uchar *msg=task.bytes+64;
//		ushort m=0;
//		for(;m<mlen/16;m++)
//		{
//			printf( CAT3( "id=%hu msg%hu=" , B4("%02x") , "\n" ) , (ushort)id , m, P16(msg));
//			msg=msg+16;
//		}
//		ulong limit=mlen%16;
//		if(limit>0)
//		{
//			printf( CAT3( "id=%hu msg%hu=" , B4("%02x") , "\n" ) , (ushort)id , m, PZ16(msg,limit));
//		}
		
			
		*(output + id) = 1;
	}
	else
	{
		*(output + id) = 0;
	}

	return;
} 
