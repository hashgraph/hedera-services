use std::convert::TryInto;
use std::mem;

use bls12_381::*;
use group::Group;
use jni::JNIEnv;
use jni::objects::{JClass, JObject};
use jni::sys::{jboolean, jbyte, jbyteArray, jint, jobject, jobjectArray, jsize};
use rand_chacha::ChaChaRng;
use rand_core::SeedableRng;

use crate::common::*;
use crate::scalar::scalar_from_jobject;

/// Converts a g1 jobject to a G1Affine object
pub(crate) fn g1_from_jobject(env: &JNIEnv, object: &JObject) -> Result<G1Affine, GenericError> {
    let compressed: bool = env.get_field(*object, "compressed", "Z")?.z()?;
    let g1_bytes: jobject = env
        .get_field(*object, "groupElement", "[B")?
        .l()?
        .into_raw();

    if compressed {
        Ok(from_bytes_generic(
            &env,
            &g1_bytes,
            &G1Affine::from_compressed,
        )?)
    } else {
        Ok(from_bytes_generic(
            &env,
            &g1_bytes,
            &G1Affine::from_uncompressed,
        )?)
    }
}

/// Creates a new identity element of group g1
#[no_mangle]
pub extern "system" fn Java_com_hedera_platform_bls_impl_Bls12381Bindings_newG1Identity(
    env: JNIEnv,
    _class: JClass,
    output: jbyteArray,
) -> jint {
    let new_identity_bytes: [u8; 96] = G1Affine::default().to_uncompressed();

    let new_identity_jbytes: &[jbyte; 96] = unsafe { mem::transmute(&new_identity_bytes) };

    return match env.set_byte_array_region(output, 0, new_identity_jbytes) {
        Ok(_) => 0,
        Err(err) => GenericError::from(err).get_error_code()
    };
}

/// Checks if 2 g1 elements are equal
#[no_mangle]
pub extern "system" fn Java_com_hedera_platform_bls_impl_Bls12381Bindings_g1ElementEquals(
    env: JNIEnv,
    _class: JClass,
    g1_1_object: JObject,
    g1_2_object: JObject,
) -> jboolean {
    let element1: G1Affine = match g1_from_jobject(&env, &g1_1_object) {
        Ok(val) => val,
        Err(_) => return jboolean::from(false),
    };

    let element2: G1Affine = match g1_from_jobject(&env, &g1_2_object) {
        Ok(val) => val,
        Err(_) => return jboolean::from(false),
    };

    jboolean::from(element1 == element2)
}

/// Checks if a g1 element is valid
#[no_mangle]
pub extern "system" fn Java_com_hedera_platform_bls_impl_Bls12381Bindings_checkG1Validity(
    env: JNIEnv,
    _class: JClass,
    g1_object: JObject,
) -> jboolean {
    return match g1_from_jobject(&env, &g1_object) {
        Ok(_) => jboolean::from(true),
        Err(_) => jboolean::from(false),
    };
}

/// Creates a new g1 element based on a byte array seed
#[no_mangle]
pub extern "system" fn Java_com_hedera_platform_bls_impl_Bls12381Bindings_newRandomG1(
    env: JNIEnv,
    _class: JClass,
    input_seed_bytes: jbyteArray,
    output: jbyteArray,
) -> jint {
    let seed_vector: Vec<u8> = match env.convert_byte_array(input_seed_bytes) {
        Ok(val) => val,
        Err(err) => return GenericError::from(err).get_error_code()
    };

    let seed_array: [u8; 32] = match seed_vector.try_into() {
        Ok(val) => val,
        Err(err) => return GenericError::from(err).get_error_code()
    };

    let random_g1_bytes: [u8; 96] = G1Affine::from(
        G1Projective::random(ChaChaRng::from_seed(seed_array))).to_uncompressed();

    let random_g1_jbytes: &[jbyte; 96] = unsafe { mem::transmute(&random_g1_bytes) };

    return match env.set_byte_array_region(output, 0, random_g1_jbytes) {
        Ok(_) => 0,
        Err(err) => GenericError::from(err).get_error_code()
    };
}

/// Computes the quotient of 2 group elements of g1
/// Result is an element of g1
#[no_mangle]
pub extern "system" fn Java_com_hedera_platform_bls_impl_Bls12381Bindings_g1Divide(
    env: JNIEnv,
    _class: JClass,
    g1_1_object: JObject,
    g1_2_object: JObject,
    output: jbyteArray,
) -> jint {
    let element1: G1Affine = match g1_from_jobject(&env, &g1_1_object) {
        Ok(val) => val,
        Err(err) => return GenericError::from(err).get_error_code()
    };

    let element2: G1Affine = match g1_from_jobject(&env, &g1_2_object) {
        Ok(val) => val,
        Err(err) => return GenericError::from(err).get_error_code()
    };

    // BLS12_381 library defines math operations differently, hence the use of `-` here instead of `/`
    // The name of this function was chosen to maintain consistency with terminology used in javaland
    let quotient_bytes: [u8; 96] = G1Affine::from(
        element1 - G1Projective::from(element2)).to_uncompressed();

    let quotient_jbytes: &[jbyte; 96] = unsafe { mem::transmute(&quotient_bytes) };

    return match env.set_byte_array_region(output, 0, quotient_jbytes) {
        Ok(_) => 0,
        Err(err) => GenericError::from(err).get_error_code()
    };
}

/// Computes the product of 2 group elements of g1
/// Result is an element of g1
#[no_mangle]
pub extern "system" fn Java_com_hedera_platform_bls_impl_Bls12381Bindings_g1Multiply(
    env: JNIEnv,
    _class: JClass,
    g1_1_object: JObject,
    g1_2_object: JObject,
    output: jbyteArray,
) -> jint {
    let element1: G1Affine = match g1_from_jobject(&env, &g1_1_object) {
        Ok(val) => val,
        Err(err) => return GenericError::from(err).get_error_code()
    };

    let element2: G1Affine = match g1_from_jobject(&env, &g1_2_object) {
        Ok(val) => val,
        Err(err) => return GenericError::from(err).get_error_code()
    };

    // BLS12_381 library defines math operations differently, hence the use of `+` here instead of `*`
    // The name of this function was chosen to maintain consistency with terminology used in javaland
    let product_bytes: [u8; 96] = G1Affine::from(
        element1 + G1Projective::from(element2)).to_uncompressed();

    let product_jbytes: &[jbyte; 96] = unsafe { mem::transmute(&product_bytes) };

    return match env.set_byte_array_region(output, 0, product_jbytes) {
        Ok(_) => 0,
        Err(err) => GenericError::from(err).get_error_code()
    };
}

/// Computes the product of a batch of group elements of g1
/// Result is an element of g1
#[no_mangle]
pub extern "system" fn Java_com_hedera_platform_bls_impl_Bls12381Bindings_g1BatchMultiply(
    env: JNIEnv,
    _class: JClass,
    element_batch: jobjectArray,
    output: jbyteArray,
) -> jint {
    let element_batch_len: jsize = match env.get_array_length(element_batch) {
        Ok(val) => val,
        Err(err) => return GenericError::from(err).get_error_code()
    };

    if element_batch_len < 1 {
        return 1;
    }

    let mut product: G1Projective = G1Projective::identity();

    for index in 0..element_batch_len {
        let g1_object: JObject = match env.get_object_array_element(element_batch, index) {
            Ok(val) => val,
            Err(err) => return GenericError::from(err).get_error_code()
        };

        let g1: G1Affine = match g1_from_jobject(&env, &g1_object) {
            Ok(val) => val,
            Err(err) => return GenericError::from(err).get_error_code()
        };

        product = product + g1;
    }

    let product_bytes: [u8; 96] = G1Affine::from(product).to_uncompressed();

    let product_jbytes: &[jbyte; 96] = unsafe { mem::transmute(&product_bytes) };

    return match env.set_byte_array_region(output, 0, product_jbytes) {
        Ok(_) => 0,
        Err(err) => GenericError::from(err).get_error_code()
    };
}

/// Computes the value of a g1 group element, taken to the power of a scalar
/// Result is an element of g1
#[no_mangle]
pub extern "system" fn Java_com_hedera_platform_bls_impl_Bls12381Bindings_g1PowZn(
    env: JNIEnv,
    _class: JClass,
    base_object: JObject,     // g1
    exponent_object: JObject, // scalar
    output: jbyteArray,
) -> jint {
    let base: G1Affine = match g1_from_jobject(&env, &base_object) {
        Ok(val) => val,
        Err(err) => return GenericError::from(err).get_error_code()
    };

    let exponent: Scalar = match scalar_from_jobject(&env, &exponent_object) {
        Ok(val) => val,
        Err(err) => return GenericError::from(err).get_error_code()
    };

    // BLS12_381 library defines math operations differently, hence the use of `*` here instead of `^`
    // The name of this function was chosen to maintain consistency with terminology used in javaland
    let power_bytes: [u8; 96] = G1Affine::from(base * exponent).to_uncompressed();

    let power_jbytes: &[jbyte; 96] = unsafe { mem::transmute(&power_bytes) };

    return match env.set_byte_array_region(output, 0, power_jbytes) {
        Ok(_) => 0,
        Err(err) => GenericError::from(err).get_error_code()
    };
}

/// Compresses a group element
#[no_mangle]
pub extern "system" fn Java_com_hedera_platform_bls_impl_Bls12381Bindings_g1Compress(
    env: JNIEnv,
    _class: JClass,
    element_object: JObject,
    output: jbyteArray,
) -> jint {
    let element: G1Affine = match g1_from_jobject(&env, &element_object) {
        Ok(val) => val,
        Err(err) => return GenericError::from(err).get_error_code()
    };

    let compressed_bytes: [u8; 48] = element.to_compressed();

    let compressed_jbytes: &[jbyte; 48] = unsafe { mem::transmute(&compressed_bytes) };

    return match env.set_byte_array_region(output, 0, compressed_jbytes) {
        Ok(_) => 0,
        Err(err) => GenericError::from(err).get_error_code()
    };
}
