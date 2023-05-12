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

/* For memset(), memcmp(), strlen() */
/* #include <string.h> */

/* For int32_t, int64_t, uint64_t */
/*
#include <stdint.h>

#include <stdio.h>
*/

/* Ajay */
typedef int int32_t ;
typedef long int64_t ;
typedef ulong uint64_t ;
typedef uchar uint8_t ;
typedef unsigned int uint32_t;


#define crypto_sign_PUBLICKEYBYTES 96U
#define crypto_sign_BYTES 96U

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
/*
int hex2bin(unsigned char * const bin, const size_t bin_maxlen,
            const char * const hex, const size_t hex_len);
			
int verify(const unsigned char *sig, const unsigned char *m,
           unsigned long long mlen, const unsigned char *pk);
*/
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


/* Ajay EDCSA P384*/


void hash_sha384_update(hash_sha512_state *state,
	const unsigned char *in,
	unsigned long long inlen);

void hash_sha384_final(hash_sha512_state *state, unsigned char *out);
void hash_sha384_init(hash_sha512_state *state);

/* Ajay - Supporting fnctions for ecdsa signature verfication*/

/* Ajay mpi.h*/

/**
* @file mpi.h
* @brief MPI (Multiple Precision Integer Arithmetic)
*
* @section License
*
* Copyright (C) 2010-2017 Oryx Embedded SARL. All rights reserved.
*
* This file is part of CycloneCrypto Open.
*
* This program is free software; you can redistribute it and/or
* modify it under the terms of the GNU General Public License
* as published by the Free Software Foundation; either version 2
* of the License, or (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program; if not, write to the Free Software Foundation,
* Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*
* @author Oryx Embedded SARL (www.oryx-embedded.com)
* @version 1.8.0
**/

#ifndef _MPI_H
#define _MPI_H

//Dependencies
// #include <stdio.h>
// #include <stdint.h>
// #include "crypto_defs.h" 
// #include "error.h"

#ifdef __cplusplus
extern "C" {
#endif


	/**
	* @brief Error codes
	**/

	typedef enum
	{
		NO_ERROR = 0,                    ///<Success
		ERROR_FAILURE = 1,               ///<Generic error code
		ERROR_INVALID_PARAMETER,         ///<Invalid parameter
		ERROR_PARAMETER_OUT_OF_RANGE,    ///<Specified parameter is out of range

		ERROR_BAD_CRC,
		ERROR_BAD_BLOCK,
		ERROR_INVALID_RECIPIENT,         ///<Invalid recipient
		ERROR_INVALID_INTERFACE,         ///<Invalid interface
		ERROR_INVALID_ENDPOINT,          ///<Invalid endpoint
		ERROR_INVALID_ALT_SETTING,       ///<Alternate setting does not exist
		ERROR_UNSUPPORTED_REQUEST,       ///<Unsupported request
		ERROR_UNSUPPORTED_CONFIGURATION, ///<Unsupported configuration
		ERROR_UNSUPPORTED_FEATURE,       ///<Unsupported feature
		ERROR_ENDPOINT_BUSY,             ///<Endpoint already in use
		ERROR_USB_RESET,
		ERROR_ABORTED,

		ERROR_OUT_OF_MEMORY = 100,
		ERROR_OUT_OF_RESOURCES,
		ERROR_INVALID_REQUEST,
		ERROR_NOT_IMPLEMENTED,
		ERROR_VERSION_NOT_SUPPORTED,
		ERROR_INVALID_SYNTAX,
		ERROR_AUTHENTICATION_FAILED,
		ERROR_UNEXPECTED_RESPONSE,
		ERROR_INVALID_RESPONSE,
		ERROR_UNEXPECTED_VALUE,

		ERROR_OPEN_FAILED = 200,
		ERROR_CONNECTION_FAILED,
		ERROR_CONNECTION_REFUSED,
		ERROR_CONNECTION_CLOSING,
		ERROR_CONNECTION_RESET,
		ERROR_NOT_CONNECTED,
		ERROR_ALREADY_CLOSED,
		ERROR_ALREADY_CONNECTED,
		ERROR_INVALID_SOCKET,
		ERROR_PROTOCOL_UNREACHABLE,
		ERROR_PORT_UNREACHABLE,
		ERROR_INVALID_FRAME,
		ERROR_INVALID_HEADER,
		ERROR_WRONG_CHECKSUM,
		ERROR_WRONG_IDENTIFIER,
		ERROR_WRONG_CLIENT_ID,
		ERROR_WRONG_SERVER_ID,
		ERROR_WRONG_COOKIE,
		ERROR_NO_RESPONSE,
		ERROR_RECEIVE_QUEUE_FULL,
		ERROR_TIMEOUT,
		ERROR_WOULD_BLOCK,
		ERROR_INVALID_NAME,
		ERROR_INVALID_OPTION,
		ERROR_UNEXPECTED_STATE,
		ERROR_INVALID_COMMAND,
		ERROR_INVALID_PROTOCOL,
		ERROR_INVALID_STATUS,
		ERROR_INVALID_ADDRESS,
		ERROR_INVALID_MESSAGE,
		ERROR_INVALID_KEY,
		ERROR_INVALID_KEY_LENGTH,
		ERROR_INVALID_EPOCH,
		ERROR_INVALID_SEQUENCE_NUMBER,
		ERROR_INVALID_CHARACTER,
		ERROR_INVALID_LENGTH,
		ERROR_INVALID_PADDING,
		ERROR_INVALID_MAC,
		ERROR_INVALID_TAG,
		ERROR_INVALID_TYPE,
		ERROR_INVALID_VALUE,
		ERROR_INVALID_CLASS,
		ERROR_INVALID_VERSION,
		ERROR_INVALID_PIN_CODE,
		ERROR_WRONG_LENGTH,
		ERROR_WRONG_TYPE,
		ERROR_WRONG_ENCODING,
		ERROR_WRONG_VALUE,
		ERROR_INCONSISTENT_VALUE,
		ERROR_UNSUPPORTED_TYPE,
		ERROR_UNSUPPORTED_CIPHER_SUITE,
		ERROR_UNSUPPORTED_CIPHER_MODE,
		ERROR_UNSUPPORTED_CIPHER_ALGO,
		ERROR_UNSUPPORTED_HASH_ALGO,
		ERROR_UNSUPPORTED_KEY_EXCH_METHOD,
		ERROR_UNSUPPORTED_SIGNATURE_ALGO,
		ERROR_UNSUPPORTED_ELLIPTIC_CURVE,
		ERROR_INVALID_SIGNATURE_ALGO,
		ERROR_CERTIFICATE_REQUIRED,
		ERROR_MESSAGE_TOO_LONG,
		ERROR_OUT_OF_RANGE,
		ERROR_MESSAGE_DISCARDED,

		ERROR_INVALID_PACKET,
		ERROR_BUFFER_EMPTY,
		ERROR_BUFFER_OVERFLOW,
		ERROR_BUFFER_UNDERFLOW,

		ERROR_INVALID_RESOURCE,
		ERROR_INVALID_PATH,
		ERROR_NOT_FOUND,
		ERROR_ACCESS_DENIED,
		ERROR_NOT_WRITABLE,
		ERROR_AUTH_REQUIRED,

		ERROR_TRANSMITTER_BUSY,
		ERROR_NO_RUNNING,

		ERROR_INVALID_FILE = 300,
		ERROR_FILE_NOT_FOUND,
		ERROR_FILE_OPENING_FAILED,
		ERROR_FILE_READING_FAILED,
		ERROR_END_OF_FILE,
		ERROR_UNEXPECTED_END_OF_FILE,
		ERROR_UNKNOWN_FILE_FORMAT,

		ERROR_INVALID_DIRECTORY,
		ERROR_DIRECTORY_NOT_FOUND,

		ERROR_FILE_SYSTEM_NOT_SUPPORTED = 400,
		ERROR_UNKNOWN_FILE_SYSTEM,
		ERROR_INVALID_FILE_SYSTEM,
		ERROR_INVALID_BOOT_SECTOR_SIGNATURE,
		ERROR_INVALID_SECTOR_SIZE,
		ERROR_INVALID_CLUSTER_SIZE,
		ERROR_INVALID_FILE_RECORD_SIZE,
		ERROR_INVALID_INDEX_BUFFER_SIZE,
		ERROR_INVALID_VOLUME_DESCRIPTOR_SIGNATURE,
		ERROR_INVALID_VOLUME_DESCRIPTOR,
		ERROR_INVALID_FILE_RECORD,
		ERROR_INVALID_INDEX_BUFFER,
		ERROR_INVALID_DATA_RUNS,
		ERROR_WRONG_TAG_IDENTIFIER,
		ERROR_WRONG_TAG_CHECKSUM,
		ERROR_WRONG_MAGIC_NUMBER,
		ERROR_WRONG_SEQUENCE_NUMBER,
		ERROR_DESCRIPTOR_NOT_FOUND,
		ERROR_ATTRIBUTE_NOT_FOUND,
		ERROR_RESIDENT_ATTRIBUTE,
		ERROR_NOT_RESIDENT_ATTRIBUTE,
		ERROR_INVALID_SUPER_BLOCK,
		ERROR_INVALID_SUPER_BLOCK_SIGNATURE,
		ERROR_INVALID_BLOCK_SIZE,
		ERROR_UNSUPPORTED_REVISION_LEVEL,
		ERROR_INVALID_INODE_SIZE,
		ERROR_INODE_NOT_FOUND,

		ERROR_UNEXPECTED_MESSAGE = 500,

		ERROR_URL_TOO_LONG,
		ERROR_QUERY_STRING_TOO_LONG,

		ERROR_NO_ADDRESS,
		ERROR_NO_BINDING,
		ERROR_NOT_ON_LINK,
		ERROR_USE_MULTICAST,
		ERROR_NAK_RECEIVED,

		ERROR_NO_CARRIER,

		ERROR_INVALID_LEVEL,
		ERROR_WRONG_STATE,
		ERROR_END_OF_STREAM,
		ERROR_LINK_DOWN,
		ERROR_INVALID_OPTION_LENGTH,
		ERROR_IN_PROGRESS,

		ERROR_NO_ACK,
		ERROR_INVALID_METADATA,
		ERROR_NOT_CONFIGURED,
		ERROR_NAME_RESOLUTION_FAILED,
		ERROR_NO_ROUTE,

		ERROR_WRITE_FAILED,
		ERROR_READ_FAILED,
		ERROR_UPLOAD_FAILED,

		ERROR_INVALID_SIGNATURE,

		ERROR_BAD_RECORD_MAC,
		ERROR_RECORD_OVERFLOW,
		ERROR_HANDSHAKE_FAILED,
		ERROR_NO_CERTIFICATE,
		ERROR_BAD_CERTIFICATE,
		ERROR_UNSUPPORTED_CERTIFICATE,
		ERROR_CERTIFICATE_EXPIRED,
		ERROR_UNKNOWN_CA,
		ERROR_DECODING_FAILED,
		ERROR_DECRYPTION_FAILED,
		ERROR_ILLEGAL_PARAMETER,
		ERROR_UNSUPPORTED_EXTENSION,
		ERROR_INAPPROPRIATE_FALLBACK,
		ERROR_NO_APPLICATION_PROTOCOL,

		ERROR_MORE_DATA_REQUIRED,
		ERROR_TLS_NOT_SUPPORTED,
		ERROR_PRNG_NOT_READY,
		ERROR_SERVICE_CLOSING,
		ERROR_INVALID_TIMESTAMP,
		ERROR_NO_DNS_SERVER,

		ERROR_OBJECT_NOT_FOUND,
		ERROR_INSTANCE_NOT_FOUND,
		ERROR_ADDRESS_NOT_FOUND,

		ERROR_UNKNOWN_IDENTITY,
		ERROR_UNKNOWN_ENGINE_ID,
		ERROR_UNKNOWN_USER_NAME,
		ERROR_UNKNOWN_CONTEXT,
		ERROR_UNAVAILABLE_CONTEXT,
		ERROR_UNSUPPORTED_SECURITY_LEVEL,
		ERROR_NOT_IN_TIME_WINDOW,
		ERROR_AUTHORIZATION_FAILED,

		ERROR_NO_MATCH,
		ERROR_PARTIAL_MATCH
	} error_t;

	//C++ guard
#ifdef __cplusplus
}
#endif

/* Ajay ECDSA P 384 function*/
error_t ecdsaVerifySignature(const uint8_t *pubKey,
	const uint8_t *digest, size_t digestLen, const uint8_t * signature);

#ifndef FALSE
#define FALSE 0
#endif

#ifndef TRUE
#define TRUE 1
#endif

#ifndef NULL
#define NULL 0
#endif


#ifndef arraysize
#define arraysize(a) (sizeof(a) / sizeof(a[0]))
#endif

/* Extra additions for compilation */
typedef char char_t;
typedef signed int int_t;
typedef unsigned int uint_t;

#if !defined(R_TYPEDEFS_H) && !defined(USE_CHIBIOS_2)
typedef int bool_t;
#endif

//Size of the sub data type
#define MPI_INT_SIZE sizeof(uint_t)

//Error code checking
#define MPI_CHECK(f) if((error = f) != NO_ERROR) goto end

//Miscellaneous macros
#define mpiIsEven(a) !mpiGetBitValue(a, 0)
#define mpiIsOdd(a) mpiGetBitValue(a, 0)

#ifndef MIN
#define MIN(a, b) ((a) < (b) ? (a) : (b))
#endif

#ifndef MAX
#define MAX(a, b) ((a) > (b) ? (a) : (b))
#endif


//C++ guard
#ifdef __cplusplus
extern "C" {
#endif


	/**
	* @brief Arbitrary precision integer
	**/

	typedef struct
	{
		int_t sign;
		uint_t size ;
		uint_t data[25];
	} Mpi;


	//MPI related functions
	void mpiInit(Mpi *r);
	void mpiFree(Mpi *r);

	error_t mpiGrow(Mpi *r, uint_t size);

	uint_t mpiGetLength(const Mpi *a);
	uint_t mpiGetByteLength(const Mpi *a);
	uint_t mpiGetBitLength(const Mpi *a);

	error_t mpiSetBitValue(Mpi *r, uint_t index, uint_t value);
	uint_t mpiGetBitValue(const Mpi *a, uint_t index);

	int_t mpiComp(const Mpi *a, const Mpi *b);
	int_t mpiCompInt(const Mpi *a, int_t b);
	int_t mpiCompAbs(const Mpi *a, const Mpi *b);

	error_t mpiCopy(Mpi *r, const Mpi *a);
	error_t mpiSetValue(Mpi *a, int_t b);

	/* error_t mpiRand(Mpi *r, uint_t length, const PrngAlgo *prngAlgo, void *prngContext);
	*/

	error_t mpiReadRaw(Mpi *r, const uint8_t *data, uint_t length);
	error_t mpiWriteRaw(const Mpi *a, uint8_t *data, uint_t length);

	error_t mpiAdd(Mpi *r, const Mpi *a, const Mpi *b);
	error_t mpiAddInt(Mpi *r, const Mpi *a, int_t b);

	error_t mpiSub(Mpi *r, const Mpi *a, const Mpi *b);
	error_t mpiSubInt(Mpi *r, const Mpi *a, int_t b);

	error_t mpiAddAbs(Mpi *r, const Mpi *a, const Mpi *b);
	error_t mpiSubAbs(Mpi *r, const Mpi *a, const Mpi *b);

	error_t mpiShiftLeft(Mpi *r, uint_t n);
	error_t mpiShiftRight(Mpi *r, uint_t n);

	error_t mpiMul(Mpi *r, const Mpi *a, const Mpi *b);
	error_t mpiMulInt(Mpi *r, const Mpi *a, int_t b);

	error_t mpiDiv(Mpi *q, Mpi *r, const Mpi *a, const Mpi *b);
	error_t mpiDivInt(Mpi *q, Mpi *r, const Mpi *a, int_t b);

	error_t mpiMod(Mpi *r, const Mpi *a, const Mpi *p);
	error_t mpiAddMod(Mpi *r, const Mpi *a, const Mpi *b, const Mpi *p);
	error_t mpiSubMod(Mpi *r, const Mpi *a, const Mpi *b, const Mpi *p);
	error_t mpiMulMod(Mpi *r, const Mpi *a, const Mpi *b, const Mpi *p);
	error_t mpiInvMod(Mpi *r, const Mpi *a, const Mpi *p);
	error_t mpiExpMod(Mpi *r, const Mpi *a, const Mpi *e, const Mpi *p);

	error_t mpiMontgomeryMul(Mpi *r, const Mpi *a, const Mpi *b, uint_t k, const Mpi *p, Mpi *t);
	error_t mpiMontgomeryRed(Mpi *r, const Mpi *a, uint_t k, const Mpi *p, Mpi *t);

	void mpiMulAccCore(uint_t *r, const uint_t *a, int_t m, const uint_t b);

	// void mpiDump(FILE *stream, const char_t *prepend, const Mpi *a);

	//C++ guard
#ifdef __cplusplus
}
#endif

#endif

/* ec.h*/
/**
* @file ec.h
* @brief ECC (Elliptic Curve Cryptography)
*
* @section License
*
* Copyright (C) 2010-2017 Oryx Embedded SARL. All rights reserved.
*
* This file is part of CycloneCrypto Open.
*
* This program is free software; you can redistribute it and/or
* modify it under the terms of the GNU General Public License
* as published by the Free Software Foundation; either version 2
* of the License, or (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program; if not, write to the Free Software Foundation,
* Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*
* @author Oryx Embedded SARL (www.oryx-embedded.com)
* @version 1.8.0
**/

#ifndef _EC_H
#define _EC_H

//Dependencies
/* #include "core/crypto.h" */
/* #include "ecc/ec_curves.h" */

// #include <stdint.h>
// #include "error.h"
// #include "mpi.h"

//Error code checking
#define EC_CHECK(f) if((error = f) != NO_ERROR) goto end

//C++ guard
#ifdef __cplusplus
extern "C" {
#endif


	typedef enum {
		EC_CURVE_TYPE_NONE = 0,
		EC_CURVE_TYPE_SECT_K1 = 1,
		EC_CURVE_TYPE_SECT_R1 = 2,
		EC_CURVE_TYPE_SECT_R2 = 3,
		EC_CURVE_TYPE_SECP_K1 = 4,
		EC_CURVE_TYPE_SECP_R1 = 5,
		EC_CURVE_TYPE_SECP_R2 = 6,
		EC_CURVE_TYPE_BRAINPOOLP_R1 = 7
	} EcCurveType;
	/**
	* @brief Elliptic curve point
	**/

	typedef struct
	{
		Mpi x; ///<x-coordinate
		Mpi y; ///<y-coordinate
		Mpi z; ///<z-coordinate
	} EcPoint;

	typedef error_t(*EcFastModAlgo)(Mpi *a, const Mpi *p);

	typedef struct
	{
		char_t name;   ///<Curve name
		const uint8_t *oid;   ///<Object identifier
		size_t oidSize;       ///<OID size
		EcCurveType type;     ///<Curve type
		const uint8_t p[66];  ///<Prime modulus p
		size_t pLen;          ///<Length of p
		const uint8_t a[66];  ///<Curve parameter a
		size_t aLen;          ///<Length of a
		const uint8_t b[66];  ///<Curve parameter b
		size_t bLen;          ///<Length of b
		const uint8_t gx[66]; ///<x-coordinate of the base point G
		size_t gxLen;         ///<Length of Gx
		const uint8_t gy[66]; ///<y-coordinate of the base point G
		size_t gyLen;         ///<Length of Gy
		const uint8_t q[66];  ///<Order of the base point G
		size_t qLen;          ///<Length of q
		uint32_t h;           ///<Cofactor h
		// EcFastModAlgo mod;    ///<Fast modular reduction
	} EcCurveInfo;

	// error_t secp384r1Mod(Mpi *a, const Mpi *p);
	error_t secp384r1Mod_1(Mpi *a, const Mpi *p);  // Ajay - To avoid function pointer in kernel
	// const uint8_t SECP384R1_OID[5] = { 0x2B, 0x81, 0x04, 0x00, 0x22 };



	/**
	* @brief EC domain parameters
	**/

	typedef struct
	{
		EcCurveType type;  ///<Curve type
		Mpi p;             ///<Prime
		Mpi a;             ///<Curve parameter a
		Mpi b;             ///<Curve parameter b
		EcPoint g;         ///<Base point G
		Mpi q;             ///<Order of the point G
		EcFastModAlgo mod; ///<Fast modular reduction
	} EcDomainParameters;


	//EC related constants
	// extern const uint8_t EC_PUBLIC_KEY_OID[7];

	//EC related functions
	void ecInitDomainParameters(EcDomainParameters *params);
	void ecFreeDomainParameters(EcDomainParameters *params);

	error_t ecLoadDomainParameters(EcDomainParameters *params, const EcCurveInfo *curveInfo);

	void ecInit(EcPoint *r);
	void ecFree(EcPoint *r);

	error_t ecCopy(EcPoint *r, const EcPoint *s);

	error_t ecImport(const EcDomainParameters *params,
		EcPoint *r, const uint8_t *data, size_t length);

	error_t ecExport(const EcDomainParameters *params,
		const EcPoint *a, uint8_t *data, size_t *length);

	error_t ecProjectify(const EcDomainParameters *params, EcPoint *r, const EcPoint *s);
	error_t ecAffinify(const EcDomainParameters *params, EcPoint *r, const EcPoint *s);

	bool_t ecIsPointAffine(const EcDomainParameters *params, const EcPoint *s);

	error_t ecDouble(const EcDomainParameters *params, EcPoint *r, const EcPoint *s);

	error_t ecAdd(const EcDomainParameters *params, EcPoint *r, const EcPoint *s, const EcPoint *t);
	error_t ecFullAdd(const EcDomainParameters *params, EcPoint *r, const EcPoint *s, const EcPoint *t);
	error_t ecFullSub(const EcDomainParameters *params, EcPoint *r, const EcPoint *s, const EcPoint *t);

	error_t ecMult(const EcDomainParameters *params, EcPoint *r, const Mpi *d, const EcPoint *s);

	error_t ecTwinMult(const EcDomainParameters *params, EcPoint *r,
		const Mpi *d0, const EcPoint *s, const Mpi *d1, const EcPoint *t);

	error_t ecAddMod(const EcDomainParameters *params, Mpi *r, const Mpi *a, const Mpi *b);
	error_t ecSubMod(const EcDomainParameters *params, Mpi *r, const Mpi *a, const Mpi *b);
	error_t ecMulMod(const EcDomainParameters *params, Mpi *r, const Mpi *a, const Mpi *b);
	error_t ecSqrMod(const EcDomainParameters *params, Mpi *r, const Mpi *a);

	//C++ guard
#ifdef __cplusplus
}
#endif

#endif





/**
* @file mpi.c
* @brief MPI (Multiple Precision Integer Arithmetic)
*
* @section License
*
* Copyright (C) 2010-2017 Oryx Embedded SARL. All rights reserved.
*
* This file is part of CycloneCrypto Open.
*
* This program is free software; you can redistribute it and/or
* modify it under the terms of the GNU General Public License
* as published by the Free Software Foundation; either version 2
* of the License, or (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program; if not, write to the Free Software Foundation,
* Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*
* @author Oryx Embedded SARL (www.oryx-embedded.com)
* @version 1.8.0
**/

//Switch to the appropriate trace level
#define TRACE_LEVEL CRYPTO_TRACE_LEVEL

//Dependencies

// #include "mpi.h"


//Check crypto library configuration
// #if (MPI_SUPPORT == ENABLED)


/**
* @brief Initialize a multiple precision integer
* @param[in,out] r Pointer to the multiple precision integer to be initialized
**/

void mpiInit(Mpi *r)
{
	//Initialize structure
	r->sign = 1;
	r->size = 0;
#pragma unroll
	for (int s=0; s<25; s++)
		r->data[s] = 0;

	// r->data = NULL; - Ajay
}


/**
* @brief Release a multiple precision integer
* @param[in,out] r Pointer to the multiple precision integer to be freed
**/

void mpiFree(Mpi *r)
{
	//Any memory previously allocated?
	/*
	if (r->data != NULL)
	{
		//Erase contents before releasing memory
		// cryptoMemset(r->data, 0, r->size * MPI_INT_SIZE); - Ajay
        #pragma unroll
		for (int s = 0; s< r->size; s++) {
			r->data[s] = 0;
		}
		// cryptoFreeMem(r->data);
	}
	*/

	//Set size to zero
	r->size = 0;
    #pragma unroll
	for (int s = 0; s< r->size; s++) {
		r->data[s] = 0;
	}
	// r->data = NULL;
}


/**
* @brief Adjust the size of multiple precision integer
* @param[in,out] r A multiple precision integer whose size is to be increased
* @param[in] size Desired size in words
* @return Error code
**/


error_t mpiGrow(Mpi *r, uint_t size)
{
	uint_t *data;

	//Ensure the parameter is valid
	size = MAX(size, 1);

	//Check the current size
	if (r->size >= size)
		return NO_ERROR;

	//Allocate a memory buffer
	// data = (uint_t *)cryptoAllocMem(size * MPI_INT_SIZE);
	//Failed to allocate memory?
	// if (data == NULL)
	//	return ERROR_OUT_OF_MEMORY;

	//Clear buffer contents
	// cryptoMemset(data, 0, size * MPI_INT_SIZE);

	//Any data to copy?
	// if (r->size > 0)
	// {
		//Copy original data
	//	cryptoMemcpy(data, r->data, r->size * MPI_INT_SIZE);
		//Free previously allocated memory
	//	cryptoFreeMem(r->data);
	// }

	//Update the size of the multiple precision integer
	r->size = size;
	// r->data = data;

	//Successful operation
	return NO_ERROR;
}



/**
* @brief Get the actual length in words
* @param[in] a Pointer to a multiple precision integer
* @return The actual length in words
**/

uint_t mpiGetLength(const Mpi *a)
{
	int_t i;

	//Check whether the specified multiple precision integer is empty
	if (a->size == 0)
		return 0;

	//Start from the most significant word
	for (i = a->size - 1; i >= 0; i--)
	{
		//Loop as long as the current word is zero
		if (a->data[i] != 0)
			break;
	}

	//Return the actual length
	return i + 1;
}


/**
* @brief Get the actual length in bytes
* @param[in] a Pointer to a multiple precision integer
* @return The actual byte count
**/

uint_t mpiGetByteLength(const Mpi *a)
{
	uint_t n;
	uint32_t m;

	//Check whether the specified multiple precision integer is empty
	if (a->size == 0)
		return 0;

	//Start from the most significant word
	for (n = a->size - 1; n > 0; n--)
	{
		//Loop as long as the current word is zero
		if (a->data[n] != 0)
			break;
	}

	//Get the current word
	m = a->data[n];
	//Convert the length to a byte count
	n *= MPI_INT_SIZE;

	//Adjust the byte count
	for (; m != 0; m >>= 8) n++;

	//Return the actual length in bytes
	return n;
}


/**
* @brief Get the actual length in bits
* @param[in] a Pointer to a multiple precision integer
* @return The actual bit count
**/

uint_t mpiGetBitLength(const Mpi *a)
{
	uint_t n;
	uint32_t m;

	//Check whether the specified multiple precision integer is empty
	if (a->size == 0)
		return 0;

	//Start from the most significant word
	for (n = a->size - 1; n > 0; n--)
	{
		//Loop as long as the current word is zero
		if (a->data[n] != 0)
			break;
	}

	//Get the current word
	m = a->data[n];
	//Convert the length to a bit count
	n *= MPI_INT_SIZE * 8;

	//Adjust the bit count
	for (; m != 0; m >>= 1) n++;

	//Return the actual length in bits
	return n;
}


/**
* @brief Set the bit value at the specified index
* @param[in] r Pointer to a multiple precision integer
* @param[in] index Position of the bit to be written
* @param[in] value Bit value
* @return Error code
**/

error_t mpiSetBitValue(Mpi *r, uint_t index, uint_t value)
{
	error_t error;
	uint_t n1;
	uint_t n2;

	//Retrieve the position of the bit to be written
	n1 = index / (MPI_INT_SIZE * 8);
	n2 = index % (MPI_INT_SIZE * 8);

	//Ajust the size of the multiple precision integer if necessary  - Ajay
	error = mpiGrow(r, (index + (MPI_INT_SIZE * 8) - 1) / (MPI_INT_SIZE * 8));
	//Failed to adjust the size?
	if (error)
	  return error;

	//Set bit value
	if (value)
		r->data[n1] |= (1 << n2);
	else
		r->data[n1] &= ~(1 << n2);

	//No error to report
	return NO_ERROR;
}


/**
* @brief Get the bit value at the specified index
* @param[in] a Pointer to a multiple precision integer
* @param[in] index Position where to read the bit
* @return The actual bit value
**/

uint_t mpiGetBitValue(const Mpi *a, uint_t index)
{
	uint_t n1;
	uint_t n2;

	//Retrieve the position of the bit to be read
	n1 = index / (MPI_INT_SIZE * 8);
	n2 = index % (MPI_INT_SIZE * 8);

	//Index out of range?
	if (n1 >= a->size)
		return 0;

	//Return the actual bit value
	return (a->data[n1] >> n2) & 0x01;
}


/**
* @brief Compare two multiple precision integers
* @param[in] a The first multiple precision integer to be compared
* @param[in] b The second multiple precision integer to be compared
* @return Comparison result
**/

int_t mpiComp(const Mpi *a, const Mpi *b)
{
	uint_t m;
	uint_t n;

	//Determine the actual length of A and B
	m = mpiGetLength(a);
	n = mpiGetLength(b);

	//Compare lengths
	if (!m && !n)
		return 0;
	else if (m > n)
		return a->sign;
	else if (m < n)
		return -b->sign;

	//Compare signs
	if (a->sign > 0 && b->sign < 0)
		return 1;
	else if (a->sign < 0 && b->sign > 0)
		return -1;

	//Then compare values
	while (n--)
	{
		if (a->data[n] > b->data[n])
			return a->sign;
		else if (a->data[n] < b->data[n])
			return -a->sign;
	}

	//Multiple precision integers are equals
	return 0;
}


/**
* @brief Compare a multiple precision integer with an integer
* @param[in] a Multiple precision integer to be compared
* @param[in] b Integer to be compared
* @return Comparison result
**/

int_t mpiCompInt(const Mpi *a, int_t b)
{
	uint_t value;
	Mpi t;

	//Initialize a temporary multiple precision integer
	value = (b >= 0) ? b : -b;
	t.sign = (b >= 0) ? 1 : -1;
	t.size = 1;
	// t.data = &value; - Ajay
	t.data[0] = value;

	//Return comparison result
	return mpiComp(a, &t);
}


/**
* @brief Compare the absolute value of two multiple precision integers
* @param[in] a The first multiple precision integer to be compared
* @param[in] b The second multiple precision integer to be compared
* @return Comparison result
**/

int_t mpiCompAbs(const Mpi *a, const Mpi *b)
{
	uint_t m;
	uint_t n;

	//Determine the actual length of A and B
	m = mpiGetLength(a);
	n = mpiGetLength(b);

	//Compare lengths
	if (!m && !n)
		return 0;
	else if (m > n)
		return 1;
	else if (m < n)
		return -1;

	//Then compare values
	while (n--)
	{
		if (a->data[n] > b->data[n])
			return 1;
		else if (a->data[n] < b->data[n])
			return -1;
	}

	//Operands are equals
	return 0;
}


/**
* @brief Copy a multiple precision integer
* @param[out] r Pointer to a multiple precision integer (destination)
* @param[in] a Pointer to a multiple precision integer (source)
* @return Error code
**/

error_t mpiCopy(Mpi *r, const Mpi *a)
{
	error_t error;
	uint_t n;

	//R and A are the same instance?
	if (r == a)
		return NO_ERROR;

	//Determine the actual length of A
	n = mpiGetLength(a);

	//Ajust the size of the destination operand
	/* Ajay */
	 error = mpiGrow(r, n);
	//Any error to report?
	if (error)
		return error;

	//Clear the contents of the multiple precision integer
	// cryptoMemset(r->data, 0, r->size * MPI_INT_SIZE);
	// Ajay
    #pragma unroll
	for (int s=0; s< r->size; s++) { 
		r->data[s] = 0;
	}


	//Let R = A
	// cryptoMemcpy(r->data, a->data, n * MPI_INT_SIZE); - Ajay
    #pragma unroll
	for (int s = 0; s < n; s++) {
		r->data[s] = a->data[s];
	}
	//Set the sign of R
	r->sign = a->sign;

	//Successful operation
	return NO_ERROR;
}


/**
* @brief Set the value of a multiple precision integer
* @param[out] r Pointer to a multiple precision integer
* @param[in] a Value to be assigned to the multiple precision integer
* @return Error code
**/

error_t mpiSetValue(Mpi *r, int_t a)
{
	error_t error;

	//Ajust the size of the destination operand
	/* Ajay */
	error = mpiGrow(r, 1);
	//Failed to adjust the size?
	if (error)
	  return error;

	//Clear the contents of the multiple precision integer
	// cryptoMemset(r->data, 0, r->size * MPI_INT_SIZE); Ajay
    #pragma unroll
	for (int s =0; s< r->size; s++) { 
		r->data[s] = 0;
	}
	//Set the value or R
	r->data[0] = (a >= 0) ? a : -a;
	//Set the sign of R
	r->sign = (a >= 0) ? 1 : -1;

	//Successful operation
	return NO_ERROR;
}


/**
* @brief Generate a random value
* @param[out] r Pointer to a multiple precision integer
* @param[in] length Desired length in bits
* @param[in] prngAlgo PRNG algorithm
* @param[in] prngContext Pointer to the PRNG context
* @return Error code
**/

// error_t mpiRand(Mpi *r, uint_t length, const PrngAlgo *prngAlgo, void *prngContext)
// {
//  error_t error;
//  uint_t m;
//  uint_t n;

//Compute the required length, in words
// n = (length + (MPI_INT_SIZE * 8) - 1) / (MPI_INT_SIZE * 8);
//Number of bits in the most significant word
//  m = length % (MPI_INT_SIZE * 8);

//Ajust the size of the multiple precision integer if necessary
//  error = mpiGrow(r, n);
//Failed to adjust the size?
//  if(error)
//   return error;

//Clear the contents of the multiple precision integer
// cryptoMemset(r->data, 0, r->size * MPI_INT_SIZE);
//Set the sign of R
// r->sign = 1;

//Generate a random pattern
// error = prngAlgo->read(prngContext, (uint8_t *) r->data, n * MPI_INT_SIZE);
//Any error to report?
// if(error)
//    return error;

//Remove the meaningless bits in the most significant word
// if(n > 0 && m > 0)
//    r->data[n - 1] &= (1 << m) - 1;

//Successful operation
// return NO_ERROR;
// }


/**
* @brief Octet string to integer conversion
*
* Converts an octet string to a non-negative integer
*
* @param[out] r Non-negative integer resulting from the conversion
* @param[in] data Octet string to be converted
* @param[in] length Length of the octet string
* @return Error code
**/

error_t mpiReadRaw(Mpi *r, const uint8_t *data, uint_t length)
{
	error_t error;
	uint_t i;

	//Skip leading zeroes
	while (length > 1 && *data == 0)
	{
		//Advance read pointer
		data++;
		length--;
	}

	//Ajust the size of the multiple precision integer
	/* Ajay */
	error = mpiGrow(r, (length + MPI_INT_SIZE - 1) / MPI_INT_SIZE);
	//Failed to adjust the size?
	if (error)
		return error;

	//Clear the contents of the multiple precision integer
	// cryptoMemset(r->data, 0, r->size * MPI_INT_SIZE); - Ajay
    #pragma unroll
	for (int s=0; s< r->size; s++) { 
		r->data[s] = 0;
	}

	//Set sign
	r->sign = 1;

	//Start from the least significant byte
	data += length - 1;

	//Copy data
	for (i = 0; i < length; i++, data--)
		r->data[i / MPI_INT_SIZE] |= *data << ((i % MPI_INT_SIZE) * 8);

	//The conversion succeeded
	return NO_ERROR;
}


/**
* @brief Integer to octet string conversion
*
* Converts an integer to an octet string of a specified length
*
* @param[in] a Non-negative integer to be converted
* @param[out] data Octet string resulting from the conversion
* @param[in] length Intended length of the resulting octet string
* @return Error code
**/

error_t mpiWriteRaw(const Mpi *a, uint8_t *data, uint_t length)
{
	uint_t i;

	//Get the actual length in bytes
	uint_t n = mpiGetByteLength(a);

	//Make sure the output buffer is large enough
	if (n > length)
		return ERROR_INVALID_LENGTH;

	//Clear output buffer
	// cryptoMemset(data, 0, length); - Ajay
    #pragma unroll
	for (int s = 0; s< length; s++) {
		data[s] = 0;
	}

	//Start from the least significant word
	data += length - 1;

	//Copy data
	for (i = 0; i < n; i++, data--)
		*data = a->data[i / MPI_INT_SIZE] >> ((i % MPI_INT_SIZE) * 8);

	//The conversion succeeded
	return NO_ERROR;
}


/**
* @brief Multiple precision addition
* @param[out] r Resulting integer R = A + B
* @param[in] a First operand A
* @param[in] b Second operand B
* @return Error code
**/

error_t mpiAdd(Mpi *r, const Mpi *a, const Mpi *b)
{
	error_t error;
	int_t sign;

	//Retrieve the sign of A
	sign = a->sign;

	//Both operands have the same sign?
	if (a->sign == b->sign)
	{
		//Perform addition
		error = mpiAddAbs(r, a, b);
		//Set the sign of the resulting number
		r->sign = sign;
	}
	//Operands have different signs?
	else
	{
		//Compare the absolute value of A and B
		if (mpiCompAbs(a, b) >= 0)
		{
			//Perform subtraction
			error = mpiSubAbs(r, a, b);
			//Set the sign of the resulting number
			r->sign = sign;
		}
		else
		{
			//Perform subtraction
			error = mpiSubAbs(r, b, a);
			//Set the sign of the resulting number
			r->sign = -sign;
		}
	}

	//Return status code
	return error;
}


/**
* @brief Add an integer to a multiple precision integer
* @param[out] r Resulting integer R = A + B
* @param[in] a First operand A
* @param[in] b Second operand B
* @return Error code
**/

error_t mpiAddInt(Mpi *r, const Mpi *a, int_t b)
{
	uint_t value;
	Mpi t;

	//Convert the second operand to a multiple precision integer
	value = (b >= 0) ? b : -b;
	t.sign = (b >= 0) ? 1 : -1;
	t.size = 1;
	// t.data = &value; - Ajay
	t.data[0] = value;

	//Perform addition
	return mpiAdd(r, a, &t);
}


/**
* @brief Multiple precision subtraction
* @param[out] r Resulting integer R = A - B
* @param[in] a First operand A
* @param[in] b Second operand B
* @return Error code
**/

error_t mpiSub(Mpi *r, const Mpi *a, const Mpi *b)
{
	error_t error;
	int_t sign;

	//Retrieve the sign of A
	sign = a->sign;

	//Both operands have the same sign?
	if (a->sign == b->sign)
	{
		//Compare the absolute value of A and B
		if (mpiCompAbs(a, b) >= 0)
		{
			//Perform subtraction
			error = mpiSubAbs(r, a, b);
			//Set the sign of the resulting number
			r->sign = sign;
		}
		else
		{
			//Perform subtraction
			error = mpiSubAbs(r, b, a);
			//Set the sign of the resulting number
			r->sign = -sign;
		}
	}
	//Operands have different signs?
	else
	{
		//Perform addition
		error = mpiAddAbs(r, a, b);
		//Set the sign of the resulting number
		r->sign = sign;
	}

	//Return status code
	return error;
}


/**
* @brief Subtract an integer from a multiple precision integer
* @param[out] r Resulting integer R = A - B
* @param[in] a First operand A
* @param[in] b Second operand B
* @return Error code
**/

error_t mpiSubInt(Mpi *r, const Mpi *a, int_t b)
{
	uint_t value;
	Mpi t;

	//Convert the second operand to a multiple precision integer
	value = (b >= 0) ? b : -b;
	t.sign = (b >= 0) ? 1 : -1;
	t.size = 1;
	// t.data = &value; - Ajay
	t.data[0] = value;

	//Perform subtraction
	return mpiSub(r, a, &t);
}


/**
* @brief Helper routine for multiple precision addition
* @param[out] r Resulting integer R = |A + B|
* @param[in] a First operand A
* @param[in] b Second operand B
* @return Error code
**/

error_t mpiAddAbs(Mpi *r, const Mpi *a, const Mpi *b)
{
	error_t error;
	uint_t i;
	uint_t n;
	uint_t c;
	uint_t d;

	//R and B are the same instance?
	if (r == b)
	{
		//Swap A and B
		const Mpi *t = a;
		a = b;
		b = t;
	}
	//R is neither A nor B?
	else if (r != a)
	{
		//Copy the first operand to R
		MPI_CHECK(mpiCopy(r, a));
	}

	//Determine the actual length of B
	n = mpiGetLength(b);
	//Extend the size of the destination register as needed
	/* Ajay */
	MPI_CHECK(mpiGrow(r, n));

	//The result is always positive
	r->sign = 1;
	//Clear carry bit
	c = 0;

	//Add operands
	for (i = 0; i < n; i++)
	{
		//Add carry bit
		d = r->data[i] + c;
		//Update carry bit
		if (d != 0) c = 0;
		//Perform addition
		d += b->data[i];
		//Update carry bit
		if (d < b->data[i]) c = 1;
		//Save result
		r->data[i] = d;
	}

	//Loop as long as the carry bit is set
	for (i = n; c && i < r->size; i++)
	{
		//Add carry bit
		r->data[i] += c;
		//Update carry bit
		if (r->data[i] != 0) c = 0;
	}

	//Check the final carry bit
	if (c && n >= r->size)
	{
		//Extend the size of the destination register
		MPI_CHECK(mpiGrow(r, n + 1));
		//Add carry bit
		r->data[n] = 1;
	}

end:
	//Return status code
	return error;
}


/**
* @brief Helper routine for multiple precision subtraction
* @param[out] r Resulting integer R = |A - B|
* @param[in] a First operand A
* @param[in] b Second operand B
* @return Error code
**/

error_t mpiSubAbs(Mpi *r, const Mpi *a, const Mpi *b)
{
	error_t error;
	uint_t c;
	uint_t d;
	uint_t i;
	uint_t m;
	uint_t n;

	//Check input parameters
	if (mpiCompAbs(a, b) < 0)
	{
		//Swap A and B if necessary
		const Mpi *t = b;
		a = b;
		b = t;
	}

	//Determine the actual length of A
	m = mpiGetLength(a);
	//Determine the actual length of B
	n = mpiGetLength(b);

	//Extend the size of the destination register as needed
	MPI_CHECK(mpiGrow(r, m));

	//The result is always positive
	r->sign = 1;
	//Clear carry bit
	c = 0;

	//Subtract operands
	for (i = 0; i < n; i++)
	{
		//Read first operand
		d = a->data[i];

		//Check the carry bit
		if (c)
		{
			//Update carry bit
			if (d != 0) c = 0;
			//Propagate carry bit
			d -= 1;
		}

		//Update carry bit
		if (d < b->data[i]) c = 1;
		//Perform subtraction
		r->data[i] = d - b->data[i];
	}

	//Loop as long as the carry bit is set
	for (i = n; c && i < m; i++)
	{
		//Update carry bit
		if (a->data[i] != 0) c = 0;
		//Propagate carry bit
		r->data[i] = a->data[i] - 1;
	}

	//R and A are not the same instance?
	if (r != a)
	{
		//Copy the remaining words
		for (; i < m; i++)
			r->data[i] = a->data[i];

		//Zero the upper part of R
		for (; i < r->size; i++)
			r->data[i] = 0;
	}

end:
	//Return status code
	return error;
}


/**
* @brief Left shift operation
* @param[in,out] r The multiple precision integer to be shifted to the left
* @param[in] n The number of bits to shift
* @return Error code
**/

error_t mpiShiftLeft(Mpi *r, uint_t n)
{
	error_t error;
	uint_t i;

	//Number of 32-bit words to shift
	uint_t n1 = n / (MPI_INT_SIZE * 8);
	//Number of bits to shift
	uint_t n2 = n % (MPI_INT_SIZE * 8);

	//Check parameters
	if (!r->size || !n)
		return NO_ERROR;

	//Increase the size of the multiple-precision number
	error = mpiGrow(r, r->size + (n + 31) / 32);
	//Check return code
	if (error)
		return error;

	//First, shift words
	if (n1 > 0)
	{
		//Process the most significant words
		for (i = r->size - 1; i >= n1; i--)
			r->data[i] = r->data[i - n1];

		//Fill the rest with zeroes
		for (i = 0; i < n1; i++)
			r->data[i] = 0;
	}

	//Then shift bits
	if (n2 > 0)
	{
		//Process the most significant words
		for (i = r->size - 1; i >= 1; i--)
			r->data[i] = (r->data[i] << n2) | (r->data[i - 1] >> (32 - n2));

		//The least significant word requires a special handling
		r->data[0] <<= n2;
	}

	//Shift operation is complete
	return NO_ERROR;
}


/**
* @brief Right shift operation
* @param[in,out] r The multiple precision integer to be shifted to the right
* @param[in] n The number of bits to shift
* @return Error code
**/

error_t mpiShiftRight(Mpi *r, uint_t n)
{
	uint_t i;
	uint_t m;

	//Number of 32-bit words to shift
	uint_t n1 = n / (MPI_INT_SIZE * 8);
	//Number of bits to shift
	uint_t n2 = n % (MPI_INT_SIZE * 8);

	//Check parameters
	if (n1 >= r->size)
	{
		// cryptoMemset(r->data, 0, r->size * MPI_INT_SIZE); - Ajay
        #pragma unroll
		for (int s = 0; s< r->size; s++) {
			r->data[s] = 0;
		}
		return NO_ERROR;
	}

	//First, shift words
	if (n1 > 0)
	{
		//Process the least significant words
		for (m = r->size - n1, i = 0; i < m; i++)
			r->data[i] = r->data[i + n1];

		//Fill the rest with zeroes
		for (i = m; i < r->size; i++)
			r->data[i] = 0;
	}

	//Then shift bits
	if (n2 > 0)
	{
		//Process the least significant words
		for (m = r->size - n1 - 1, i = 0; i < m; i++)
			r->data[i] = (r->data[i] >> n2) | (r->data[i + 1] << (32 - n2));

		//The most significant word requires a special handling
		r->data[m] >>= n2;
	}

	//Shift operation is complete
	return NO_ERROR;
}


/**
* @brief Multiple precision multiplication
* @param[out] r Resulting integer R = A * B
* @param[in] a First operand A
* @param[in] b Second operand B
* @return Error code
**/

error_t mpiMul(Mpi *r, const Mpi *a, const Mpi *b)
{
	error_t error;
	int_t i;
	int_t m;
	int_t n;
	Mpi ta;
	Mpi tb;

	//Initialize multiple precision integers
	mpiInit(&ta);
	mpiInit(&tb);

	//R and A are the same instance?
	if (r == a)
	{
		//Copy A to TA
		MPI_CHECK(mpiCopy(&ta, a));
		//Use TA instead of A
		a = &ta;
	}

	//R and B are the same instance?
	if (r == b)
	{
		//Copy B to TB
		MPI_CHECK(mpiCopy(&tb, b));
		//Use TB instead of B
		b = &tb;
	}

	//Determine the actual length of A and B
	m = mpiGetLength(a);
	n = mpiGetLength(b);

	//Adjust the size of R
	MPI_CHECK(mpiGrow(r, m + n));
	//Set the sign of R
	r->sign = (a->sign == b->sign) ? 1 : -1;

	//Clear the contents of the destination integer
	// cryptoMemset(r->data, 0, r->size * MPI_INT_SIZE); - Ajay
    #pragma unroll
	for (int s = 0; s< r->size; s++) {
		r->data[s] = 0;
	}

	//Perform multiplication
	if (m < n)
	{
		for (i = 0; i < m; i++)
			mpiMulAccCore(&r->data[i], b->data, n, a->data[i]);
	}
	else
	{
		for (i = 0; i < n; i++)
			mpiMulAccCore(&r->data[i], a->data, m, b->data[i]);
	}

end:
	//Release multiple precision integers
	mpiFree(&ta);
	mpiFree(&tb);

	//Return status code
	return error;
}


/**
* @brief Multiply a multiple precision integer by an integer
* @param[out] r Resulting integer R = A * B
* @param[in] a First operand A
* @param[in] b Second operand B
* @return Error code
**/

error_t mpiMulInt(Mpi *r, const Mpi *a, int_t b)
{
	uint_t value;
	Mpi t;

	//Convert the second operand to a multiple precision integer
	value = (b >= 0) ? b : -b;
	t.sign = (b >= 0) ? 1 : -1;
	t.size = 1;
	// t.data = &value; - Ajay
	t.data[0] = value;	

	//Perform multiplication
	return mpiMul(r, a, &t);
}


/**
* @brief Multiple precision division
* @param[out] q The quotient Q = A / B
* @param[out] r The remainder R = A mod B
* @param[in] a The dividend A
* @param[in] b The divisor B
* @return Error code
**/

error_t mpiDiv(Mpi *q, Mpi *r, const Mpi *a, const Mpi *b)
{
	error_t error;
	uint_t m;
	uint_t n;
	Mpi c;
	Mpi d;
	Mpi e;

	//Check whether the divisor is equal to zero
	if (!mpiCompInt(b, 0))
		return ERROR_INVALID_PARAMETER;

	//Initialize multiple precision integers
	mpiInit(&c);
	mpiInit(&d);
	mpiInit(&e);

	MPI_CHECK(mpiCopy(&c, a));
	MPI_CHECK(mpiCopy(&d, b));
	MPI_CHECK(mpiSetValue(&e, 0));

	m = mpiGetBitLength(&c);
	n = mpiGetBitLength(&d);

	if (m > n)
		MPI_CHECK(mpiShiftLeft(&d, m - n));

	while (n++ <= m)
	{
		MPI_CHECK(mpiShiftLeft(&e, 1));

		if (mpiComp(&c, &d) >= 0)
		{
			MPI_CHECK(mpiSetBitValue(&e, 0, 1));
			MPI_CHECK(mpiSub(&c, &c, &d));
		}

		MPI_CHECK(mpiShiftRight(&d, 1));
	}

	if (q != NULL)
		MPI_CHECK(mpiCopy(q, &e));

	if (r != NULL)
		MPI_CHECK(mpiCopy(r, &c));

end:
	//Release previously allocated memory
	mpiFree(&c);
	mpiFree(&d);
	mpiFree(&e);

	//Return status code
	return error;
}


/**
* @brief Divide a multiple precision integer by an integer
* @param[out] q The quotient Q = A / B
* @param[out] r The remainder R = A mod B
* @param[in] a The dividend A
* @param[in] b The divisor B
* @return Error code
**/

error_t mpiDivInt(Mpi *q, Mpi *r, const Mpi *a, int_t b)
{
	uint_t value;
	Mpi t;

	//Convert the divisor to a multiple precision integer
	value = (b >= 0) ? b : -b;
	t.sign = (b >= 0) ? 1 : -1;
	t.size = 1;
	// t.data = &value; - Ajay
	t.data[0] = value;

	//Perform division
	return mpiDiv(q, r, a, &t);
}


/**
* @brief Modulo operation
* @param[out] r Resulting integer R = A mod P
* @param[in] a The multiple precision integer to be reduced
* @param[in] p The modulus P
* @return Error code
**/

error_t mpiMod(Mpi *r, const Mpi *a, const Mpi *p)
{
	error_t error;
	int_t sign;
	uint_t m;
	uint_t n;
	Mpi c;

	//Make sure the modulus is positive
	if (mpiCompInt(p, 0) <= 0)
		return ERROR_INVALID_PARAMETER;

	//Initialize multiple precision integer
	mpiInit(&c);

	//Save the sign of A
	sign = a->sign;
	//Determine the actual length of A
	m = mpiGetBitLength(a);
	//Determine the actual length of P
	n = mpiGetBitLength(p);

	//Let R = A
	MPI_CHECK(mpiCopy(r, a));

	if (m >= n)
	{
		MPI_CHECK(mpiCopy(&c, p));
		MPI_CHECK(mpiShiftLeft(&c, m - n));

		while (mpiCompAbs(r, p) >= 0)
		{
			if (mpiCompAbs(r, &c) >= 0)
			{
				MPI_CHECK(mpiSubAbs(r, r, &c));
			}

			MPI_CHECK(mpiShiftRight(&c, 1));
		}
	}

	if (sign < 0)
	{
		MPI_CHECK(mpiSubAbs(r, p, r));
	}

end:
	//Release previously allocated memory
	mpiFree(&c);

	//Return status code
	return error;
}



/**
* @brief Modular addition
* @param[out] r Resulting integer R = A + B mod P
* @param[in] a The first operand A
* @param[in] b The second operand B
* @param[in] p The modulus P
* @return Error code
**/

error_t mpiAddMod(Mpi *r, const Mpi *a, const Mpi *b, const Mpi *p)
{
	error_t error;

	//Perform modular addition
	MPI_CHECK(mpiAdd(r, a, b));
	MPI_CHECK(mpiMod(r, r, p));

end:
	//Return status code
	return error;
}


/**
* @brief Modular subtraction
* @param[out] r Resulting integer R = A - B mod P
* @param[in] a The first operand A
* @param[in] b The second operand B
* @param[in] p The modulus P
* @return Error code
**/

error_t mpiSubMod(Mpi *r, const Mpi *a, const Mpi *b, const Mpi *p)
{
	error_t error;

	//Perform modular subtraction
	MPI_CHECK(mpiSub(r, a, b));
	MPI_CHECK(mpiMod(r, r, p));

end:
	//Return status code
	return error;
}


/**
* @brief Modular multiplication
* @param[out] r Resulting integer R = A * B mod P
* @param[in] a The first operand A
* @param[in] b The second operand B
* @param[in] p The modulus P
* @return Error code
**/

error_t mpiMulMod(Mpi *r, const Mpi *a, const Mpi *b, const Mpi *p)
{
	error_t error;

	//Perform modular multiplication
	MPI_CHECK(mpiMul(r, a, b));
	MPI_CHECK(mpiMod(r, r, p));

end:
	//Return status code
	return error;
}


/**
* @brief Modular inverse
* @param[out] r Resulting integer R = A^-1 mod P
* @param[in] a The multiple precision integer A
* @param[in] p The modulus P
* @return Error code
**/

error_t mpiInvMod(Mpi *r, const Mpi *a, const Mpi *p)
{
	error_t error;
	Mpi b;
	Mpi c;
	Mpi q0;
	Mpi r0;
	Mpi t;
	Mpi u;
	Mpi v;

	//Initialize multiple precision integers
	mpiInit(&b);
	mpiInit(&c);
	mpiInit(&q0);
	mpiInit(&r0);
	mpiInit(&t);
	mpiInit(&u);
	mpiInit(&v);

	MPI_CHECK(mpiCopy(&b, p));
	MPI_CHECK(mpiCopy(&c, a));
	MPI_CHECK(mpiSetValue(&u, 0));
	MPI_CHECK(mpiSetValue(&v, 1));

	while (mpiCompInt(&c, 0) > 0)
	{
		MPI_CHECK(mpiDiv(&q0, &r0, &b, &c));

		MPI_CHECK(mpiCopy(&b, &c));
		MPI_CHECK(mpiCopy(&c, &r0));

		MPI_CHECK(mpiCopy(&t, &v));
		MPI_CHECK(mpiMul(&q0, &q0, &v));
		MPI_CHECK(mpiSub(&v, &u, &q0));
		MPI_CHECK(mpiCopy(&u, &t));
	}

	if (mpiCompInt(&b, 1))
	{
		MPI_CHECK(ERROR_FAILURE);
	}

	if (mpiCompInt(&u, 0) > 0)
	{
		MPI_CHECK(mpiCopy(r, &u));
	}
	else
	{
		MPI_CHECK(mpiAdd(r, &u, p));
	}

end:
	//Release previously allocated memory
	mpiFree(&b);
	mpiFree(&c);
	mpiFree(&q0);
	mpiFree(&r0);
	mpiFree(&t);
	mpiFree(&u);
	mpiFree(&v);

	//Return status code
	return error;
}


/**
* @brief Modular exponentiation
* @param[out] r Resulting integer R = A ^ E mod P
* @param[in] a Pointer to a multiple precision integer
* @param[in] e Exponent
* @param[in] p Modulus
* @return Error code
**/

error_t mpiExpMod(Mpi *r, const Mpi *a, const Mpi *e, const Mpi *p)
{
	error_t error;
	int_t i;
	int_t j;
	int_t n;
	uint_t d;
	uint_t k;
	uint_t u;
	Mpi b;
	Mpi c2;
	Mpi t;
	Mpi s[8];

	//Initialize multiple precision integers
	mpiInit(&b);
	mpiInit(&c2);
	mpiInit(&t);

	//Initialize precomputed values
	for (i = 0; i < arraysize(s); i++)
		mpiInit(&s[i]);

	//Very small exponents are often selected with low Hamming weight.
	//The sliding window mechanism should be disabled in that case
	d = (mpiGetBitLength(e) <= 32) ? 1 : 4;

	//Even modulus?
	if (mpiIsEven(p))
	{
		//Let B = A^2
		MPI_CHECK(mpiMulMod(&b, a, a, p));
		//Let S[0] = A
		MPI_CHECK(mpiCopy(&s[0], a));

		//Precompute S[i] = A^(2 * i + 1)
		for (i = 1; i < (1 << (d - 1)); i++)
		{
			MPI_CHECK(mpiMulMod(&s[i], &s[i - 1], &b, p));
		}

		//Let R = 1
		MPI_CHECK(mpiSetValue(r, 1));

		//The exponent is processed in a right-to-left fashion
		i = mpiGetBitLength(e) - 1;

		//Perform sliding window exponentiation
		while (i >= 0)
		{
			//The sliding window exponentiation algorithm decomposes E
			//into zero and nonzero windows
			if (!mpiGetBitValue(e, i))
			{
				//Compute R = R^2
				MPI_CHECK(mpiMulMod(r, r, r, p));
				//Next bit to be processed
				i--;
			}
			else
			{
				//Find the longest window
				n = MAX(i - d + 1, 0);

				//The least significant bit of the window must be equal to 1
				while (!mpiGetBitValue(e, n)) n++;

				//The algorithm processes more than one bit per iteration
				for (u = 0, j = i; j >= n; j--)
				{
					//Compute R = R^2
					MPI_CHECK(mpiMulMod(r, r, r, p));
					//Compute the relevant index to be used in the precomputed table
					u = (u << 1) | mpiGetBitValue(e, j);
				}

				//Perform a single multiplication per iteration
				MPI_CHECK(mpiMulMod(r, r, &s[u >> 1], p));
				//Next bit to be processed
				i = n - 1;
			}
		}
	}
	else
	{
		//Compute the smaller C = (2^32)^k such as C > P
		k = mpiGetLength(p);

		//Compute C^2 mod P
		MPI_CHECK(mpiSetValue(&c2, 1));
		MPI_CHECK(mpiShiftLeft(&c2, 2 * k * (MPI_INT_SIZE * 8)));
		MPI_CHECK(mpiMod(&c2, &c2, p));

		//Let B = A * C mod P
		if (mpiComp(a, p) >= 0)
		{
			MPI_CHECK(mpiMod(&b, a, p));
			MPI_CHECK(mpiMontgomeryMul(&b, &b, &c2, k, p, &t));
		}
		else
		{
			MPI_CHECK(mpiMontgomeryMul(&b, a, &c2, k, p, &t));
		}

		//Let R = B^2 * C^-1 mod P
		MPI_CHECK(mpiMontgomeryMul(r, &b, &b, k, p, &t));
		//Let S[0] = B
		MPI_CHECK(mpiCopy(&s[0], &b));

		//Precompute S[i] = B^(2 * i + 1) * C^-1 mod P
		for (i = 1; i < (1 << (d - 1)); i++)
		{
			MPI_CHECK(mpiMontgomeryMul(&s[i], &s[i - 1], r, k, p, &t));
		}

		//Let R = C mod P
		MPI_CHECK(mpiCopy(r, &c2));
		MPI_CHECK(mpiMontgomeryRed(r, r, k, p, &t));

		//The exponent is processed in a right-to-left fashion
		i = mpiGetBitLength(e) - 1;

		//Perform sliding window exponentiation
		while (i >= 0)
		{
			//The sliding window exponentiation algorithm decomposes E
			//into zero and nonzero windows
			if (!mpiGetBitValue(e, i))
			{
				//Compute R = R^2 * C^-1 mod P
				MPI_CHECK(mpiMontgomeryMul(r, r, r, k, p, &t));
				//Next bit to be processed
				i--;
			}
			else
			{
				//Find the longest window
				n = MAX(i - d + 1, 0);

				//The least significant bit of the window must be equal to 1
				while (!mpiGetBitValue(e, n)) n++;

				//The algorithm processes more than one bit per iteration
				for (u = 0, j = i; j >= n; j--)
				{
					//Compute R = R^2 * C^-1 mod P
					MPI_CHECK(mpiMontgomeryMul(r, r, r, k, p, &t));
					//Compute the relevant index to be used in the precomputed table
					u = (u << 1) | mpiGetBitValue(e, j);
				}

				//Compute R = R * T[u/2] * C^-1 mod P
				MPI_CHECK(mpiMontgomeryMul(r, r, &s[u >> 1], k, p, &t));
				//Next bit to be processed
				i = n - 1;
			}
		}

		//Compute R = R * C^-1 mod P
		MPI_CHECK(mpiMontgomeryRed(r, r, k, p, &t));
	}

end:
	//Release multiple precision integers
	mpiFree(&b);
	mpiFree(&c2);
	mpiFree(&t);

	//Release precomputed values
	for (i = 0; i < arraysize(s); i++)
		mpiFree(&s[i]);

	//Return status code
	return error;
}


/**
* @brief Montgomery multiplication
* @param[out] r Resulting integer R = A * B / 2^k mod P
* @param[in] a An integer A such as 0 <= A < 2^k
* @param[in] b An integer B such as 0 <= B < 2^k
* @param[in] k An integer k such as P < 2^k
* @param[in] p Modulus P
* @param[in] t An preallocated integer T (for internal operation)
* @return Error code
**/

error_t mpiMontgomeryMul(Mpi *r, const Mpi *a, const Mpi *b, uint_t k, const Mpi *p, Mpi *t)
{
	error_t error;
	uint_t i;
	uint_t m;
	uint_t n;
	uint_t q;

	//Use Newton's method to compute the inverse of P[0] mod 2^32
	for (m = 2 - p->data[0], i = 0; i < 4; i++)
		m = m * (2 - m * p->data[0]);

	//Precompute -1/P[0] mod 2^32;
	m = ~m + 1;

	//We assume that B is always less than 2^k
	n = MIN(b->size, k);

	//Make sure T is large enough
	MPI_CHECK(mpiGrow(t, 2 * k + 1));
	//Let T = 0
	MPI_CHECK(mpiSetValue(t, 0));

	//Perform Montgomery multiplication
	for (i = 0; i < k; i++)
	{
		//Check current index
		if (i < a->size)
		{
			//Compute q = ((T[i] + A[i] * B[0]) * m) mod 2^32
			q = (t->data[i] + a->data[i] * b->data[0]) * m;
			//Compute T = T + A[i] * B
			mpiMulAccCore(t->data + i, b->data, n, a->data[i]);
		}
		else
		{
			//Compute q = (T[i] * m) mod 2^32
			q = t->data[i] * m;
		}

		//Compute T = T + q * P
		mpiMulAccCore(t->data + i, p->data, k, q);
	}

	//Compute R = T / 2^(32 * k)
	MPI_CHECK(mpiShiftRight(t, k * (MPI_INT_SIZE * 8)));
	MPI_CHECK(mpiCopy(r, t));

	//A final subtraction is required
	if (mpiComp(r, p) >= 0)
	{
		MPI_CHECK(mpiSub(r, r, p));
	}

end:
	//Return status code
	return error;
}


/**
* @brief Montgomery reduction
* @param[out] r Resulting integer R = A / 2^k mod P
* @param[in] a An integer A such as 0 <= A < 2^k
* @param[in] k An integer k such as P < 2^k
* @param[in] p Modulus P
* @param[in] t An preallocated integer T (for internal operation)
* @return Error code
**/

error_t mpiMontgomeryRed(Mpi *r, const Mpi *a, uint_t k, const Mpi *p, Mpi *t)
{
	uint_t value;
	Mpi b;

	//Let B = 1
	value = 1;
	b.sign = 1;
	b.size = 1;
	// b.data = &value; - Ajay
	b.data[0] = value;

	//Compute R = A / 2^k mod P
	return mpiMontgomeryMul(r, a, &b, k, p, t);
}


#if (MPI_ASM_SUPPORT == DISABLED)

/**
* @brief Multiply-accumulate operation
* @param[out] r Resulting integer
* @param[in] a First operand A
* @param[in] m Size of A in words
* @param[in] b Second operand B
**/

void mpiMulAccCore(uint_t *r, const uint_t *a, int_t m, const uint_t b)
{
	int_t i;
	uint32_t c;
	uint32_t u;
	uint32_t v;
	uint64_t p;

	//Clear variables
	c = 0;
	u = 0;
	v = 0;

	//Perform multiplication
	for (i = 0; i < m; i++)
	{
		p = (uint64_t)a[i] * b;
		u = (uint32_t)p;
		v = (uint32_t)(p >> 32);

		u += c;
		if (u < c) v++;

		u += r[i];
		if (u < r[i]) v++;

		r[i] = u;
		c = v;
	}

	//Propagate carry
	for (; c != 0; i++)
	{
		r[i] += c;
		c = (r[i] < c);
	}
}

#endif


/**
* @brief Display the contents of a multiple precision integer
* @param[in] stream Pointer to a FILE object that identifies an output stream
* @param[in] prepend String to prepend to the left of each line
* @param[in] a Pointer to a multiple precision integer
**/

/*
void mpiDump(FILE *stream, const char_t *prepend, const Mpi *a)
{
	uint_t i;

	//Process each word
	for (i = 0; i < a->size; i++)
	{
		//Beginning of a new line?
		if (i == 0 || ((a->size - i - 1) % 8) == 7)
			fprintf(stream, "%s", prepend);

		//Display current data
		fprintf(stream, "%08X ", a->data[a->size - 1 - i]);

		//End of current line?
		if (((a->size - i - 1) % 8) == 0 || i == (a->size - 1))
			fprintf(stream, "\r\n");
	}
}
*/

// #endif


/* Ajay ec.c*/
/**
* @file ec.c
* @brief ECC (Elliptic Curve Cryptography)
*
* @section License
*
* Copyright (C) 2010-2017 Oryx Embedded SARL. All rights reserved.
*
* This file is part of CycloneCrypto Open.
*
* This program is free software; you can redistribute it and/or
* modify it under the terms of the GNU General Public License
* as published by the Free Software Foundation; either version 2
* of the License, or (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program; if not, write to the Free Software Foundation,
* Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*
* @author Oryx Embedded SARL (www.oryx-embedded.com)
* @version 1.8.0
**/

//Switch to the appropriate trace level
#define TRACE_LEVEL CRYPTO_TRACE_LEVEL

//Dependencies

// #include "ec.h"


//Check crypto library configuration
// #if (EC_SUPPORT == ENABLED)

//EC Public Key OID (1.2.840.10045.2.1)
// const uint8_t EC_PUBLIC_KEY_OID[7] = { 0x2A, 0x86, 0x48, 0xCE, 0x3D, 0x02, 0x01 };


/**
* @brief Initialize EC domain parameters
* @param[in] params Pointer to the EC domain parameters to be initialized
**/

void ecInitDomainParameters(EcDomainParameters *params)
{
	//Initialize structure
	params->type = EC_CURVE_TYPE_NONE;
	params->mod = NULL;

	//Initialize EC domain parameters
	mpiInit(&params->p);
	mpiInit(&params->a);
	mpiInit(&params->b);
	ecInit(&params->g);
	mpiInit(&params->q);
}


/**
* @brief Release EC domain parameters
* @param[in] params Pointer to the EC domain parameters to free
**/

void ecFreeDomainParameters(EcDomainParameters *params)
{
	//Release previously allocated resources
	mpiFree(&params->p);
	mpiFree(&params->a);
	mpiFree(&params->b);
	ecFree(&params->g);
	mpiFree(&params->q);
}




/**
* @brief Initialize elliptic curve point
* @param[in,out] r Pointer to the EC point to be initialized
**/

void ecInit(EcPoint *r)
{
	//Initialize structure
	mpiInit(&r->x);
	mpiInit(&r->y);
	mpiInit(&r->z);
}


/**
* @brief Release an elliptic curve point
* @param[in,out] r Pointer to the EC point to initialize to free
**/

void ecFree(EcPoint *r)
{
	//Release previously allocated resources
	mpiFree(&r->x);
	mpiFree(&r->y);
	mpiFree(&r->z);
}


/**
* @brief Copy EC point
* @param[out] r Destination EC point
* @param[in] s Source EC point
* @return Error code
**/

error_t ecCopy(EcPoint *r, const EcPoint *s)
{
	error_t error;

	//R and S are the same instance?
	if (r == s)
		return NO_ERROR;

	//Copy coordinates
	MPI_CHECK(mpiCopy(&r->x, &s->x));
	MPI_CHECK(mpiCopy(&r->y, &s->y));
	MPI_CHECK(mpiCopy(&r->z, &s->z));

end:
	//Return status code
	return error;
}


/**
* @brief Convert an octet string to an EC point
* @param[in] params EC domain parameters
* @param[out] r EC point resulting from the conversion
* @param[in] data Pointer to the octet string
* @param[in] length Length of the octet string
* @return Error code
**/

error_t ecImport(const EcDomainParameters *params,
	EcPoint *r, const uint8_t *data, size_t length)
{
	error_t error;
	// size_t k;

	//Get the length in octets of the prime
	// k = mpiGetByteLength(&params->p);

	//Check the length of the octet string
	// if(length != (k * 2 + 1))
	// return ERROR_DECODING_FAILED;

	//Compressed point representation is not supported
	// if(data[0] != 0x04)
	// return ERROR_ILLEGAL_PARAMETER;

	//Convert the x-coordinate to a multiple precision integer
	// error = mpiReadRaw(&r->x, data + 1, k);
	error = mpiReadRaw(&r->x, data, 48);
	//Any error to report?
	if (error)
		return error;

	//Convert the y-coordinate to a multiple precision integer
	error = mpiReadRaw(&r->y, data + 48, 48);
	//Any error to report?
	if (error)
		return error;

	//Successful processing
	return NO_ERROR;
	// return 0;
}


/**
* @brief Convert an EC point to an octet string
* @param[in] params EC domain parameters
* @param[in] a EC point to be converted
* @param[out] data Pointer to the octet string
* @param[out] length Length of the resulting octet string
* @return Error code
**/

error_t ecExport(const EcDomainParameters *params,
	const EcPoint *a, uint8_t *data, size_t *length)
{
	error_t error;
	size_t k;

	//Get the length in octets of the prime
	k = mpiGetByteLength(&params->p);

	//Point compression is not used
	data[0] = 0x04;

	//Convert the x-coordinate to an octet string
	error = mpiWriteRaw(&a->x, data + 1, k);
	//Conversion failed?
	if (error)
		return error;

	//Convert the y-coordinate to an octet string
	error = mpiWriteRaw(&a->y, data + k + 1, k);
	//Conversion failed?
	if (error)
		return error;

	//Return the total number of bytes that have been written
	*length = k * 2 + 1;
	//Successful processing
	return NO_ERROR;
}


/**
* @brief Compute projective representation
* @param[in] params EC domain parameters
* @param[out] r Projective representation of the point
* @param[in] s Affine representation of the point
* @return Error code
**/

error_t ecProjectify(const EcDomainParameters *params, EcPoint *r, const EcPoint *s)
{
	error_t error = NO_ERROR;

	//Copy point
	// EC_CHECK(ecCopy(r, s));
	//Map the point to projective space
	// MPI_CHECK(mpiSetValue(&r->z, 1));

	mpiCopy(&r->x, &s->x);
	mpiCopy(&r->y, &s->y);
	mpiCopy(&r->z, &s->z);
	mpiSetValue(&r->z, 1);

end:
	//Return status code
	return error;
}


/**
* @brief Recover affine representation
* @param[in] params EC domain parameters
* @param[out] r Affine representation of the point
* @param[in] s Projective representation of the point
* @return Error code
**/

error_t ecAffinify(const EcDomainParameters *params, EcPoint *r, const EcPoint *s)
{
	error_t error = NO_ERROR;
	Mpi a;
	Mpi b;

	//Point at the infinity?
	if (!mpiCompInt(&s->z, 0))
		return ERROR_INVALID_PARAMETER;

	//Initialize multiple precision integers
	mpiInit(&a);
	mpiInit(&b);

	//Compute a = 1/Sz mod p
	MPI_CHECK(mpiInvMod(&a, &s->z, &params->p));

	//Set Rx = a^2 * Sx mod p
	EC_CHECK(ecSqrMod(params, &b, &a));
	EC_CHECK(ecMulMod(params, &r->x, &b, &s->x));

	//Set Ry = a^3 * Sy mod p
	EC_CHECK(ecMulMod(params, &b, &b, &a));
	EC_CHECK(ecMulMod(params, &r->y, &b, &s->y));

	//Set Rz = 1
	MPI_CHECK(mpiSetValue(&r->z, 1));

end:
	//Release multiple precision integers
	mpiFree(&a);
	mpiFree(&b);

	//Return status code
	return error;
}


/**
* @brief Check whether the affine point S is on the curve
* @param[in] params EC domain parameters
* @param[in] s Affine representation of the point
* @return TRUE if the affine point S is on the curve, else FALSE
**/

bool_t ecIsPointAffine(const EcDomainParameters *params, const EcPoint *s)
{
	error_t error;
	Mpi t1;
	Mpi t2;

	//Initialize multiple precision integers
	mpiInit(&t1);
	mpiInit(&t2);

	//Compute t1 = (Sx^3 + a * Sx + b) mod p
	EC_CHECK(ecSqrMod(params, &t1, &s->x));
	EC_CHECK(ecMulMod(params, &t1, &t1, &s->x));
	EC_CHECK(ecMulMod(params, &t2, &params->a, &s->x));
	EC_CHECK(ecAddMod(params, &t1, &t1, &t2));
	EC_CHECK(ecAddMod(params, &t1, &t1, &params->b));

	//Compute t2 = Sy^2
	EC_CHECK(ecSqrMod(params, &t2, &s->y));

	//Check whether the point is on the elliptic curve
	if (mpiComp(&t1, &t2))
		error = ERROR_FAILURE;

end:
	//Release multiple precision integers
	mpiFree(&t1);
	mpiFree(&t2);

	//Return TRUE if the affine point S is on the curve, else FALSE
	return error ? FALSE : TRUE;
}


/**
* @brief Point doubling
* @param[in] params EC domain parameters
* @param[out] r Resulting point R = 2S
* @param[in] s Point S
* @return Error code
**/

error_t ecDouble(const EcDomainParameters *params, EcPoint *r, const EcPoint *s)
{
	error_t error;
	Mpi t1;
	Mpi t2;
	Mpi t3;
	Mpi t4;
	Mpi t5;

	//Initialize multiple precision integers
	mpiInit(&t1);
	mpiInit(&t2);
	mpiInit(&t3);
	mpiInit(&t4);
	mpiInit(&t5);

	//Set t1 = Sx
	MPI_CHECK(mpiCopy(&t1, &s->x));
	//Set t2 = Sy
	MPI_CHECK(mpiCopy(&t2, &s->y));
	//Set t3 = Sz
	MPI_CHECK(mpiCopy(&t3, &s->z));

	//Point at the infinity?
	if (!mpiCompInt(&t3, 0))
	{
		//Set R = (1, 1, 0)
		MPI_CHECK(mpiSetValue(&r->x, 1));
		MPI_CHECK(mpiSetValue(&r->y, 1));
		MPI_CHECK(mpiSetValue(&r->z, 0));
	}
	else
	{
		//SECP K1 elliptic curve?
		if (params->type == EC_CURVE_TYPE_SECP_K1)
		{
			//Compute t5 = t1^2
			EC_CHECK(ecSqrMod(params, &t5, &t1));
			//Compute t4 = 3 * t5
			EC_CHECK(ecAddMod(params, &t4, &t5, &t5));
			EC_CHECK(ecAddMod(params, &t4, &t4, &t5));
		}
		//SECP R1 elliptic curve?
		else if (params->type == EC_CURVE_TYPE_SECP_R1)
		{
			//Compute t4 = t3^2
			EC_CHECK(ecSqrMod(params, &t4, &t3));
			//Compute t5 = t1 - t4
			EC_CHECK(ecSubMod(params, &t5, &t1, &t4));
			//Compute t4 = t1 + t4
			EC_CHECK(ecAddMod(params, &t4, &t1, &t4));
			//Compute t5 = t4 * t5
			EC_CHECK(ecMulMod(params, &t5, &t4, &t5));
			//Compute t4 = 3 * t5
			EC_CHECK(ecAddMod(params, &t4, &t5, &t5));
			EC_CHECK(ecAddMod(params, &t4, &t4, &t5));
		}
		else
		{
			//Compute t4 = t3^4
			EC_CHECK(ecSqrMod(params, &t4, &t3));
			EC_CHECK(ecSqrMod(params, &t4, &t4));
			//Compute t4 = a * t4
			EC_CHECK(ecMulMod(params, &t4, &t4, &params->a));
			//Compute t5 = t1^2
			EC_CHECK(ecSqrMod(params, &t5, &t1));
			//Compute t4 = t4 + 3 * t5
			EC_CHECK(ecAddMod(params, &t4, &t4, &t5));
			EC_CHECK(ecAddMod(params, &t4, &t4, &t5));
			EC_CHECK(ecAddMod(params, &t4, &t4, &t5));
		}

		//Compute t3 = t3 * t2
		EC_CHECK(ecMulMod(params, &t3, &t3, &t2));
		//Compute t3 = 2 * t3
		EC_CHECK(ecAddMod(params, &t3, &t3, &t3));
		//Compute t2 = t2^2
		EC_CHECK(ecSqrMod(params, &t2, &t2));
		//Compute t5 = t1 * t2
		EC_CHECK(ecMulMod(params, &t5, &t1, &t2));
		//Compute t5 = 4 * t5
		EC_CHECK(ecAddMod(params, &t5, &t5, &t5));
		EC_CHECK(ecAddMod(params, &t5, &t5, &t5));
		//Compute t1 = t4^2
		EC_CHECK(ecSqrMod(params, &t1, &t4));
		//Compute t1 = t1 - 2 * t5
		EC_CHECK(ecSubMod(params, &t1, &t1, &t5));
		EC_CHECK(ecSubMod(params, &t1, &t1, &t5));
		//Compute t2 = t2^2
		EC_CHECK(ecSqrMod(params, &t2, &t2));
		//Compute t2 = 8 * t2
		EC_CHECK(ecAddMod(params, &t2, &t2, &t2));
		EC_CHECK(ecAddMod(params, &t2, &t2, &t2));
		EC_CHECK(ecAddMod(params, &t2, &t2, &t2));
		//Compute t5 = t5 - t1
		EC_CHECK(ecSubMod(params, &t5, &t5, &t1));
		//Compute t5 = t4 * t5
		EC_CHECK(ecMulMod(params, &t5, &t4, &t5));
		//Compute t2 = t5 - t2
		EC_CHECK(ecSubMod(params, &t2, &t5, &t2));

		//Set Rx = t1
		MPI_CHECK(mpiCopy(&r->x, &t1));
		//Set Ry = t2
		MPI_CHECK(mpiCopy(&r->y, &t2));
		//Set Rz = t3
		MPI_CHECK(mpiCopy(&r->z, &t3));
	}

end:
	//Release multiple precision integers
	mpiFree(&t1);
	mpiFree(&t2);
	mpiFree(&t3);
	mpiFree(&t4);
	mpiFree(&t5);

	//Return status code
	return error;
}


/**
* @brief Point addition (helper routine)
* @param[in] params EC domain parameters
* @param[out] r Resulting point R = S + T
* @param[in] s First operand
* @param[in] t Second operand
* @return Error code
**/

error_t ecAdd(const EcDomainParameters *params, EcPoint *r, const EcPoint *s, const EcPoint *t)
{
	error_t error;
	Mpi t1;
	Mpi t2;
	Mpi t3;
	Mpi t4;
	Mpi t5;
	Mpi t6;
	Mpi t7;

	//Initialize multiple precision integers
	mpiInit(&t1);
	mpiInit(&t2);
	mpiInit(&t3);
	mpiInit(&t4);
	mpiInit(&t5);
	mpiInit(&t6);
	mpiInit(&t7);

	//Set t1 = Sx
	MPI_CHECK(mpiCopy(&t1, &s->x));
	//Set t2 = Sy
	MPI_CHECK(mpiCopy(&t2, &s->y));
	//Set t3 = Sz
	MPI_CHECK(mpiCopy(&t3, &s->z));
	//Set t4 = Tx
	MPI_CHECK(mpiCopy(&t4, &t->x));
	//Set t5 = Ty
	MPI_CHECK(mpiCopy(&t5, &t->y));

	//Check whether Tz != 1
	if (mpiCompInt(&t->z, 1))
	{
		//Compute t6 = Tz
		MPI_CHECK(mpiCopy(&t6, &t->z));
		//Compute t7 = t6^2
		EC_CHECK(ecSqrMod(params, &t7, &t6));
		//Compute t1 = t1 * t7
		EC_CHECK(ecMulMod(params, &t1, &t1, &t7));
		//Compute t7 = t6 * t7
		EC_CHECK(ecMulMod(params, &t7, &t6, &t7));
		//Compute t2 = t2 * t7
		EC_CHECK(ecMulMod(params, &t2, &t2, &t7));
	}

	//Compute t7 = t3^2
	EC_CHECK(ecSqrMod(params, &t7, &t3));
	//Compute t4 = t4 * t7
	EC_CHECK(ecMulMod(params, &t4, &t4, &t7));
	//Compute t7 = t3 * t7
	EC_CHECK(ecMulMod(params, &t7, &t3, &t7));
	//Compute t5 = t5 * t7
	EC_CHECK(ecMulMod(params, &t5, &t5, &t7));
	//Compute t4 = t1 - t4
	EC_CHECK(ecSubMod(params, &t4, &t1, &t4));
	//Compute t5 = t2 - t5
	EC_CHECK(ecSubMod(params, &t5, &t2, &t5));

	//Check whether t4 == 0
	if (!mpiCompInt(&t4, 0))
	{
		//Check whether t5 == 0
		if (!mpiCompInt(&t5, 0))
		{
			//Set R = (0, 0, 0)
			MPI_CHECK(mpiSetValue(&r->x, 0));
			MPI_CHECK(mpiSetValue(&r->y, 0));
			MPI_CHECK(mpiSetValue(&r->z, 0));
		}
		else
		{
			//Set R = (1, 1, 0)
			MPI_CHECK(mpiSetValue(&r->x, 1));
			MPI_CHECK(mpiSetValue(&r->y, 1));
			MPI_CHECK(mpiSetValue(&r->z, 0));
		}
	}
	else
	{
		//Compute t1 = 2 * t1 - t4
		EC_CHECK(ecAddMod(params, &t1, &t1, &t1));
		EC_CHECK(ecSubMod(params, &t1, &t1, &t4));
		//Compute t2 = 2 * t2 - t5
		EC_CHECK(ecAddMod(params, &t2, &t2, &t2));
		EC_CHECK(ecSubMod(params, &t2, &t2, &t5));

		//Check whether Tz != 1
		if (mpiCompInt(&t->z, 1))
		{
			//Compute t3 = t3 * t6
			EC_CHECK(ecMulMod(params, &t3, &t3, &t6));
		}

		//Compute t3 = t3 * t4
		EC_CHECK(ecMulMod(params, &t3, &t3, &t4));
		//Compute t7 = t4^2
		EC_CHECK(ecSqrMod(params, &t7, &t4));
		//Compute t4 = t4 * t7
		EC_CHECK(ecMulMod(params, &t4, &t4, &t7));
		//Compute t7 = t1 * t7
		EC_CHECK(ecMulMod(params, &t7, &t1, &t7));
		//Compute t1 = t5^2
		EC_CHECK(ecSqrMod(params, &t1, &t5));
		//Compute t1 = t1 - t7
		EC_CHECK(ecSubMod(params, &t1, &t1, &t7));
		//Compute t7 = t7 - 2 * t1
		EC_CHECK(ecAddMod(params, &t6, &t1, &t1));
		EC_CHECK(ecSubMod(params, &t7, &t7, &t6));
		//Compute t5 = t5 * t7
		EC_CHECK(ecMulMod(params, &t5, &t5, &t7));
		//Compute t4 = t2 * t4
		EC_CHECK(ecMulMod(params, &t4, &t2, &t4));
		//Compute t2 = t5 - t4
		EC_CHECK(ecSubMod(params, &t2, &t5, &t4));

		//Compute t2 = t2 / 2
		if (mpiIsEven(&t2))
		{
			MPI_CHECK(mpiShiftRight(&t2, 1));
		}
		else
		{
			MPI_CHECK(mpiAdd(&t2, &t2, &params->p));
			MPI_CHECK(mpiShiftRight(&t2, 1));
		}

		//Set Rx = t1
		MPI_CHECK(mpiCopy(&r->x, &t1));
		//Set Ry = t2
		MPI_CHECK(mpiCopy(&r->y, &t2));
		//Set Rz = t3
		MPI_CHECK(mpiCopy(&r->z, &t3));
	}

end:
	//Release multiple precision integers
	mpiFree(&t1);
	mpiFree(&t2);
	mpiFree(&t3);
	mpiFree(&t4);
	mpiFree(&t5);
	mpiFree(&t6);
	mpiFree(&t7);

	//Return status code
	return error;
}


/**
* @brief Point addition
* @param[in] params EC domain parameters
* @param[out] r Resulting point R = S + T
* @param[in] s First operand
* @param[in] t Second operand
* @return Error code
**/

error_t ecFullAdd(const EcDomainParameters *params, EcPoint *r, const EcPoint *s, const EcPoint *t)
{
	error_t error;

	//Check whether Sz == 0
	if (!mpiCompInt(&s->z, 0))
	{
		//Set R = T
		MPI_CHECK(mpiCopy(&r->x, &t->x));
		MPI_CHECK(mpiCopy(&r->y, &t->y));
		MPI_CHECK(mpiCopy(&r->z, &t->z));
	}
	//Check whether Tz == 0
	else if (!mpiCompInt(&t->z, 0))
	{
		//Set R = S
		MPI_CHECK(mpiCopy(&r->x, &s->x));
		MPI_CHECK(mpiCopy(&r->y, &s->y));
		MPI_CHECK(mpiCopy(&r->z, &s->z));
	}
	else
	{
		//Compute R = S + T
		EC_CHECK(ecAdd(params, r, s, t));

		//Check whether R == (0, 0, 0)
		if (!mpiCompInt(&r->x, 0) && !mpiCompInt(&r->y, 0) && !mpiCompInt(&r->z, 0))
		{
			//Compute R = 2 * S
			EC_CHECK(ecDouble(params, r, s));
		}
	}

end:
	//Return status code
	return error;
}


/**
* @brief Point subtraction
* @param[in] params EC domain parameters
* @param[out] r Resulting point R = S - T
* @param[in] s First operand
* @param[in] t Second operand
* @return Error code
**/

error_t ecFullSub(const EcDomainParameters *params, EcPoint *r, const EcPoint *s, const EcPoint *t)
{
	error_t error;
	EcPoint u;

	//Initialize EC point
	ecInit(&u);

	//Set Ux = Tx and Uz = Tz
	MPI_CHECK(mpiCopy(&u.x, &t->x));
	MPI_CHECK(mpiCopy(&u.z, &t->z));
	//Set Uy = p - Ty
	MPI_CHECK(mpiSub(&u.y, &params->p, &t->y));

	//Compute R = S + U
	EC_CHECK(ecFullAdd(params, r, s, &u));

end:
	//Release EC point
	ecFree(&u);

	//Return status code
	return error;
}


/**
* @brief Scalar multiplication
* @param[in] params EC domain parameters
* @param[out] r Resulting point R = d.S
* @param[in] d An integer d such as 0 <= d < p
* @param[in] s EC point
* @return Error code
**/

error_t ecMult(const EcDomainParameters *params, EcPoint *r, const Mpi *d, const EcPoint *s)
{
	error_t error;
	uint_t i;
	Mpi h;

	//Initialize multiple precision integer
	mpiInit(&h);

	//Check whether d == 0
	if (!mpiCompInt(d, 0))
	{
		//Set R = (1, 1, 0)
		MPI_CHECK(mpiSetValue(&r->x, 1));
		MPI_CHECK(mpiSetValue(&r->y, 1));
		MPI_CHECK(mpiSetValue(&r->z, 0));
	}
	//Check whether d == 1
	else if (!mpiCompInt(d, 1))
	{
		//Set R = S
		MPI_CHECK(mpiCopy(&r->x, &s->x));
		MPI_CHECK(mpiCopy(&r->y, &s->y));
		MPI_CHECK(mpiCopy(&r->z, &s->z));
	}
	//Check whether Sz == 0
	else if (!mpiCompInt(&s->z, 0))
	{
		//Set R = (1, 1, 0)
		MPI_CHECK(mpiSetValue(&r->x, 1));
		MPI_CHECK(mpiSetValue(&r->y, 1));
		MPI_CHECK(mpiSetValue(&r->z, 0));
	}
	else
	{
		//Check whether Sz != 1
		if (mpiCompInt(&s->z, 1))
		{
			//Normalize S
			EC_CHECK(ecAffinify(params, r, s));
			EC_CHECK(ecProjectify(params, r, r));
		}
		else
		{
			//Set R = S
			MPI_CHECK(mpiCopy(&r->x, &s->x));
			MPI_CHECK(mpiCopy(&r->y, &s->y));
			MPI_CHECK(mpiCopy(&r->z, &s->z));
		}

		//Left-to-right binary method
#if 0
		for (i = mpiGetBitLength(d) - 1; i >= 1; i--)
		{
			//Point doubling
			EC_CHECK(ecDouble(params, r, r));

			if (mpiGetBitValue(d, i - 1))
			{
				//Compute R = R + S
				EC_CHECK(ecFullAdd(params, r, r, s));
			}
		}
		//Fast left-to-right binary method
#else
		//Precompute h = 3 * d
		MPI_CHECK(mpiAdd(&h, d, d));
		MPI_CHECK(mpiAdd(&h, &h, d));

		//Scalar multiplication
		for (i = mpiGetBitLength(&h) - 2; i >= 1; i--)
		{
			//Point doubling
			EC_CHECK(ecDouble(params, r, r));

			//Check whether h(i) == 1 and k(i) == 0
			if (mpiGetBitValue(&h, i) && !mpiGetBitValue(d, i))
			{
				//Compute R = R + S
				EC_CHECK(ecFullAdd(params, r, r, s));
			}
			//Check whether h(i) == 0 and k(i) == 1
			else if (!mpiGetBitValue(&h, i) && mpiGetBitValue(d, i))
			{
				//Compute R = R - S
				EC_CHECK(ecFullSub(params, r, r, s));
			}
		}
#endif
	}

end:
	//Release multiple precision integer
	mpiFree(&h);

	//Return status code
	return error;
}


/**
* @brief An auxiliary function for the twin multiplication
* @param[in] t An integer T such as 0 <= T <= 31
* @return Output value
**/

uint_t ecTwinMultF(uint_t t)
{
	if (18 <= t && t < 22)
		return 9;
	else if (14 <= t && t < 18)
		return 10;
	else if (22 <= t && t < 24)
		return 11;
	else if (4 <= t && t < 12)
		return 14;
	else
		return 12;
}


/**
* @brief Twin multiplication
* @param[in] params EC domain parameters
* @param[out] r Resulting point R = d0.S + d1.T
* @param[in] d0 An integer d such as 0 <= d0 < p
* @param[in] s EC point
* @param[in] d1 An integer d such as 0 <= d1 < p
* @param[in] t EC point
* @return Error code
**/



error_t ecTwinMult(const EcDomainParameters *params, EcPoint *r,
	const Mpi *d0, const EcPoint *s, const Mpi *d1, const EcPoint *t)
{
	error_t error;
	int_t k;
	uint_t m;
	uint_t m0;
	uint_t m1;
	uint_t c0;
	uint_t c1;
	uint_t h0;
	uint_t h1;
	int_t u0;
	int_t u1;
	EcPoint spt;
	EcPoint smt;

	//Initialize EC points
	ecInit(&spt);
	ecInit(&smt);

	//Precompute SpT = S + T
	EC_CHECK(ecFullAdd(params, &spt, s, t));
	//Precompute SmT = S - T
	EC_CHECK(ecFullSub(params, &smt, s, t));

	//Let m0 be the bit length of d0
	m0 = mpiGetBitLength(d0);
	//Let m1 be the bit length of d1
	m1 = mpiGetBitLength(d1);
	//Let m = MAX(m0, m1)
	m = MAX(m0, m1);

	// printf(" m in twin mult = %d\n", m);

	//Let c be a 2 x 6 binary matrix
	c0 = mpiGetBitValue(d0, m - 4);
	c0 |= mpiGetBitValue(d0, m - 3) << 1;
	c0 |= mpiGetBitValue(d0, m - 2) << 2;
	c0 |= mpiGetBitValue(d0, m - 1) << 3;
	c1 = mpiGetBitValue(d1, m - 4);
	c1 |= mpiGetBitValue(d1, m - 3) << 1;
	c1 |= mpiGetBitValue(d1, m - 2) << 2;
	c1 |= mpiGetBitValue(d1, m - 1) << 3;

	//Set R = (1, 1, 0)
	MPI_CHECK(mpiSetValue(&r->x, 1));
	MPI_CHECK(mpiSetValue(&r->y, 1));
	MPI_CHECK(mpiSetValue(&r->z, 0));

	//Calculate both multiplications at the same time
	for (k = m; k >= 0; k--)
	{
		//Compute h(0) = 16 * c(0,1) + 8 * c(0,2) + 4 * c(0,3) + 2 * c(0,4) + c(0,5)
		h0 = c0 & 0x1F;
		//Check whether c(0,0) == 1
		if (c0 & 0x20)
			h0 = 31 - h0;

		//Compute h(1) = 16 * c(1,1) + 8 * c(1,2) + 4 * c(1,3) + 2 * c(1,4) + c(1,5)
		h1 = c1 & 0x1F;
		//Check whether c(1,0) == 1
		if (c1 & 0x20)
			h1 = 31 - h1;

		//Compute u(0)
		if (h0 < ecTwinMultF(h1))
			u0 = 0;
		else if (c0 & 0x20)
			u0 = -1;
		else
			u0 = 1;

		//Compute u(1)
		if (h1 < ecTwinMultF(h0))
			u1 = 0;
		else if (c1 & 0x20)
			u1 = -1;
		else
			u1 = 1;

		//Update c matrix
		c0 <<= 1;
		c0 |= mpiGetBitValue(d0, k - 5);
		c0 ^= u0 ? 0x20 : 0x00;
		c1 <<= 1;
		c1 |= mpiGetBitValue(d1, k - 5);
		c1 ^= u1 ? 0x20 : 0x00;

		//Point doubling
		EC_CHECK(ecDouble(params, r, r));

		//Check u(0) and u(1)
		if (u0 == -1 && u1 == -1)
		{
			//Compute R = R - SpT
			EC_CHECK(ecFullSub(params, r, r, &spt));
		}
		else if (u0 == -1 && u1 == 0)
		{
			//Compute R = R - S
			EC_CHECK(ecFullSub(params, r, r, s));
		}
		else if (u0 == -1 && u1 == 1)
		{
			//Compute R = R - SmT
			EC_CHECK(ecFullSub(params, r, r, &smt));
		}
		else if (u0 == 0 && u1 == -1)
		{
			//Compute R = R - T
			EC_CHECK(ecFullSub(params, r, r, t));
		}
		else if (u0 == 0 && u1 == 1)
		{
			//Compute R = R + T
			EC_CHECK(ecFullAdd(params, r, r, t));
		}
		else if (u0 == 1 && u1 == -1)
		{
			//Compute R = R + SmT
			EC_CHECK(ecFullAdd(params, r, r, &smt));
		}
		else if (u0 == 1 && u1 == 0)
		{
			//Compute R = R + S
			EC_CHECK(ecFullAdd(params, r, r, s));
		}
		else if (u0 == 1 && u1 == 1)
		{
			//Compute R = R + SpT
			EC_CHECK(ecFullAdd(params, r, r, &spt));
		}
	}

end:
	//Release EC points
	ecFree(&spt);
	ecFree(&smt);

	//Return status code
	return error;
}


/**
* @brief Fast modular addition
* @param[in] params EC domain parameters
* @param[out] r Resulting integer R = (A + B) mod p
* @param[in] a An integer such as 0 <= A < p
* @param[in] b An integer such as 0 <= B < p
* @return Error code
**/

error_t ecAddMod(const EcDomainParameters *params, Mpi *r, const Mpi *a, const Mpi *b)
{
	error_t error;

	//Compute R = A + B
	MPI_CHECK(mpiAdd(r, a, b));

	//Compute R = (A + B) mod p
	if (mpiComp(r, &params->p) >= 0)
	{
		MPI_CHECK(mpiSub(r, r, &params->p));
	}

end:
	//Return status code
	return error;
}


/**
* @brief Fast modular subtraction
* @param[in] params EC domain parameters
* @param[out] r Resulting integer R = (A - B) mod p
* @param[in] a An integer such as 0 <= A < p
* @param[in] b An integer such as 0 <= B < p
* @return Error code
**/

error_t ecSubMod(const EcDomainParameters *params, Mpi *r, const Mpi *a, const Mpi *b)
{
	error_t error;

	//Compute R = A - B
	MPI_CHECK(mpiSub(r, a, b));

	//Compute R = (A - B) mod p
	if (mpiCompInt(r, 0) < 0)
	{
		MPI_CHECK(mpiAdd(r, r, &params->p));
	}

end:
	//Return status code
	return error;
}


/**
* @brief Fast modular multiplication
* @param[in] params EC domain parameters
* @param[out] r Resulting integer R = (A * B) mod p
* @param[in] a An integer such as 0 <= A < p
* @param[in] b An integer such as 0 <= B < p
* @return Error code
**/

error_t ecMulMod(const EcDomainParameters *params, Mpi *r, const Mpi *a, const Mpi *b)
{
	error_t error;

	//Compute R = A * B
	MPI_CHECK(mpiMul(r, a, b));

	//Compute R = (A * B) mod p
	MPI_CHECK(secp384r1Mod_1(r, &params->p));   //Ajay - to avoid function pointers
	/*
	if (params->mod != NULL)
	{
		MPI_CHECK(secp384r1Mod_1(r, &params->p));
	}
	else
	{
		MPI_CHECK(mpiMod(r, r, &params->p));
	}
	*/
end:
	//Return status code
	return error;
}


/**
* @brief Fast modular squaring
* @param[in] params EC domain parameters
* @param[out] r Resulting integer R = (A ^ 2) mod p
* @param[in] a An integer such as 0 <= A < p
* @return Error code
**/

error_t ecSqrMod(const EcDomainParameters *params, Mpi *r, const Mpi *a)
{
	error_t error;

	//Compute R = A ^ 2
	MPI_CHECK(mpiMul(r, a, a));

	//Compute R = (A ^ 2) mod p
	MPI_CHECK(secp384r1Mod_1(r, &params->p));  //Ajay - to avoid function pointers
	/*
	if (params->mod != NULL)
	{
		MPI_CHECK(secp384r1Mod_1(r, &params->p));
	}
	else
	{
		MPI_CHECK(mpiMod(r, r, &params->p));
	}
	*/

end:
	//Return status code
	return error;
}

// #endif




/* Ajay */
void mymemcpy_char(unsigned char *dest, const unsigned char *src, int len)
{
   #pragma unroll
   for (int i=0; i<len; i++) {
      dest[i] = src[i] ;
   }

}


/* Ajay */
/*
int hex2bin(unsigned char * const bin, const size_t bin_maxlen,
            const char * const hex, const size_t hex_len) {
    size_t        bin_pos = (size_t) 0U;
    size_t        hex_pos = (size_t) 0U;
    int           ret = 0;
    unsigned char c;
    unsigned char c_acc = 0U;
    unsigned char c_alpha0, c_alpha;
    unsigned char c_num0, c_num;
    unsigned char c_val;
    unsigned char state = 0U;

    while (hex_pos < hex_len) {
        c = (unsigned char) hex[hex_pos];
        c_num = c ^ 48U;
        c_num0 = (c_num - 10U) >> 8;
        c_alpha = (c & ~32U) - 55U;
        c_alpha0 = ((c_alpha - 10U) ^ (c_alpha - 16U)) >> 8;
        if ((c_num0 | c_alpha0) == 0U) {
            break;
        }
        c_val = (c_num0 & c_num) | (c_alpha0 & c_alpha);
        if (bin_pos >= bin_maxlen) {
            ret = -1;
            break;
        }
        if (state == 0U) {
            c_acc = c_val * 16U;
        } else {
            bin[bin_pos++] = c_acc | c_val;
        }
        state = ~state;
        hex_pos++;
    }
    if (state != 0U) {
        hex_pos--;
    }
    return ret;
}
*/




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
    uint64_t S[8];
    uint64_t t0, t1;
    int i;

    be64dec_vect(W, block, 128);
    for (i = 16; i < 80; i++) {
        W[i] = s1(W[i - 2]) + W[i - 7] + s0(W[i - 15]) + W[i - 16];
    }

    /* memcpy(S, state, 64); */ /* Ajay */

    #pragma unroll
    for (int j = 0; j<8; j++)
       S[j] = state[j] ;


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

    /*
    memset((void *) W, 0, sizeof W);
    memset((void *) S, 0, sizeof S);
    memset((void *) &t0, 0, sizeof t0);
    memset((void *) &t1, 0, sizeof t1);
    */    /*Ajay */

    #pragma unroll
    for (int k1 = 0; k1 < 80; k1++)
       W[k1] = 0;

    #pragma unroll
    for (int k2=0; k2 <8; k2++)
       S[k2] = 0;

    t0 = 0;
    t1 = 0;

}

/*
static unsigned char PAD[128] = {
    0x80, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
};
*/

void sha512_pad(hash_sha512_state *state) {
    unsigned char len[16];
    uint64_t r, plen;
    unsigned char PAD[128] = {                        /* Ajay */
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
        if (fe_isnonzero(check)) { printf("Error ge mult\n"); return -1;}
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
    ge_precomp Bi[8] = {
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
    h[9] = h9;
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
    h[0] = f0;
    h[1] = f1;
    h[2] = f2;
    h[3] = f3;
    h[4] = f4;
    h[5] = f5;
    h[6] = f6;
    h[7] = f7;
    h[8] = f8;
    h[9] = f9;
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
    int32_t f0_2 = 2 * f0;
    int32_t f1_2 = 2 * f1;
    int32_t f2_2 = 2 * f2;
    int32_t f3_2 = 2 * f3;
    int32_t f4_2 = 2 * f4;
    int32_t f5_2 = 2 * f5;
    int32_t f6_2 = 2 * f6;
    int32_t f7_2 = 2 * f7;
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
    int32_t f0_2 = 2 * f0;
    int32_t f1_2 = 2 * f1;
    int32_t f2_2 = 2 * f2;
    int32_t f3_2 = 2 * f3;
    int32_t f4_2 = 2 * f4;
    int32_t f5_2 = 2 * f5;
    int32_t f6_2 = 2 * f6;
    int32_t f7_2 = 2 * f7;
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

    h0 += h0;
    h1 += h1;
    h2 += h2;
    h3 += h3;
    h4 += h4;
    h5 += h5;
    h6 += h6;
    h7 += h7;
    h8 += h8;
    h9 += h9;

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
    int32_t f1_2 = 2 * f1;
    int32_t f3_2 = 2 * f3;
    int32_t f5_2 = 2 * f5;
    int32_t f7_2 = 2 * f7;
    int32_t f9_2 = 2 * f9;
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
    int32_t h0 = f0 - g0;
    int32_t h1 = f1 - g1;
    int32_t h2 = f2 - g2;
    int32_t h3 = f3 - g3;
    int32_t h4 = f4 - g4;
    int32_t h5 = f5 - g5;
    int32_t h6 = f6 - g6;
    int32_t h7 = f7 - g7;
    int32_t h8 = f8 - g8;
    int32_t h9 = f9 - g9;
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
    int32_t h0 = -f0;
    int32_t h1 = -f1;
    int32_t h2 = -f2;
    int32_t h3 = -f3;
    int32_t h4 = -f4;
    int32_t h5 = -f5;
    int32_t h6 = -f6;
    int32_t h7 = -f7;
    int32_t h8 = -f8;
    int32_t h9 = -f9;
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





/* Ajay EDCSA P384*/
//Macro definition
// #define CLEAR_WORD32(a, i, n) memset((a)->data + i, 0, n * MPI_INT_SIZE);

void CLEAR_WORD32(Mpi *a, int i, int n)
{
#pragma unroll
	for (int s = i; s < i + n; s++)
	{
		a->data[s] = 0;
	}
}


// #define COPY_WORD32(a, i, b, j, n) memcpy((a)->data + i, (b)->data + j, n*4 );

void COPY_WORD32(Mpi *a, int i, Mpi *b, int j, int n) {
#pragma unroll
	for (int s = j; s < j + n; s++) {
		a->data[i] = b->data[s];
		i++;
	}
	// printf("i = %d \n", i);

}







/* Ajay EDCSA P384*/
/**
* @brief Fast modular reduction (secp384r1 curve)
* @param[in,out] a This function accept an integer less than p^2 as
*   input and return (a mod p) as output
* @param[in] p Prime modulus
**/

error_t secp384r1Mod_1(Mpi *a, const Mpi *p)  // Ajay - Name change to avoid function pointer in kernel
{
	error_t error;
	Mpi s;
	Mpi t;

	//Initialize multiple precision integers
	mpiInit(&s);
	mpiInit(&t);

	//Ajust the size of the integers
	MPI_CHECK(mpiGrow(a, 96 / MPI_INT_SIZE));
	MPI_CHECK(mpiGrow(&s, 48 / MPI_INT_SIZE));
	MPI_CHECK(mpiGrow(&t, 48 / MPI_INT_SIZE));

	//Compute T = A11 | A10 | A9 | A8 | A7 | A6 | A5 | A4 | A3 | A2 | A1 | A0
	COPY_WORD32(&t, 0, a, 0, 12);

	//Compute S1 = 0 | 0 | 0 | 0 | 0 | A23 | A22 | A21 | 0 | 0 | 0 | 0
	CLEAR_WORD32(&s, 0, 4);
	COPY_WORD32(&s, 4, a, 21, 3);
	CLEAR_WORD32(&s, 7, 5);
	//Compute T = T + 2 * S1
	MPI_CHECK(mpiAdd(&t, &t, &s));
	MPI_CHECK(mpiAdd(&t, &t, &s));

	//Compute S2 = A23 | A22 | A21 | A20 | A19 | A18 | A17 | A16 | A15 | A14 | A13 | A12
	COPY_WORD32(&s, 0, a, 12, 12);
	//Compute T = T + S2
	MPI_CHECK(mpiAdd(&t, &t, &s));

	//Compute S3 = A20 | A19 | A18 | A17 | A16 | A15 | A14 | A13 | A12 | A23| A22 | A21
	COPY_WORD32(&s, 0, a, 21, 3);
	COPY_WORD32(&s, 3, a, 12, 9);
	//Compute T = T + S3
	MPI_CHECK(mpiAdd(&t, &t, &s));

	//Compute S4 = A19 | A18 | A17 | A16 | A15 | A14 | A13 | A12 | A20 | 0 | A23 | 0
	CLEAR_WORD32(&s, 0, 1);
	COPY_WORD32(&s, 1, a, 23, 1);
	CLEAR_WORD32(&s, 2, 1);
	COPY_WORD32(&s, 3, a, 20, 1);
	COPY_WORD32(&s, 4, a, 12, 8);
	//Compute T = T + S4
	MPI_CHECK(mpiAdd(&t, &t, &s));

	//Compute S5 = 0 | 0 | 0 | 0 | A23 | A22 | A21 | A20 | 0 | 0 | 0 | 0
	CLEAR_WORD32(&s, 0, 4);
	COPY_WORD32(&s, 4, a, 20, 4);
	CLEAR_WORD32(&s, 8, 4);
	//Compute T = T + S5
	MPI_CHECK(mpiAdd(&t, &t, &s));

	//Compute S6 = 0 | 0 | 0 | 0 | 0 | 0 | A23 | A22 | A21 | 0 | 0 | A20
	COPY_WORD32(&s, 0, a, 20, 1);
	CLEAR_WORD32(&s, 1, 2);
	COPY_WORD32(&s, 3, a, 21, 3);
	CLEAR_WORD32(&s, 6, 6);
	//Compute T = T + S6
	MPI_CHECK(mpiAdd(&t, &t, &s));

	//Compute D1 = A22 | A21 | A20 | A19 | A18 | A17 | A16 | A15 | A14 | A13 | A12 | A23
	COPY_WORD32(&s, 0, a, 23, 1);
	COPY_WORD32(&s, 1, a, 12, 11);
	//Compute T = T - D1
	MPI_CHECK(mpiSub(&t, &t, &s));

	//Compute D2 = 0 | 0 | 0 | 0 | 0 | 0 | 0 | A23 | A22 | A21 | A20 | 0
	CLEAR_WORD32(&s, 0, 1);
	COPY_WORD32(&s, 1, a, 20, 4);
	CLEAR_WORD32(&s, 5, 7);
	//Compute T = T - D2
	MPI_CHECK(mpiSub(&t, &t, &s));

	//Compute D3 = 0 | 0 | 0 | 0 | 0 | 0 | 0 | A23 | A23 | 0 | 0 | 0
	CLEAR_WORD32(&s, 0, 3);
	COPY_WORD32(&s, 3, a, 23, 1);
	COPY_WORD32(&s, 4, a, 23, 1);
	CLEAR_WORD32(&s, 5, 7);
	//Compute T = T - D3
	MPI_CHECK(mpiSub(&t, &t, &s));

	//Compute (T + 2 * S1 + S2 + S3 + S4 + S5 + S6 - D1 - D2 - D3) mod p
	while (mpiComp(&t, p) >= 0)
	{
		MPI_CHECK(mpiSub(&t, &t, p));
	}

	while (mpiCompInt(&t, 0) < 0)
	{
		MPI_CHECK(mpiAdd(&t, &t, p));
	}

	//Save result
	MPI_CHECK(mpiCopy(a, &t));

end:
	//Release multiple precision integers
	mpiFree(&s);
	mpiFree(&t);

	//Return status code
	return error;
}

/* Ajay EDCSA P384*/
error_t ecdsaVerifySignature(const uint8_t *pubKey,
	const uint8_t *digest, size_t digestLen, const uint8_t * signature)
	// const EcdsaSignature *signature)
{
	error_t error;
	uint_t n;
	Mpi w;
	Mpi z;
	Mpi u1;
	Mpi u2;
	Mpi v;
	EcPoint v0;
	EcPoint v1;
	Mpi sig_r;
	Mpi sig_s;
	EcDomainParameters params;
	EcPoint publicKey;
	uint8_t SECP384R1_OID[5] = { 0x2B, 0x81, 0x04, 0x00, 0x22 };
	char_t curve_name = 's';

	EcCurveInfo secp384r1Curve =
	{
		//Curve name
		// "secp384r1",
		curve_name,
		//Object identifier
		SECP384R1_OID,
		sizeof(SECP384R1_OID),
		//Curve type
		EC_CURVE_TYPE_SECP_R1,
		//Prime modulus p
		{ 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
		0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFE,
		0xFF, 0xFF, 0xFF, 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF, 0xFF, 0xFF, 0xFF },
		48,
		//Curve parameter a
		{ 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
		0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFE,
		0xFF, 0xFF, 0xFF, 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF, 0xFF, 0xFF, 0xFC },
		48,
		//Curve parameter b
		{ 0xB3, 0x31, 0x2F, 0xA7, 0xE2, 0x3E, 0xE7, 0xE4, 0x98, 0x8E, 0x05, 0x6B, 0xE3, 0xF8, 0x2D, 0x19,
		0x18, 0x1D, 0x9C, 0x6E, 0xFE, 0x81, 0x41, 0x12, 0x03, 0x14, 0x08, 0x8F, 0x50, 0x13, 0x87, 0x5A,
		0xC6, 0x56, 0x39, 0x8D, 0x8A, 0x2E, 0xD1, 0x9D, 0x2A, 0x85, 0xC8, 0xED, 0xD3, 0xEC, 0x2A, 0xEF },
		48,
		//x-coordinate of the base point G
		{ 0xAA, 0x87, 0xCA, 0x22, 0xBE, 0x8B, 0x05, 0x37, 0x8E, 0xB1, 0xC7, 0x1E, 0xF3, 0x20, 0xAD, 0x74,
		0x6E, 0x1D, 0x3B, 0x62, 0x8B, 0xA7, 0x9B, 0x98, 0x59, 0xF7, 0x41, 0xE0, 0x82, 0x54, 0x2A, 0x38,
		0x55, 0x02, 0xF2, 0x5D, 0xBF, 0x55, 0x29, 0x6C, 0x3A, 0x54, 0x5E, 0x38, 0x72, 0x76, 0x0A, 0xB7 },
		48,
		//y-coordinate of the base point G
		{ 0x36, 0x17, 0xDE, 0x4A, 0x96, 0x26, 0x2C, 0x6F, 0x5D, 0x9E, 0x98, 0xBF, 0x92, 0x92, 0xDC, 0x29,
		0xF8, 0xF4, 0x1D, 0xBD, 0x28, 0x9A, 0x14, 0x7C, 0xE9, 0xDA, 0x31, 0x13, 0xB5, 0xF0, 0xB8, 0xC0,
		0x0A, 0x60, 0xB1, 0xCE, 0x1D, 0x7E, 0x81, 0x9D, 0x7A, 0x43, 0x1D, 0x7C, 0x90, 0xEA, 0x0E, 0x5F },
		48,
		//Base point order q
		{ 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
		0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xC7, 0x63, 0x4D, 0x81, 0xF4, 0x37, 0x2D, 0xDF,
		0x58, 0x1A, 0x0D, 0xB2, 0x48, 0xB0, 0xA7, 0x7A, 0xEC, 0xEC, 0x19, 0x6A, 0xCC, 0xC5, 0x29, 0x73 },
		48,
		//Cofactor
		1,
		//Fast modular reduction
		// secp384r1Mod_1
	};


	//Check parameters
	if (pubKey == NULL || digest == NULL || signature == NULL)
		return ERROR_INVALID_PARAMETER;

	//Debug message
	/*
	TRACE_DEBUG("ECDSA signature verification...\r\n");
	TRACE_DEBUG("  public key X:\r\n");
	TRACE_DEBUG_MPI("    ", &publicKey->x);
	TRACE_DEBUG("  public key Y:\r\n");
	TRACE_DEBUG_MPI("    ", &publicKey->y);
	TRACE_DEBUG("  digest:\r\n");
	TRACE_DEBUG_ARRAY("    ", digest, digestLen);
	TRACE_DEBUG("  r:\r\n");
	TRACE_DEBUG_MPI("    ", &signature->r);
	TRACE_DEBUG("  s:\r\n");
	TRACE_DEBUG_MPI("    ", &signature->s);
	*/
	mpiInit(&sig_r);
	mpiInit(&sig_s);

	mpiReadRaw(&sig_r, signature, 48);
	mpiReadRaw(&sig_s, signature + 48, 48);// Read domain parameters into Mpi

	unsigned char read_sig_r[48];
	mpiWriteRaw(&sig_r, read_sig_r, 48);


	ecInitDomainParameters(&params);
	//Import prime modulus
	mpiReadRaw(&params.p, secp384r1Curve.p, secp384r1Curve.pLen);
	//Import parameter a
	mpiReadRaw(&params.a, secp384r1Curve.a, secp384r1Curve.aLen);
	//Import parameter b
	mpiReadRaw(&params.b, secp384r1Curve.b, secp384r1Curve.bLen);
	//Import the x-coordinate of the base point G
	mpiReadRaw(&params.g.x, secp384r1Curve.gx, secp384r1Curve.gxLen);
	//Import the y-coordinate of the base point G
	mpiReadRaw(&params.g.y, secp384r1Curve.gy, secp384r1Curve.gyLen);
	//Import base point order q
	mpiReadRaw(&params.q, secp384r1Curve.q, secp384r1Curve.qLen);
	//Normalize base point G
	mpiSetValue(&params.g.z, 1);
	//Fast modular reduction  - Ajay - function pointers not supported in opencl
	// params.mod = secp384r1Curve.mod;



	//The verifier shall check that 0 < r < q
	if (mpiCompInt(&sig_r, 0) <= 0 || mpiComp(&sig_r, &params.q) >= 0)
	{
		//If the condition is violated, the signature shall be rejected as invalid
		printf("Erro 1\n");
		return ERROR_INVALID_SIGNATURE;
		// return -1;
	}

	//The verifier shall check that 0 < s < q
	if (mpiCompInt(&sig_s, 0) <= 0 || mpiComp(&sig_s, &params.q) >= 0)
	{
		//If the condition is violated, the signature shall be rejected as invalid
		printf("Erro 2\n");
		return ERROR_INVALID_SIGNATURE;
		// return -2 ;
	}

	//Initialize multiple precision integers
	mpiInit(&w);
	mpiInit(&z);
	mpiInit(&u1);
	mpiInit(&u2);
	mpiInit(&v);
	//Initialize EC points
	ecInit(&v0);
	ecInit(&v1);



	// Build the public key into a EcPoint
	ecInit(&publicKey);
	ecImport(&params, &publicKey, pubKey, 192);


	//Let N be the bit length of q
	n = mpiGetBitLength(&params.q);
	//Compute N = MIN(N, outlen)
	n = MIN(n, digestLen * 8);

	//Convert the digest to a multiple precision integer
	mpiReadRaw(&z, digest, (n + 7) / 8);

	//Keep the leftmost N bits of the hash value
	if ((n % 8) != 0)
	{
		MPI_CHECK(mpiShiftRight(&z, 8 - (n % 8)));
	}

	//Compute w = s ^ -1 mod q

	// double time_inv_mod = 0;
	// double start_inv_mod = wallclock_1();

	mpiInvMod(&w, &sig_s, &params.q);

	// time_inv_mod += (wallclock_1() - start_inv_mod);
	// cout << "inv mod time--" << time_inv_mod << "seconds" << endl;


	//Compute u1 = z * w mod q
	// double time_mul_mod_1 = 0;
	// double start_mul_mod_1 = wallclock_1();

	mpiMulMod(&u1, &z, &w, &params.q);

	// time_mul_mod_1 += (wallclock_1() - start_mul_mod_1);
	// cout << "mul mod 1 time--" << time_mul_mod_1 << "seconds" << endl;

	//Compute u2 = r * w mod q
	// double time_mul_mod_2 = 0;
	// double start_mul_mod_2 = wallclock_1();

	mpiMulMod(&u2, &sig_r, &w, &params.q);

	// time_mul_mod_2 += (wallclock_1() - start_mul_mod_2);
	// cout << "mul mod 2 time--" << time_mul_mod_2 << "seconds" << endl;

	//Compute V0 = (x0, y0) = u1.G + u2.Q
	EC_CHECK(ecProjectify(&params, &v1, &publicKey));

	// double time_twin_mul = 0;
	// double start_twin_mul = wallclock_1();

	EC_CHECK(ecTwinMult(&params, &v0, &u1, &params.g, &u2, &v1));

	// time_twin_mul += (wallclock_1() - start_twin_mul);
	// cout << "twin mul time--" << time_twin_mul << "seconds" << endl;

	EC_CHECK(ecAffinify(&params, &v0, &v0));

	//Debug message
	// TRACE_DEBUG("  x0:\r\n");
	// TRACE_DEBUG_MPI("    ", &v0.x);
	// TRACE_DEBUG("  y0:\r\n");
	// TRACE_DEBUG_MPI("    ", &v0.y);

	//Compute v = x0 mod q
	MPI_CHECK(mpiMod(&v, &v0.x, &params.q));

	//Debug message
	// TRACE_DEBUG("  v:\r\n");
	// TRACE_DEBUG_MPI("    ", &v);

	//If v = r, then the signature is verified. If v does not equal r,
	//then the message or the signature may have been modified
	if (!mpiComp(&v, &sig_r))
		error = NO_ERROR;
	else
		error = ERROR_INVALID_SIGNATURE;

end:
	//Release multiple precision integers
	mpiFree(&w);
	mpiFree(&z);
	mpiFree(&u1);
	mpiFree(&u2);
	mpiFree(&v);
	//Release EC points
	ecFree(&v0);
	ecFree(&v1);

	//Return status code
	return error;
}

/* Ajay EDCSA P384*/
void hash_sha384_init(hash_sha512_state *context)
{
	context->count[0] = context->count[1] = 0;
	//Set initial hash value
	context->state[0] = 0xCBBB9D5DC1059ED8;
	context->state[1] = 0x629A292A367CD507;
	context->state[2] = 0x9159015A3070DD17;
	context->state[3] = 0x152FECD8F70E5939;
	context->state[4] = 0x67332667FFC00B31;
	context->state[5] = 0x8EB44A8768581511;
	context->state[6] = 0xDB0C2E0D64F98FA7;
	context->state[7] = 0x47B5481DBEFA4FA4;


}

#define MAX_MSG_LEN 5120
__kernel void verify(__global unsigned char *input, __global unsigned char *output) {
	hash_sha512_state hs;
	unsigned char h[64];
	unsigned int  i;
	unsigned char d = 0;
	uint buf_sz=256+MAX_MSG_LEN;
	
	const uint x = get_global_id(0);
	const uint y = get_global_id(1);
	const uint z = get_global_id(2);
	const uint width = get_global_size(0);
	const uint height = get_global_size(1);
	const uint id = z * width * height + y * width + x;
 
	
	uint orig=id*buf_sz; //position of my buffer
	
	union {
		unsigned char bytes[256];
		ulong16 v[2];
	} buffer;

	unsigned char len1[8];

	union {
		unsigned char m[MAX_MSG_LEN];
		ulong16 v[40];
	} msg;

	//read 128 bytes
	buffer.v[0]=vload16(orig>>7, (__global ulong *)input);
	unsigned char *sig = buffer.bytes;
	
	ulong mlen = *(ulong *)(sig + crypto_sign_BYTES + sizeof(int) + sizeof(uint));
	//printf("for id = %d mlen = %d\n", id, (int)mlen);
	
	//read 128 bytes
	buffer.v[1]=vload16(1+(orig>>7), (__global ulong *)input);
	unsigned char *pk = buffer.bytes+128;
	
	uint msg_words = (uint)(mlen >> 7);
	
	for(i=0;i<=msg_words;i++) //copy all 5120 bytes 
	{
		msg.v[i]= vload16(i+2+(orig >> 7), (__global ulong *)input);
	}
	

	hash_sha384_init(&hs);
	hash_sha512_update(&hs, msg.m, mlen);
	hash_sha512_final(&hs, h);
	
/*	
	printf("pk :");
	for (int k = 0; k<96; k++)
	{
		printf("%x ", pk[k]);
	}
	printf("\n");
	
	
	printf("dig :");
	for (int k = 0; k<48; k++)
	{
		printf("%x ", h[k]);
	}
	printf("\n");
	
	printf("sig :");
	for (int k = 0; k<96; k++)
	{
		printf("%x ", sig[k]);
	}
	printf("\n");
*/
	int res = (int)ecdsaVerifySignature(pk, h, 48, sig);

    if(res!=0)
	{
		printf("failed id=%d with error %d \n", id, res);
		*(output+id)=1;
	}
	else
	{
		*(output+id)=0;
	}
	
//	barrier(CLK_GLOBAL_MEM_FENCE);

	return;
}


