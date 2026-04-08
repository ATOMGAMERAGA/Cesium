use jni::JNIEnv;
use jni::objects::{JByteArray, JClass};
use jni::sys::jlong;
use xxhash_rust::xxh3::xxh3_64;

/// JNI entry point: compute xxHash3-64 of a byte array.
///
/// Signature: `(byte[] input) -> long`
///
/// Used internally by Cesium for fast packet fingerprinting (e.g., detecting
/// duplicate position packets without a full memcmp).
#[no_mangle]
pub extern "system" fn Java_net_cesium_native_CesiumNativeCrypto_xxhash64(
    mut env: JNIEnv,
    _class: JClass,
    input: JByteArray,
) -> jlong {
    let input_bytes = match env.convert_byte_array(&input) {
        Ok(b) => b,
        Err(_) => return 0,
    };
    xxh3_64(&input_bytes) as jlong
}
