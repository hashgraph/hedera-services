use std::mem;

use bls12_381::*;
use jni::JNIEnv;
use jni::objects::{JClass, JObject};
use jni::sys::{jboolean, jbyte, jbyteArray, jint};

use crate::common::GenericError;
use crate::g1::g1_from_jobject;
use crate::g2::g2_from_jobject;

/// Computes 2 pairings, A and B, and checks for equality of the pairing outputs
#[no_mangle]
pub extern "system" fn Java_com_hedera_platform_bls_impl_Bls12381Bindings_comparePairing(
    env: JNIEnv,
    _class: JClass,
    g1_a_object: JObject,
    g2_a_object: JObject,
    g1_b_object: JObject,
    g2_b_object: JObject,
) -> jboolean {
    let g1_a: G1Affine = match g1_from_jobject(&env, &g1_a_object) {
        Ok(val) => val,
        Err(_) => return jboolean::from(false),
    };

    let g2_a: G2Affine = match g2_from_jobject(&env, &g2_a_object) {
        Ok(val) => val,
        Err(_) => return jboolean::from(false),
    };

    let g1_b: G1Affine = match g1_from_jobject(&env, &g1_b_object) {
        Ok(val) => val,
        Err(_) => return jboolean::from(false),
    };

    let g2_b: G2Affine = match g2_from_jobject(&env, &g2_b_object) {
        Ok(val) => val,
        Err(_) => return jboolean::from(false),
    };

    jboolean::from(pairing(&g1_a, &g2_a) == pairing(&g1_b, &g2_b))
}

/// Accepts an element from group1, and an element from group2
/// Computes the pairing of the two group elements
/// Returns a string (as a byte array) representing the resulting group element
#[no_mangle]
pub extern "system" fn Java_com_hedera_platform_bls_impl_Bls12381Bindings_pairingDisplay(
    env: JNIEnv,
    _class: JClass,
    g1_object: JObject,
    g2_object: JObject,
    output: jbyteArray,
) -> jint {
    let g1: G1Affine = match g1_from_jobject(&env, &g1_object) {
        Ok(val) => val,
        Err(err) => return GenericError::from(err).get_error_code()
    };

    let g2: G2Affine = match g2_from_jobject(&env, &g2_object) {
        Ok(val) => val,
        Err(err) => return GenericError::from(err).get_error_code()
    };

    let pairing_string: String = format!("{}", pairing(&g1, &g2)).to_owned();
    let pairing_bytes: &[u8] = pairing_string.as_bytes();

    // this should never happen, but check just in case (since we are using format instead of a
    // purpose build serialization function)
    if pairing_bytes.len() != 1249 {
        return GenericError::OutputLength().get_error_code();
    }

    let pairing_jbytes: &[jbyte] = unsafe { mem::transmute(pairing_bytes) };

    return match env.set_byte_array_region(output, 0, pairing_jbytes) {
        Ok(_) => 0,
        Err(err) => GenericError::from(err).get_error_code()
    };
}
