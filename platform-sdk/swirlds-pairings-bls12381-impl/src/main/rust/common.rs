use std::convert::TryInto;
use std::fmt;

use jni::JNIEnv;
use jni::sys::jbyteArray;
use subtle::CtOption;

/// Generic error is used throughout these bindings to allow for a standard result type
#[derive(Debug)]
pub(crate) enum GenericError {
    // JNI error while converting inputs into rust types
    Jni(jni::errors::Error),
    // error while attempting to convert a slice into an array
    TryFromSlice(std::array::TryFromSliceError),
    // error while attempting to convert between types
    TryInto(usize),
    // function input had unexpected length
    InputLength(String),
    // underlying library returned an unexpected number of bytes
    OutputLength(),
}

impl fmt::Display for GenericError {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        match *self {
            GenericError::Jni(ref error) => error.fmt(f),
            GenericError::TryFromSlice(ref error) => error.fmt(f),
            GenericError::TryInto(ref size) => {
                write!(f, "try_input failed for vector of size {}", size)
            }
            GenericError::InputLength(ref string) => write!(f, "Input Length error: {}", string),
            GenericError::OutputLength() => {
                write!(f, "Underlying library returned unexpected number of bytes")
            }
        }
    }
}

impl std::error::Error for GenericError {}

impl From<jni::errors::Error> for GenericError {
    fn from(err: jni::errors::Error) -> GenericError {
        GenericError::Jni(err)
    }
}

impl From<std::array::TryFromSliceError> for GenericError {
    fn from(err: std::array::TryFromSliceError) -> GenericError {
        GenericError::TryFromSlice(err)
    }
}

impl From<Vec<u8>> for GenericError {
    fn from(vector: Vec<u8>) -> GenericError {
        GenericError::TryInto(vector.len())
    }
}

impl GenericError {
    /// These error codes are what get sent back in the first slot of the byte array to java
    pub fn get_error_code(&self) -> i32 {
        match *self {
            GenericError::Jni(_) => 1,
            GenericError::TryFromSlice(_) => 2,
            GenericError::TryInto(_) => 3,
            GenericError::InputLength(_) => 4,
            GenericError::OutputLength() => 5,
        }
    }
}

/// Generic function to convert bytes to a rust object
/// Accepts the input bytes, and a function to call that is capable of converting the bytes into the desired type
pub(crate) fn from_bytes_generic<ReturnType, const INPUT_SIZE: usize>(
    env: &JNIEnv,
    input_bytes: &jbyteArray,
    byte_conversion_fn: &dyn Fn(&[u8; INPUT_SIZE]) -> CtOption<ReturnType>,
) -> Result<ReturnType, GenericError> {
    let vector = env.convert_byte_array(*input_bytes)?;

    let array: [u8; INPUT_SIZE] = vector.try_into()?;

    Option::from(byte_conversion_fn(&array)).ok_or_else(|| {
        GenericError::InputLength(format!("Expected byte array of length {}", INPUT_SIZE))
    })
}
