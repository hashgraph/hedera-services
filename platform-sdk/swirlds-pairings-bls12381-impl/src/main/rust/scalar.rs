use std::convert::TryInto;
use std::mem;

use bls12_381::*;
use ff::Field;
use jni::JNIEnv;
use jni::objects::{JClass, JObject};
use jni::sys::{jboolean, jbyte, jbyteArray, jint, jlong};
use rand_chacha::ChaChaRng;
use rand_core::SeedableRng;

use crate::common::*;

const BIG_INT_SIZE: usize = 32;

/// Converts a scalar jobject to a Scalar object
pub(crate) fn scalar_from_jobject(env: &JNIEnv, object: &JObject) -> Result<Scalar, GenericError> {
    let scalar_bytes = env
        .get_field(*object, "fieldElement", "[B")?
        .l()?
        .into_raw();

    Ok(from_bytes_generic(
        &env,
        &scalar_bytes,
        &Scalar::from_bytes,
    )?)
}

/// Converts bytes to a big int, which is represented by an array of 4 64 bit integers
fn bytes_to_big_int(env: &JNIEnv, bytes: &jbyteArray) -> Result<[u64; 4], GenericError> {
    let mut vector: Vec<u8> = env.convert_byte_array(*bytes)?;

    if vector.len() > BIG_INT_SIZE {
        return Err(GenericError::InputLength(format!(
            "Input byte length {} is too long for a big int (max {} bytes)",
            vector.len(),
            BIG_INT_SIZE
        )));
    }

    // big integer from java comes in as big endian, with variable length
    vector.reverse(); // reverse bytes
    vector.resize(BIG_INT_SIZE, 0); // pad to length of 32

    let x1 = u64::from_le_bytes(<[u8; 8]>::try_from(&vector[0..8])?);
    let x2 = u64::from_le_bytes(<[u8; 8]>::try_from(&vector[8..16])?);
    let x3 = u64::from_le_bytes(<[u8; 8]>::try_from(&vector[16..24])?);
    let x4 = u64::from_le_bytes(<[u8; 8]>::try_from(&vector[24..32])?);

    Ok([x1, x2, x3, x4])
}

/// Creates a new scalar, based on input seed
#[no_mangle]
pub extern "system" fn Java_com_hedera_platform_bls_impl_Bls12381Bindings_newRandomScalar(
    env: JNIEnv,
    _class: JClass,
    input_seed_bytes: jbyteArray,
    output: jbyteArray,
) -> jint {
    let seed_vector = match env.convert_byte_array(input_seed_bytes) {
        Ok(val) => val,
        Err(err) => return GenericError::from(err).get_error_code()
    };

    let seed_array: [u8; 32] = match seed_vector.try_into() {
        Ok(val) => val,
        Err(err) => return GenericError::from(err).get_error_code()
    };

    let random_scalar_bytes: [u8; 32] = Scalar::random(
        ChaChaRng::from_seed(seed_array)).to_bytes();

    let random_scalar_jbytes: &[jbyte; 32] = unsafe { mem::transmute(&random_scalar_bytes) };

    return match env.set_byte_array_region(output, 0, random_scalar_jbytes) {
        Ok(_) => 0,
        Err(err) => GenericError::from(err).get_error_code()
    };
}

/// Creates a new scalar from a long
#[no_mangle]
pub extern "system" fn Java_com_hedera_platform_bls_impl_Bls12381Bindings_newScalarFromLong(
    env: JNIEnv,
    _class: JClass,
    input_long: jlong,
    output: jbyteArray,
) -> jint {
    let new_scalar_bytes: [u8; 32] = Scalar::from(input_long as u64).to_bytes();

    let new_scalar_jbytes: &[jbyte; 32] = unsafe { mem::transmute(&new_scalar_bytes) };

    return match env.set_byte_array_region(output, 0, new_scalar_jbytes) {
        Ok(_) => 0,
        Err(err) => GenericError::from(err).get_error_code()
    };
}

/// Creates a new 0 value scalar
#[no_mangle]
pub extern "system" fn Java_com_hedera_platform_bls_impl_Bls12381Bindings_newZeroScalar(
    env: JNIEnv,
    _class: JClass,
    output: jbyteArray,
) -> jint {
    let new_zero_bytes: [u8; 32] = Scalar::zero().to_bytes();

    let new_zero_jbytes: &[jbyte; 32] = unsafe { mem::transmute(&new_zero_bytes) };

    return match env.set_byte_array_region(output, 0, new_zero_jbytes) {
        Ok(_) => 0,
        Err(err) => GenericError::from(err).get_error_code()
    };
}

/// Creates a new 1 value scalar
#[no_mangle]
pub extern "system" fn Java_com_hedera_platform_bls_impl_Bls12381Bindings_newOneScalar(
    env: JNIEnv,
    _class: JClass,
    output: jbyteArray,
) -> jint {
    let new_one_bytes: [u8; 32] = Scalar::one().to_bytes();

    let new_one_jbytes: &[jbyte; 32] = unsafe { mem::transmute(&new_one_bytes) };

    return match env.set_byte_array_region(output, 0, new_one_jbytes) {
        Ok(_) => 0,
        Err(err) => GenericError::from(err).get_error_code()
    };
}

/// Checks if 2 scalar values are equal
#[no_mangle]
pub extern "system" fn Java_com_hedera_platform_bls_impl_Bls12381Bindings_scalarEquals(
    env: JNIEnv,
    _class: JClass,
    scalar1_object: JObject,
    scalar2_object: JObject,
) -> jboolean {
    let scalar1 = match scalar_from_jobject(&env, &scalar1_object) {
        Ok(val) => val,
        Err(_) => return jboolean::from(false),
    };

    let scalar2 = match scalar_from_jobject(&env, &scalar2_object) {
        Ok(val) => val,
        Err(_) => return jboolean::from(false),
    };

    jboolean::from(scalar1 == scalar2)
}

/// Checks if a scalar is valid
#[no_mangle]
pub extern "system" fn Java_com_hedera_platform_bls_impl_Bls12381Bindings_checkScalarValidity(
    env: JNIEnv,
    _class: JClass,
    scalar_object: JObject,
) -> jboolean {
    return match scalar_from_jobject(&env, &scalar_object) {
        Ok(_) => jboolean::from(true),
        Err(_) => jboolean::from(false),
    };
}

/// Computes the sum of 2 scalar values
/// Result is a scalar
#[no_mangle]
pub extern "system" fn Java_com_hedera_platform_bls_impl_Bls12381Bindings_scalarAdd(
    env: JNIEnv,
    _class: JClass,
    scalar1_object: JObject,
    scalar2_object: JObject,
    output: jbyteArray,
) -> jint {
    let scalar1 = match scalar_from_jobject(&env, &scalar1_object) {
        Ok(val) => val,
        Err(err) => return GenericError::from(err).get_error_code()
    };

    let scalar2 = match scalar_from_jobject(&env, &scalar2_object) {
        Ok(val) => val,
        Err(err) => return GenericError::from(err).get_error_code()
    };

    let sum_bytes: [u8; 32] = (scalar1 + scalar2).to_bytes();

    let sum_jbytes: &[jbyte; 32] = unsafe { mem::transmute(&sum_bytes) };

    return match env.set_byte_array_region(output, 0, sum_jbytes) {
        Ok(_) => 0,
        Err(err) => GenericError::from(err).get_error_code()
    };
}

/// Computes the difference between 2 scalar values
/// Result is a scalar
#[no_mangle]
pub extern "system" fn Java_com_hedera_platform_bls_impl_Bls12381Bindings_scalarSubtract(
    env: JNIEnv,
    _class: JClass,
    scalar1_object: JObject,
    scalar2_object: JObject,
    output: jbyteArray,
) -> jint {
    let scalar1 = match scalar_from_jobject(&env, &scalar1_object) {
        Ok(val) => val,
        Err(err) => return GenericError::from(err).get_error_code()
    };

    let scalar2 = match scalar_from_jobject(&env, &scalar2_object) {
        Ok(val) => val,
        Err(err) => return GenericError::from(err).get_error_code()
    };

    let difference_bytes: [u8; 32] = (scalar1 - scalar2).to_bytes();

    let difference_jbytes: &[jbyte; 32] = unsafe { mem::transmute(&difference_bytes) };

    return match env.set_byte_array_region(output, 0, difference_jbytes) {
        Ok(_) => 0,
        Err(err) => GenericError::from(err).get_error_code()
    };
}

/// Computes the product of 2 scalar values
/// Result is a scalar
#[no_mangle]
pub extern "system" fn Java_com_hedera_platform_bls_impl_Bls12381Bindings_scalarMultiply(
    env: JNIEnv,
    _class: JClass,
    scalar1_object: JObject,
    scalar2_object: JObject,
    output: jbyteArray,
) -> jint {
    let scalar1 = match scalar_from_jobject(&env, &scalar1_object) {
        Ok(val) => val,
        Err(err) => return GenericError::from(err).get_error_code()
    };

    let scalar2 = match scalar_from_jobject(&env, &scalar2_object) {
        Ok(val) => val,
        Err(err) => return GenericError::from(err).get_error_code()
    };

    let product_bytes: [u8; 32] = (scalar1 * scalar2).to_bytes();

    let product_jbytes: &[jbyte; 32] = unsafe { mem::transmute(&product_bytes) };

    return match env.set_byte_array_region(output, 0, product_jbytes) {
        Ok(_) => 0,
        Err(err) => GenericError::from(err).get_error_code()
    };
}

/// Computes the quotient of 2 scalar values
/// Result is a scalar
#[no_mangle]
pub extern "system" fn Java_com_hedera_platform_bls_impl_Bls12381Bindings_scalarDivide(
    env: JNIEnv,
    _class: JClass,
    scalar1_object: JObject,
    scalar2_object: JObject,
    output: jbyteArray,
) -> jint {
    let scalar1 = match scalar_from_jobject(&env, &scalar1_object) {
        Ok(val) => val,
        Err(err) => return GenericError::from(err).get_error_code()
    };

    let scalar2 = match scalar_from_jobject(&env, &scalar2_object) {
        Ok(val) => val,
        Err(err) => return GenericError::from(err).get_error_code()
    };

    let scalar2_inversion: Scalar = match Option::from(scalar2.invert()) {
        Some(val) => val,
        None => return 1,
    };

    let quotient_bytes: [u8; 32] = (scalar1 * scalar2_inversion).to_bytes();

    let quotient_jbytes: &[jbyte; 32] = unsafe { mem::transmute(&quotient_bytes) };

    return match env.set_byte_array_region(output, 0, quotient_jbytes) {
        Ok(_) => 0,
        Err(err) => GenericError::from(err).get_error_code()
    };
}

/// Computes the value of a scalar taken to the power of a big integer
/// Result is a scalar value
#[no_mangle]
pub extern "system" fn Java_com_hedera_platform_bls_impl_Bls12381Bindings_scalarPower(
    env: JNIEnv,
    _class: JClass,
    base_object: JObject,       // scalar
    exponent_bytes: jbyteArray, // big int
    output: jbyteArray,
) -> jint {
    let base = match scalar_from_jobject(&env, &base_object) {
        Ok(val) => val,
        Err(err) => return GenericError::from(err).get_error_code()
    };

    let exponent = match bytes_to_big_int(&env, &exponent_bytes) {
        Ok(val) => val,
        Err(err) => return GenericError::from(err).get_error_code()
    };

    let power_bytes: [u8; 32] = (base.pow(&exponent)).to_bytes();

    let power_jbytes: &[jbyte; 32] = unsafe { mem::transmute(&power_bytes) };

    return match env.set_byte_array_region(output, 0, power_jbytes) {
        Ok(_) => 0,
        Err(err) => GenericError::from(err).get_error_code()
    };
}
