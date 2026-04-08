use jni::JNIEnv;
use jni::objects::{JByteArray, JClass};
use jni::sys::{jbyteArray, jint};

/// JNI entry point: compress bytes with Zstandard.
///
/// Signature: `(byte[] input, int level) -> byte[]`
#[no_mangle]
pub extern "system" fn Java_net_cesium_native_CesiumNativeCompression_compressZstd(
    mut env: JNIEnv,
    _class: JClass,
    input: JByteArray,
    level: jint,
) -> jbyteArray {
    let input_bytes = match env.convert_byte_array(&input) {
        Ok(b) => b,
        Err(_) => return std::ptr::null_mut(),
    };

    let level = level.clamp(1, 22) as i32;
    match zstd::encode_all(input_bytes.as_slice(), level) {
        Ok(compressed) => {
            match env.byte_array_from_slice(&compressed) {
                Ok(arr) => arr.into_raw(),
                Err(_) => std::ptr::null_mut(),
            }
        }
        Err(_) => std::ptr::null_mut(),
    }
}

/// JNI entry point: decompress Zstandard bytes.
///
/// Signature: `(byte[] input) -> byte[]`
#[no_mangle]
pub extern "system" fn Java_net_cesium_native_CesiumNativeCompression_decompressZstd(
    mut env: JNIEnv,
    _class: JClass,
    input: JByteArray,
) -> jbyteArray {
    let input_bytes = match env.convert_byte_array(&input) {
        Ok(b) => b,
        Err(_) => return std::ptr::null_mut(),
    };

    match zstd::decode_all(input_bytes.as_slice()) {
        Ok(decompressed) => {
            match env.byte_array_from_slice(&decompressed) {
                Ok(arr) => arr.into_raw(),
                Err(_) => std::ptr::null_mut(),
            }
        }
        Err(_) => std::ptr::null_mut(),
    }
}

/// JNI entry point: compress bytes with LZ4.
///
/// Signature: `(byte[] input) -> byte[]`
#[no_mangle]
pub extern "system" fn Java_net_cesium_native_CesiumNativeCompression_compressLz4(
    mut env: JNIEnv,
    _class: JClass,
    input: JByteArray,
) -> jbyteArray {
    let input_bytes = match env.convert_byte_array(&input) {
        Ok(b) => b,
        Err(_) => return std::ptr::null_mut(),
    };

    let compressed = lz4_flex::compress_prepend_size(&input_bytes);
    match env.byte_array_from_slice(&compressed) {
        Ok(arr) => arr.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

/// JNI entry point: decompress LZ4 bytes.
///
/// Signature: `(byte[] input) -> byte[]`
#[no_mangle]
pub extern "system" fn Java_net_cesium_native_CesiumNativeCompression_decompressLz4(
    mut env: JNIEnv,
    _class: JClass,
    input: JByteArray,
) -> jbyteArray {
    let input_bytes = match env.convert_byte_array(&input) {
        Ok(b) => b,
        Err(_) => return std::ptr::null_mut(),
    };

    match lz4_flex::decompress_size_prepended(&input_bytes) {
        Ok(decompressed) => {
            match env.byte_array_from_slice(&decompressed) {
                Ok(arr) => arr.into_raw(),
                Err(_) => std::ptr::null_mut(),
            }
        }
        Err(_) => std::ptr::null_mut(),
    }
}
